package xyz.luyu.diary.ai;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import xyz.luyu.diary.coaching.CoachingPeriodAi;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static xyz.luyu.diary.ai.DiaryAiModels.*;

/** Exercises real lease/budget rows and loopback generation without waiting for the 90-second lease. */
@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class DiaryLongGenerationPostgresTest {
    DiaryAiPostgresTest diary;
    CoachingPeriodAiTest period;

    @BeforeAll static void connect(){DiaryAiPostgresTest.connect();CoachingPeriodAiTest.connect();}
    @AfterAll static void close(){DiaryAiPostgresTest.close();CoachingPeriodAiTest.close();}
    @BeforeEach void setup() throws Exception {diary=new DiaryAiPostgresTest();diary.fixture();}
    @AfterEach void stop(){if(period!=null)period.stop();if(diary!=null)diary.stop();}

    private static void assertHeartbeatRenewsLease(DiaryAiStore store,long owner,Runnable heartbeat) {
        assertNotNull(heartbeat);
        store.tx(owner,()->{
            assertEquals(1,store.jdbc.update("UPDATE diary_ai.worker_slot SET expires_at=now()+interval '20 seconds' WHERE slot=1 AND owner_id=:owner AND expires_at>now()",Map.of("owner",owner)));
            return null;
        });
        heartbeat.run();
        assertTrue(store.tx(owner,()->store.jdbc.queryForObject("SELECT expires_at>now()+interval '70 seconds' FROM diary_ai.worker_slot WHERE slot=1 AND owner_id=:owner",Map.of("owner",owner),Boolean.class)));
    }

    @Test void diaryWorkerPassesALeaseRenewingHeartbeatAndSendsOnlyOneGeneration() {
        UUID entry=diary.entry("虚构记录：周一完成了排期核对。");
        var scope=diary.selected(entry);var preview=diary.store.preview(diary.owner,scope);
        var job=diary.store.enqueue(diary.owner,Kind.REVIEW,new Request(UUID.randomUUID(),"请展开复盘",scope,preview.snapshotDigest(),false));
        diary.reply.set(diary.catalogReply(true));
        var observed=mock(DiaryAiCloud.class);var callbacks=new AtomicInteger();
        when(observed.generate(anyString(),anyString(),any(UUID.class),any(Runnable.class))).thenAnswer(call->{
            assertHeartbeatRenewsLease(diary.store,diary.owner,call.getArgument(3));callbacks.incrementAndGet();
            return diary.cloud.generate(call.getArgument(0),call.getArgument(1),call.getArgument(2));
        });
        var options=spy(diary.p);doReturn(true).when(options).configured();
        var worker=new DiaryAiWorker(diary.store,diary.index,observed,options,diary.owner,()->true);
        try {
            worker.tick();
            assertEquals(State.SUCCEEDED,diary.store.job(diary.owner,job.id()).state());
            assertEquals(1,callbacks.get());assertEquals(1,diary.calls.get());
            verify(observed,times(1)).generate(anyString(),anyString(),eq(job.id()),any(Runnable.class));
            verifyNoMoreInteractions(observed);
        } finally {worker.close();}
    }

    @Test void periodWorkerPassesALeaseRenewingHeartbeatAndSendsOnlyOneGeneration() throws Exception {
        period=new CoachingPeriodAiTest();period.fixture();
        var original=new DiaryAiCloud(period.p,HttpClient.newHttpClient(),uri->{});
        var observed=mock(DiaryAiCloud.class);var callbacks=new AtomicInteger();
        when(observed.generate(anyString(),anyString(),any(UUID.class),any(Runnable.class))).thenAnswer(call->{
            assertHeartbeatRenewsLease(period.ai,period.owner,call.getArgument(3));callbacks.incrementAndGet();
            return original.generate(call.getArgument(0),call.getArgument(1),call.getArgument(2));
        });
        var coaching=new CoachingPeriodAi(period.store,period.periods,period.options,period.ai,observed,period.p);
        var job=coaching.enqueue(period.owner,period.request());
        var worker=new DiaryAiWorker(period.ai,new DiaryAiIndex(period.ai,original,period.p),observed,period.p,period.owner,period.active::get,()->coaching);
        try {
            worker.tick();
            assertEquals("SUCCEEDED",coaching.job(period.owner,job.id()).state());
            assertEquals(1,callbacks.get());assertEquals(1,period.calls.get());
            verify(observed,times(1)).generate(anyString(),anyString(),eq(job.id()),any(Runnable.class));
            verifyNoMoreInteractions(observed);
        } finally {worker.close();}
    }

    @Test void usageCreatesTheCompletionDayRowAndAccumulatesTokensWithoutReservingAnotherAttempt() {
        var params=Map.of("owner",diary.owner,"day",LocalDate.now(ZoneId.of("Asia/Shanghai")));
        assertEquals(0,diary.store.tx(diary.owner,()->diary.store.jdbc.queryForObject("SELECT count(*) FROM diary_ai.usage_budget WHERE owner_id=:owner AND day=:day",params,Integer.class)));
        diary.store.usage(diary.owner,12L,null);
        diary.store.usage(diary.owner,7L,9L);
        diary.store.usage(diary.owner,null,3L);
        var row=diary.store.tx(diary.owner,()->diary.store.jdbc.queryForMap("SELECT generations,embedding_blocks,input_tokens,output_tokens FROM diary_ai.usage_budget WHERE owner_id=:owner AND day=:day",params));
        assertEquals(19L,((Number)row.get("input_tokens")).longValue());
        assertEquals(12L,((Number)row.get("output_tokens")).longValue());
        assertEquals(0,((Number)row.get("generations")).intValue());
        assertEquals(0,((Number)row.get("embedding_blocks")).intValue());
    }
}
