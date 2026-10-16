package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.kuailemao.diary.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** Dedicated disposable pgvector fixture only. Never reads application/production properties. */
@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class DiaryAiPostgresTest {
    static HikariDataSource pool;static NamedParameterJdbcTemplate jdbc;static DataSourceTransactionManager tx;
    DiaryAiProperties p;DiaryAiStore store;DiaryAiIndex index;DiaryAiCloud cloud;DiaryStore diary;
    HttpServer server;long owner;AtomicInteger calls;AtomicReference<String> reply;CountDownLatch started,proceed;
    @BeforeAll static void connect(){pool=new HikariDataSource();pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");pool.setUsername("diary_runtime");pool.setPassword("isolated-diary-runtime");pool.setMaximumPoolSize(3);pool.setMinimumIdle(0);pool.setConnectionTimeout(1500);jdbc=new NamedParameterJdbcTemplate(pool);tx=new DataSourceTransactionManager(pool);}
    @AfterAll static void close(){if(pool!=null)pool.close();}
    @BeforeEach void fixture() throws Exception {
        owner=(UUID.randomUUID().getMostSignificantBits() & 0x0000ffffffffffffL)+1000;calls=new AtomicInteger();reply=new AtomicReference<>();
        p=new DiaryAiProperties();p.setProvider("synthetic");p.setDimensions(3);p.setEmbeddingModel("synthetic-v1");p.setGenerationModel("synthetic-answer");p.setMinimumSimilarity(0.8);
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/fixture",e->{
            var body=JSON.parseObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));calls.incrementAndGet();
            if(started!=null){started.countDown();try{proceed.await(10,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}}
            String response;
            if(body.containsKey("input")){var data=new ArrayList<Map<String,Object>>();for(int i=0;i<body.getJSONArray("input").size();i++)data.add(Map.of("index",i,"embedding",List.of(1,0,0)));response=JSON.toJSONString(Map.of("data",data));}
            else response=JSON.toJSONString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",reply.get())))));
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);try{e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}
        });server.start();p.setEmbeddingEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/fixture");p.setGenerationEndpoint(p.getEmbeddingEndpoint());
        cloud=new DiaryAiCloud(p,HttpClient.newHttpClient(),uri->{});store=new DiaryAiStore(jdbc,tx,p);index=new DiaryAiIndex(store,cloud,p);diary=new DiaryStore(jdbc,tx);assertTrue(store.available(owner));store.ensureProfile(owner);
    }
    @AfterEach void stop(){if(proceed!=null)proceed.countDown();if(server!=null)server.stop(0);if(store!=null)store.tx(owner,()->{jdbc.update("DELETE FROM diary_ai.worker_slot WHERE owner_id=:owner",Map.of("owner",owner));jdbc.update("UPDATE diary_ai.job SET state='CANCELED' WHERE owner_id=:owner AND state='RUNNING'",Map.of("owner",owner));return null;});}
    UUID entry(String body){UUID id=UUID.randomUUID();diary.create(owner,new DiaryModels.Write(id,UUID.randomUUID(),0,"虚构测试",body,LocalDate.of(2024,2,28),LocalDate.of(2024,3,2)));return id;}
    void status(int code,Runnable op){assertEquals(code,assertThrows(DiaryFailure.class,op::run).status());}
    Scope selected(UUID id){return new Scope(List.of(id),null,null,null);}
    Request question(Scope scope){return new Request(UUID.randomUUID(),"虚构测试",scope,null,false);}
    void indexAll(){store.indexAction(owner,new IndexRequest(UUID.randomUUID(),false),true);UUID t=UUID.randomUUID();assertTrue(store.acquire(owner,t));try{for(int i=0;i<10;i++)index.processBatch(owner,store.ensureProfile(owner),t,()->true);}finally{store.release(owner,t);}}
    @Test void fullRetrievalCurrentVersionsProfilesAndOwnerIsolation(){
        UUID id=entry("虚构测试：周末练习水彩🙂。\n".repeat(80));indexAll();String profile=store.ensureProfile(owner);
        assertEquals(1,index.coverage(owner,profile,null).indexed());assertEquals(0,index.coverage(owner+1,profile,null).total());
        var sources=index.retrieve(owner,profile,question(selected(id)),new float[]{1,0,0});assertFalse(sources.isEmpty());assertTrue(sources.size()<=2);
        for(Source s:sources)assertEquals(s.text(),slice(diary.read(owner,id).body(),s.start(),s.end()));
        diary.save(owner,id,new DiaryModels.Write(id,UUID.randomUUID(),1,"修订","完全更新后的虚构正文",null,null));
        assertEquals(0,index.coverage(owner,profile,null).indexed());status(409,()->index.retrieve(owner,profile,question(selected(id)),new float[]{1,0,0}));
        indexAll();assertEquals(2,index.retrieve(owner,profile,question(selected(id)),new float[]{1,0,0}).get(0).revision());
        p.setEmbeddingModel("synthetic-v2");String next=store.ensureProfile(owner);assertNotEquals(profile,next);assertFalse(index.status(owner,next).syncing());assertEquals(0,index.coverage(owner,next,null).indexed());
        diary.delete(owner,id,new DiaryModels.Change(UUID.randomUUID(),2,null));assertEquals(0,index.coverage(owner,next,null).total());
        diary.restore(owner,id,new DiaryModels.Change(UUID.randomUUID(),3,1L));indexAll();assertEquals(4,index.retrieve(owner,next,question(selected(id)),new float[]{1,0,0}).get(0).revision());
    }
    @Test void idempotencyPreviewNewMatchingEntryAndStaleReportAreConsistent(){
        UUID id=entry("虚构水彩记录🙂");Scope scope=selected(id);var preview=store.preview(owner,scope);
        var req=new Request(UUID.randomUUID(),null,scope,preview.snapshotDigest(),false);Job job=store.enqueue(owner,Kind.REVIEW,req);assertEquals(job.id(),store.enqueue(owner,Kind.REVIEW,req).id());
        status(409,()->store.enqueue(owner,Kind.QUESTION,new Request(req.requestId(),"不同问题",null,null,false)));
        UUID token=UUID.randomUUID();assertTrue(store.acquire(owner,token));var work=store.claim(owner,token);assertEquals(job.id(),work.id());
        var e=store.tx(owner,()->store.entry(owner,id));Source s=new Source("S1",id,1,e.title(),e.coveredFrom(),e.coveredTo(),e.createdAt(),0,points(e.body()),e.body(),DiaryAiText.hash(e.body()));
        var statement=new Statement("记录中提到水彩",List.of("S1"),List.of(new Quote("S1","水彩")));
        store.complete(owner,work,token,List.of(s),new Result(null,statement,List.of(statement),List.of(),List.of(),List.of("仅是虚构夹具"),false),false);store.release(owner,token);
        UUID report=store.job(owner,job.id()).reportId();assertEquals("虚构水彩记录🙂",store.report(owner,report).sources().get(0).text());status(404,()->store.report(owner+1,report));
        diary.save(owner,id,new DiaryModels.Write(id,UUID.randomUUID(),1,"修改","虚构修改后",null,null));status(409,()->store.report(owner,report));assertEquals(State.STALE,store.job(owner,job.id()).state());
        assertNull(store.tx(owner,()->jdbc.queryForObject("SELECT result FROM diary_ai.report WHERE owner_id=:owner AND id=:id",Map.of("owner",owner,"id",report),String.class)));
        store.deleteReport(owner,report);assertEquals(2,diary.history(owner,id,1).total());assertEquals(State.CANCELED,store.enqueue(owner,Kind.REVIEW,req).state());
        var range=new Scope(null,"created",LocalDate.now().minusDays(1),LocalDate.now().plusDays(1));var before=store.preview(owner,range);entry("新加入范围的虚构记录");
        status(409,()->store.enqueue(owner,Kind.REVIEW,new Request(UUID.randomUUID(),null,range,before.snapshotDigest(),false)));
    }
    @Test void atomicBudgetsLeasesCancellationAndRestartDoNotDuplicate() throws Exception {
        p.setDailyGenerations(1);var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<Integer> reserve=()->{gate.await();try{store.reserve(owner,1,0);return 200;}catch(DiaryFailure d){return d.status();}};
        try{var a=executor.submit(reserve);var b=executor.submit(reserve);gate.countDown();assertEquals(Set.of(200,429),Set.of(a.get(),b.get()));}finally{executor.shutdownNow();}
        store.usage(owner,12L,null);store.usage(owner,null,3L);
        UUID t=UUID.randomUUID();assertTrue(store.acquire(owner,t));assertFalse(store.acquire(owner,UUID.randomUUID()));
        Job j=store.enqueue(owner,Kind.QUESTION,question(null));var w=store.claim(owner,t);store.cancel(owner,j.id());status(409,()->store.complete(owner,w,t,List.of(),DiaryAiEvidence.insufficient(),false));assertEquals(State.CANCELED,store.job(owner,j.id()).state());store.release(owner,t);
    }
    @Test void lostRunningAttemptBecomesUnknownAndNeverClaimedAgain(){
        UUID token=UUID.randomUUID();assertTrue(store.acquire(owner,token));Job j=store.enqueue(owner,Kind.QUESTION,question(null));assertNotNull(store.claim(owner,token));store.release(owner,token);
        UUID next=UUID.randomUUID();assertTrue(store.acquire(owner,next));assertNull(store.claim(owner,next));assertEquals(State.UNCERTAIN,store.job(owner,j.id()).state());assertTrue(store.job(owner,j.id()).possiblyCharged());store.release(owner,next);
    }
    @Test void concurrentIdenticalRequestsReturnOneDurableJob() throws Exception {
        var req=question(null);var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<UUID> submit=()->{gate.await();return store.enqueue(owner,Kind.QUESTION,req).id();};
        try{var a=executor.submit(submit);var b=executor.submit(submit);gate.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));}finally{executor.shutdownNow();}
    }
    @Test void completeQuestionAndReviewWorkerChainsUseCurrentEvidence() {
        UUID id=entry("虚构水彩记录🙂");indexAll();
        var statement=new Statement("日记提到水彩",List.of("S1"),List.of(new Quote("S1","水彩")));
        reply.set(JSON.toJSONString(new Result(statement,null,List.of(statement),List.of(),List.of(),List.of("夹具只测试链路，不代表模型质量"),false)));
        var spy=org.mockito.Mockito.spy(p);org.mockito.Mockito.doReturn(true).when(spy).configured();
        var worker=new DiaryAiWorker(store,index,cloud,spy,owner,()->true);
        try{
            Job q=store.enqueue(owner,Kind.QUESTION,question(selected(id)));worker.tick();assertEquals(State.SUCCEEDED,store.job(owner,q.id()).state());assertEquals(1,store.report(owner,store.job(owner,q.id()).reportId()).sources().size());
            reply.set(JSON.toJSONString(new Result(null,statement,List.of(statement),List.of(),List.of(),List.of("仅覆盖选定篇目"),false)));
            var preview=store.preview(owner,selected(id));Job review=store.enqueue(owner,Kind.REVIEW,new Request(UUID.randomUUID(),null,selected(id),preview.snapshotDigest(),false));
            worker.tick();assertEquals(State.SUCCEEDED,store.job(owner,review.id()).state());assertEquals("虚构水彩记录🙂",store.report(owner,store.job(owner,review.id()).reportId()).sources().get(0).text());
        }finally{worker.close();}
    }
    @Test void slowEmbeddingDoesNotHoldPgConnectionAndLateIndexCannotPublish() throws Exception {
        UUID id=entry("虚构慢请求");store.indexAction(owner,new IndexRequest(UUID.randomUUID(),false),true);UUID token=UUID.randomUUID();assertTrue(store.acquire(owner,token));
        started=new CountDownLatch(1);proceed=new CountDownLatch(1);var executor=Executors.newSingleThreadExecutor();
        try{var result=executor.submit(()->index.processBatch(owner,store.ensureProfile(owner),token,()->true));assertTrue(started.await(5,TimeUnit.SECONDS));assertEquals(0,pool.getHikariPoolMXBean().getActiveConnections());
            diary.save(owner,id,new DiaryModels.Write(id,UUID.randomUUID(),1,"更新","虚构新版",null,null));proceed.countDown();result.get(10,TimeUnit.SECONDS);assertEquals(0,index.coverage(owner,store.ensureProfile(owner),null).indexed());
        }finally{proceed.countDown();executor.shutdownNow();store.release(owner,token);}
    }
}
