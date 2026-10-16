package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.*;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.kuailemao.diary.*;
import xyz.kuailemao.diary.coaching.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class CoachingPeriodAiTest {
    static HikariDataSource pool;static NamedParameterJdbcTemplate jdbc;static DataSourceTransactionManager tx;
    CoachingStore store;CoachingPeriods periods;CoachingPeriodAi coaching;DiaryAiStore ai;DiaryAiWorker worker;DiaryAiProperties p;CoachingProperties options;
    DiaryStore diary;long owner;UUID goal,action;HttpServer server;AtomicInteger calls;CountDownLatch started,proceed;AtomicBoolean active;
    @BeforeAll static void connect() {
        pool=new HikariDataSource();pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");pool.setUsername("diary_runtime");pool.setPassword("isolated-diary-runtime");pool.setMaximumPoolSize(3);pool.setMinimumIdle(0);pool.setConnectionTimeout(1500);
        jdbc=new NamedParameterJdbcTemplate(pool);tx=new DataSourceTransactionManager(pool);
    }
    @AfterAll static void close() { pool.close(); }
    @BeforeEach void fixture() throws Exception {
        owner=(UUID.randomUUID().getLeastSignificantBits() & 0x0000ffffffffffffL)+1000;calls=new AtomicInteger();active=new AtomicBoolean(true);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/model",exchange->{
            calls.incrementAndGet();var request=JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            var context=JSON.parseObject(request.getJSONArray("messages").getJSONObject(1).getString("content"));
            if(started!=null) { started.countDown();try { proceed.await(8,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } }
            var source=context.getJSONArray("sources").stream().map(v->(JSONObject)v).filter(v->"G".equals(v.getString("kind"))).findFirst().orElseThrow();
            String sourceId=source.getString("sourceId");
            var claim=new CoachingEvidence.Claim("虚构目标中写了水彩", "GOAL",List.of(sourceId),List.of(new CoachingEvidence.Quote(sourceId,"水彩")));
            var suggestion=new CoachingEvidence.Suggestion("虚构：试一次水彩 <img src=https://coaching-probe.invalid/a>","只是一项可选择的计划",List.of(sourceId),"五分钟以内","下一次记录时",false);
            var result=new CoachingEvidence.Result("虚构受控HTTP，仅验证链路",List.of(claim),List.of(),List.of(),List.of(),List.of(suggestion),List.of("不代表真实模型质量；计划不是执行"));
            byte[] body=JSON.toJSONBytes(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",JSON.toJSONString(result))))));
            try { exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body); } finally { exchange.close(); }
        });server.start();
        p=spy(new DiaryAiProperties());p.setEnabled(true);p.setDimensions(3);p.setProvider("fictional");p.setGenerationModel("fictional-model");p.setEmbeddingModel("fictional-vector");p.setGenerationEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/model");doReturn(true).when(p).configured();
        options=new CoachingProperties();options.setEnabled(true);options.setAiEnabled(true);
        store=new CoachingStore(jdbc,tx);periods=new CoachingPeriods(store);diary=new DiaryStore(jdbc,tx);
        var ref=new AtomicReference<DiaryAiExtension>();ai=new DiaryAiStore(jdbc,tx,p,ref::get);ai.ensureProfile(owner);
        var cloud=new DiaryAiCloud(p,HttpClient.newHttpClient(),uri->{});coaching=new CoachingPeriodAi(store,periods,options,ai,cloud,p);ref.set(coaching);
        worker=new DiaryAiWorker(ai,new DiaryAiIndex(ai,cloud,p),cloud,p,owner,active::get,ref::get);
        goal=UUID.randomUUID();store.write(owner,Kind.GOAL,new Write(goal,UUID.randomUUID(),0,JSONObject.of("title","虚构水彩目标","state","ACTIVE"),null,null),false,(o,s)->{});
        action=UUID.randomUUID();store.write(owner,Kind.ACTION,new Write(action,UUID.randomUUID(),0,JSONObject.of("goalId",goal.toString(),"text","虚构计划","state","PLANNED"),null,null),false,(o,s)->{});
        diary.create(owner,new DiaryModels.Write(UUID.randomUUID(),UUID.randomUUID(),0,"虚构水彩日记","虚构记录，不是真实个人资料",LocalDate.now(ZONE),LocalDate.now(ZONE)));
    }
    @AfterEach void stop() {
        if(proceed!=null) proceed.countDown();if(worker!=null) worker.close();if(server!=null) server.stop(0);
        ai.tx(owner,()->{jdbc.update("DELETE FROM diary_ai.worker_slot WHERE owner_id=:owner",Map.of("owner",owner));return null;});
    }
    CoachingModels.Period period() { return new CoachingModels.Period("WEEK",LocalDate.now(ZONE),null,null,"covered",List.of(goal),true); }
    CoachingPeriodAi.Request request() { return new CoachingPeriodAi.Request(UUID.randomUUID(),period(),periods.preview(owner,period()).snapshotDigest()); }
    void status(int value,Runnable task) { assertEquals(value,assertThrows(DiaryFailure.class,task::run).status()); }
    @Test void sharedWorkerProducesTypedReportAndAdoptionRequiresConfirmation() {
        var request=request();var job=coaching.enqueue(owner,request);assertEquals(job.id(),coaching.enqueue(owner,request).id());assertEquals(0,calls.get());
        worker.tick();var finished=coaching.job(owner,job.id());assertEquals("SUCCEEDED",finished.state());assertEquals(1,calls.get());
        var report=coaching.report(owner,finished.reportId());assertTrue(report.sources().stream().anyMatch(s->s.kind().equals("G")));status(404,()->coaching.report(owner+1,report.id()));
        var suggestion=JSON.parseObject(report.result()).getJSONArray("suggestions").getJSONObject(0);
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("ai",ai);beans.registerSingleton("coaching",coaching);
        var originResolver=new CoachingOrigins(beans.getBeanProvider(DiaryAiStore.class),store,beans.getBeanProvider(CoachingPeriodAi.class));
        Origin origin=new Origin("PERIOD_REPORT",report.id(),0,hash(suggestion));assertEquals(suggestion,originResolver.suggestion(owner,origin));
        assertEquals(1,store.list(owner,Kind.ACTION,goal,null,1,20).total());
        var adoption=new Write(UUID.randomUUID(),UUID.randomUUID(),0,JSONObject.of("goalId",goal.toString(),"text","本人修改后确认的水彩计划","state","PLANNED"),origin,null);
        var ack=store.write(owner,Kind.ACTION,adoption,false,originResolver);assertEquals(ack,store.write(owner,Kind.ACTION,adoption,false,originResolver));
        assertEquals(2,store.list(owner,Kind.ACTION,goal,null,1,20).total());assertEquals("STALE",originResolver.status(owner,ack.id())); // new matching action changes complete scope
        coaching.delete(owner,report.id());assertEquals("CANCELED",coaching.enqueue(owner,request).state());assertEquals(2,store.list(owner,Kind.ACTION,goal,null,1,20).total());
    }
    @Test void mixedQueuesContendForLastSlotAndDisabledAiLeavesManualStoreAvailable() throws Exception {
        p.setMaxQueue(1);var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);var req=request();
        Callable<Integer> period=()->{gate.await();try{coaching.enqueue(owner,req);return 200;}catch(DiaryFailure e){return e.status();}};
        Callable<Integer> question=()->{gate.await();try{ai.enqueue(owner,DiaryAiModels.Kind.QUESTION,new DiaryAiModels.Request(UUID.randomUUID(),"虚构问题",null,null,false));return 200;}catch(DiaryFailure e){return e.status();}};
        try { var a=executor.submit(period);var b=executor.submit(question);gate.countDown();Set<Integer> results=Set.of(a.get(),b.get());assertTrue(results.contains(200));assertTrue(results.contains(429)||results.contains(503)); }
        finally { executor.shutdownNow(); }
        long queued=coaching.queued(owner)+ai.tx(owner,()->jdbc.queryForObject("SELECT count(*) FROM diary_ai.job WHERE owner_id=:owner AND state IN('QUEUED','RUNNING')",Map.of("owner",owner),Long.class));assertEquals(1,queued);
        options.setAiEnabled(false);assertFalse(coaching.available(owner));assertTrue(store.available(owner));status(503,()->coaching.enqueue(owner,request()));
    }
    @Test void slowHttpReleasesConnectionsCancelRejectsLateResultAndRestartDoesNotResend() throws Exception {
        var job=coaching.enqueue(owner,request());started=new CountDownLatch(1);proceed=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        try {
            var running=executor.submit(worker::tick);assertTrue(started.await(5,TimeUnit.SECONDS));
            assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());assertTrue(diary.available(owner));
            worker.tick();assertEquals(1,calls.get(),"A second consumer cannot enter while the global lease is held");
            coaching.cancel(owner,job.id());proceed.countDown();running.get(8,TimeUnit.SECONDS);assertEquals("CANCELED",coaching.job(owner,job.id()).state());assertEquals(0,coaching.reports(owner));
        } finally { proceed.countDown();executor.shutdownNow(); }
        var next=coaching.enqueue(owner,request());UUID token=UUID.randomUUID();assertTrue(ai.acquire(owner,token));
        ai.tx(owner,()->{jdbc.update("UPDATE diary_coaching.period_job SET state='RUNNING',lease_token=:token WHERE owner_id=:owner AND id=:id",Map.of("owner",owner,"id",next.id(),"token",UUID.randomUUID()));return null;});
        ai.release(owner,token);int before=calls.get();worker.tick();assertEquals("UNCERTAIN",coaching.job(owner,next.id()).state());assertEquals(before,calls.get());
    }
    @Test void forgedFeedbackEvidenceCannotTurnPlanIntoExecution() {
        var preview=periods.preview(owner,period());var source=preview.sources().stream().filter(s->s.kind().equals("A")).findFirst().orElseThrow();
        var claim=new CoachingEvidence.Claim("已经完成练习","EXECUTION",List.of(source.sourceId()),List.of(new CoachingEvidence.Quote(source.sourceId(),"虚构计划")));
        var result=new CoachingEvidence.Result("虚构覆盖",List.of(),List.of(),List.of(claim),List.of(),List.of(),List.of("没有实际反馈"));
        status(400,()->CoachingEvidence.validate(JSON.toJSONString(result),preview.sources()));
        assertEquals(0,calls.get());
    }
    @Test void bothQueuesReserveFinalReportSlotAndDailyGenerationBudget() {
        p.setMaxReports(1);var periodJob=coaching.enqueue(owner,request());
        status(429,()->ai.enqueue(owner,DiaryAiModels.Kind.QUESTION,new DiaryAiModels.Request(UUID.randomUUID(),"fictional",null,null,false)));
        p.setDailyGenerations(1);worker.tick();assertEquals("SUCCEEDED",coaching.job(owner,periodJob.id()).state());assertEquals(1,calls.get());
        status(429,()->ai.reserve(owner,1,0));
        coaching.delete(owner,coaching.job(owner,periodJob.id()).reportId());
        var next=coaching.enqueue(owner,request());worker.tick();assertEquals("FAILED",coaching.job(owner,next.id()).state());assertEquals(1,calls.get());
    }
    @Test void queuesAreClaimedInBothAgeOrdersAndRevocationCannotPublishLateResponse() throws Exception {
        var first=coaching.enqueue(owner,request());var question=ai.enqueue(owner,DiaryAiModels.Kind.QUESTION,new DiaryAiModels.Request(UUID.randomUUID(),"fictional",null,null,false));
        worker.tick();assertEquals("SUCCEEDED",coaching.job(owner,first.id()).state());assertEquals(DiaryAiModels.State.QUEUED,ai.job(owner,question.id()).state());
        var second=coaching.enqueue(owner,request());worker.tick();assertNotEquals(DiaryAiModels.State.QUEUED,ai.job(owner,question.id()).state());assertEquals("QUEUED",coaching.job(owner,second.id()).state());
        started=new CountDownLatch(1);proceed=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        try { var running=executor.submit(worker::tick);assertTrue(started.await(5,TimeUnit.SECONDS));active.set(false);proceed.countDown();running.get(8,TimeUnit.SECONDS);assertEquals("FAILED",coaching.job(owner,second.id()).state());assertEquals(1,coaching.reports(owner)); }
        finally { proceed.countDown();executor.shutdownNow(); }
    }
    @Test void realPgDisconnectAfterHttpBecomesUnknownOnRecoveryWithoutResending() throws Exception {
        var job=coaching.enqueue(owner,request());started=new CountDownLatch(1);proceed=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        try(var admin=java.sql.DriverManager.getConnection("jdbc:postgresql://127.0.0.1:15432/diary_qa","postgres","isolated-diary-admin")) {
            var running=executor.submit(worker::tick);assertTrue(started.await(5,TimeUnit.SECONDS));
            try {
                try(var statement=admin.createStatement()) { statement.execute("ALTER ROLE diary_runtime NOLOGIN");statement.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename='diary_runtime' AND datname='diary_qa'"); }
                proceed.countDown();running.get(20,TimeUnit.SECONDS);
            } finally { try(var statement=admin.createStatement()) { statement.execute("ALTER ROLE diary_runtime LOGIN");statement.execute("UPDATE diary_ai.worker_slot SET expires_at=now()-interval '1 second' WHERE owner_id="+owner); } }
        } finally { proceed.countDown();executor.shutdownNow(); }
        worker.tick();assertEquals("UNCERTAIN",coaching.job(owner,job.id()).state());assertEquals(1,calls.get());assertEquals(0,coaching.reports(owner));
    }
    @Test void originalDiaryAiSuggestionUsesSameOwnerAndServerVerifiedAdoption() {
        var entry=ai.tx(owner,()->ai.select(owner,null,1).get(0));
        var scope=new DiaryAiModels.Scope(List.of(entry.id()),null,null,null);
        var request=new DiaryAiModels.Request(UUID.randomUUID(),null,scope,ai.preview(owner,scope).snapshotDigest(),false);
        var queued=ai.enqueue(owner,DiaryAiModels.Kind.REVIEW,request);UUID token=UUID.randomUUID();assertTrue(ai.acquire(owner,token));
        var work=ai.claim(owner,token);
        var source=new DiaryAiModels.Source("S1",entry.id(),entry.revision(),entry.title(),entry.coveredFrom(),entry.coveredTo(),entry.createdAt(),0,DiaryAiModels.points(entry.body()),entry.body(),DiaryAiText.hash(entry.body()));
        var statement=new DiaryAiModels.Statement("虚构原日记概览",List.of("S1"),List.of(new DiaryAiModels.Quote("S1",entry.body())));
        var suggestion=new DiaryAiModels.Suggestion("fictional legacy suggestion","optional plan",List.of("S1"),"five minutes","next entry",false);
        ai.complete(owner,work,token,List.of(source),new DiaryAiModels.Result(null,statement,List.of(),List.of(),List.of(suggestion),List.of("fictional only"),false),false);ai.release(owner,token);
        UUID reportId=ai.job(owner,queued.id()).reportId();var value=JSON.parseObject(ai.report(owner,reportId).result()).getJSONArray("suggestions").getJSONObject(0);
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("ai",ai);beans.registerSingleton("coaching",coaching);
        var resolver=new CoachingOrigins(beans.getBeanProvider(DiaryAiStore.class),store,beans.getBeanProvider(CoachingPeriodAi.class));
        Origin origin=new Origin("AI_REPORT",reportId,0,hash(value));assertEquals(value,resolver.suggestion(owner,origin));status(404,()->resolver.suggestion(owner+1,origin));
        status(400,()->resolver.suggestion(owner,new Origin("AI_REPORT",reportId,19,hash(value))));
        var adopted=new Write(UUID.randomUUID(),UUID.randomUUID(),0,JSONObject.of("goalId",goal.toString(),"text","fictional manually confirmed legacy plan","state","PLANNED"),origin,null);
        var ack=store.write(owner,Kind.ACTION,adopted,false,resolver);assertEquals("CURRENT",resolver.status(owner,ack.id()));
        ai.deleteReport(owner,reportId);assertEquals("STALE",resolver.status(owner,ack.id()));assertEquals(2,store.list(owner,Kind.ACTION,goal,null,1,20).total());assertEquals(0,calls.get());
    }
}
