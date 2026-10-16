package xyz.kuailemao.diary.coaching;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.kuailemao.diary.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fixed isolated fixture only; never resolves production configuration or actual diary owners. */
@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class CoachingPostgresTest {
    static HikariDataSource pool;static NamedParameterJdbcTemplate jdbc;static DataSourceTransactionManager manager;
    CoachingStore store;CoachingPeriods periods;DiaryStore diary;long owner;
    @BeforeAll static void connect() {
        pool=new HikariDataSource();pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");pool.setUsername("diary_runtime");pool.setPassword("isolated-diary-runtime");
        pool.setMaximumPoolSize(3);pool.setMinimumIdle(0);pool.setConnectionTimeout(1500);jdbc=new NamedParameterJdbcTemplate(pool);manager=new DataSourceTransactionManager(pool);
    }
    @AfterAll static void close() { if(pool!=null) pool.close(); }
    @BeforeEach void setup() { owner=(UUID.randomUUID().getLeastSignificantBits() & 0x0000ffffffffffffL)+1000;store=new CoachingStore(jdbc,manager);periods=new CoachingPeriods(store);diary=new DiaryStore(jdbc,manager); }
    Write request(UUID id,long revision,JSONObject data) { return new Write(id,UUID.randomUUID(),revision,data,null,null); }
    Ack write(Kind kind,Write request) { return store.write(owner,kind,request,false,(o,s)->{throw new AssertionError("No origin expected");}); }
    UUID goal() { UUID id=UUID.randomUUID();write(Kind.GOAL,request(id,0,JSONObject.of("title","虚构：水彩练习","criterion","本人反馈是否有帮助","state","ACTIVE")));return id; }
    UUID action(UUID goal) { UUID id=UUID.randomUUID();write(Kind.ACTION,request(id,0,JSONObject.of("goalId",goal.toString(),"text","虚构：尝试一张水彩","state","PLANNED")));return id; }
    JSONObject feedback(UUID action,String date,String effect) { return JSONObject.of("actionId",action.toString(),"execution","TRIED","effect",effect,"occurredOn",date,"note","虚构记录🙂"); }
    void status(int expected,Runnable action) { assertEquals(expected,assertThrows(DiaryFailure.class,action::run).status()); }

    @Test void ownerIsolationImmutableHistoryIdempotencyAndNoop() {
        assertTrue(store.available(owner));UUID id=goal();Row current=store.read(owner,Kind.GOAL,id);var change=request(id,1,JSONObject.of("title","虚构修改","state","PAUSED"));
        Ack ack=write(Kind.GOAL,change);assertEquals(ack,write(Kind.GOAL,change));assertEquals(2,store.history(owner,Kind.GOAL,id,1).total());
        status(409,()->write(Kind.GOAL,new Write(id,change.mutationId(),1,JSONObject.of("title","不同内容","state","ACTIVE"),null,null)));
        var noChange=request(id,2,store.read(owner,Kind.GOAL,id).content());assertEquals(2,write(Kind.GOAL,noChange).revision());assertEquals(2,store.history(owner,Kind.GOAL,id,1).total());
        status(409,()->write(Kind.GOAL,request(id,1,current.content())));status(404,()->store.read(owner+1,Kind.GOAL,id));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM diary_coaching.goal",Map.of(),Integer.class));
        assertThrows(DiaryFailure.class,()->store.tx(owner,false,()->jdbc.update("DELETE FROM diary_coaching.goal_revision WHERE owner_id=:owner",Map.of("owner",owner))));
        assertThrows(DiaryFailure.class,()->store.tx(owner,false,()->jdbc.update("UPDATE diary_coaching.mutation SET request_hash=request_hash WHERE owner_id=:owner",Map.of("owner",owner))));
        assertFalse(jdbc.queryForObject("SELECT has_schema_privilege(current_user,'diary_coaching','CREATE')",Map.of(),Boolean.class));
    }
    @Test void feedbackCorrectionDeleteAndActionCasAreAtomicAndHonest() {
        UUID goal=goal(),action=action(goal),id=UUID.randomUUID();JSONObject data=feedback(action,null,"UNSURE");
        write(Kind.FEEDBACK,request(id,0,data));assertEquals("PLANNED",store.read(owner,Kind.ACTION,action).content().getString("state"));
        var window=new Period("MONTH",LocalDate.now(ZONE),null,null,"covered",List.of(goal),false);
        var empty=periods.preview(owner,window);assertEquals(0,empty.statistics().feedback());assertEquals(1,empty.statistics().undated());assertNull(empty.statistics().helpfulPercent());
        data.put("occurredOn",LocalDate.now(ZONE).toString());data.put("execution","COMPLETED");data.put("effect","HELPFUL");
        status(409,()->write(Kind.FEEDBACK,new Write(id,UUID.randomUUID(),1,data,null,99L)));
        assertEquals(1,store.read(owner,Kind.FEEDBACK,id).revision());assertEquals(1,store.read(owner,Kind.ACTION,action).revision());
        Ack saved=write(Kind.FEEDBACK,new Write(id,UUID.randomUUID(),1,data,null,1L));assertEquals(2,saved.revision());assertEquals("COMPLETED",store.read(owner,Kind.ACTION,action).content().getString("state"));
        var preview=periods.preview(owner,window);assertEquals(1,preview.statistics().completed());assertEquals(100,preview.statistics().helpfulPercent());
        var deletion=new Write(id,UUID.randomUUID(),2,null,null,null);store.write(owner,Kind.FEEDBACK,deletion,true,(o,s)->{});store.write(owner,Kind.FEEDBACK,deletion,true,(o,s)->{});
        assertEquals(3,store.history(owner,Kind.FEEDBACK,id,1).total());assertEquals(0,periods.preview(owner,window).statistics().feedback());
        assertNotEquals(preview.snapshotDigest(),periods.preview(owner,window).snapshotDigest());assertEquals(1,store.list(owner,Kind.ACTION,goal,null,1,20).total());
    }
    @Test void completeScopeDetectsNewEntriesBackfillAndBoundaryDiariesWithoutDailyInference() {
        UUID goal=goal(),action=action(goal);var window=new Period("MONTH",LocalDate.of(2024,2,15),null,null,"covered",List.of(goal),true);
        var before=periods.preview(owner,window);UUID entry=UUID.randomUUID();
        diary.create(owner,new DiaryModels.Write(entry,UUID.randomUUID(),0,"虚构跨十几天","虚构：只描述一次记录，不意味着每天练习。",LocalDate.of(2024,1,28),LocalDate.of(2024,2,14)));
        var added=periods.preview(owner,window);assertNotEquals(before.snapshotDigest(),added.snapshotDigest());assertEquals(1,added.diaries().size());assertTrue(added.diaries().get(0).getBooleanValue("crossesBoundary"));
        for(String effect:List.of("HELPFUL","NOT_HELPFUL","UNSURE")) write(Kind.FEEDBACK,request(UUID.randomUUID(),0,feedback(action,"2024-02-29",effect)));
        write(Kind.FEEDBACK,request(UUID.randomUUID(),0,feedback(action,null,"NOT_EVALUATED")));
        var after=periods.preview(owner,window);assertEquals(3,after.statistics().feedback());assertEquals(1,after.statistics().undated());assertEquals(50,after.statistics().helpfulPercent());assertEquals(4,after.feedback().size());
        assertNotEquals(added.snapshotDigest(),after.snapshotDigest());assertTrue(after.withinBudget());assertTrue(after.sources().stream().anyMatch(s->s.kind().equals("F")));
        assertEquals(4,JSON.parseObject(new String(store.export(owner),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("feedback").size());
        diary.save(owner,entry,new DiaryModels.Write(entry,UUID.randomUUID(),1,"虚构过大","水".repeat(24001),LocalDate.of(2024,2,1),LocalDate.of(2024,2,14)));
        assertFalse(periods.preview(owner,window).withinBudget());
    }
    @Test void concurrentLastActiveSlotAndCasHaveSingleWinner() throws Exception {
        for(int i=0;i<19;i++) goal();var executor=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        Callable<Integer> create=()->{gate.await();try{goal();return 200;}catch(DiaryFailure failure){return failure.status();}};
        try { var a=executor.submit(create);var b=executor.submit(create);gate.countDown();assertEquals(Set.of(200,400),Set.of(a.get(),b.get())); }
        finally { executor.shutdownNow(); }
        assertEquals(20,store.list(owner,Kind.GOAL,null,"ACTIVE",1,100).total());
        UUID otherGoal=goalForOtherOwner();status(404,()->write(Kind.ACTION,request(UUID.randomUUID(),0,JSONObject.of("goalId",otherGoal.toString(),"text","不应可见","state","PLANNED"))));
    }
    UUID goalForOtherOwner() {
        UUID id=UUID.randomUUID();store.write(owner+1,Kind.GOAL,request(id,0,JSONObject.of("title","他人虚构目标","state","ACTIVE")),false,(o,s)->{});return id;
    }
}
