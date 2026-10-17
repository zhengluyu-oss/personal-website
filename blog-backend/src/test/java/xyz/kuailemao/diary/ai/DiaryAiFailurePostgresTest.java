package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** Uses only the established loopback disposable PostgreSQL fixture and fictional owners. */
@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class DiaryAiFailurePostgresTest {
    DiaryAiPostgresTest diary;CoachingPeriodAiTest period;
    @BeforeAll static void connect(){DiaryAiPostgresTest.connect();CoachingPeriodAiTest.connect();}
    @AfterAll static void close(){DiaryAiPostgresTest.close();CoachingPeriodAiTest.close();}
    @AfterEach void cleanup(){if(diary!=null)diary.stop();if(period!=null)period.stop();}
    static String truncated(){return JSON.toJSONString(Map.of("choices",List.of(Map.of("finish_reason","length","message",Map.of("content","{partial fictional JSON"))),"usage",Map.of("prompt_tokens",10,"completion_tokens",7)));}
    void diaryFixture(boolean rejectEmbedding) throws Exception {
        diary=new DiaryAiPostgresTest();diary.fixture();
        diary.server.removeContext("/fixture");diary.server.createContext("/fixture",e->{
            diary.calls.incrementAndGet();var body=JSON.parseObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            boolean embedding=body.containsKey("input");String response=embedding?"{\"data\":[{\"index\":0,\"embedding\":[1,0,0]}],\"usage\":{\"prompt_tokens\":5}}":truncated();
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);try{e.sendResponseHeaders(embedding && rejectEmbedding?401:200,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}
        });
    }
    DiaryAiWorker worker(){var options=spy(diary.p);doReturn(true).when(options).configured();return new DiaryAiWorker(diary.store,diary.index,diary.cloud,options,diary.owner,()->true);}
    long input(){return diary.store.tx(diary.owner,()->DiaryAiPostgresTest.jdbc.queryForObject("SELECT coalesce(input_tokens,0) FROM diary_ai.usage_budget WHERE owner_id=:owner AND day=:day",Map.of("owner",diary.owner,"day",LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))),Long.class));}
    @Test void acceptedQuestionPersistsTruncationAndUsageExactlyOnceWithoutRepeatingCalls() throws Exception {
        diaryFixture(false);UUID entry=diary.entry("虚构测试水彩记录");diary.indexAll();long before=input();int callsBefore=diary.calls.get();
        var request=diary.question(diary.selected(entry));var job=diary.store.enqueue(diary.owner,Kind.QUESTION,request);
        try(var ignored=new WorkerClose(worker())) {ignored.worker.tick();var done=diary.store.job(diary.owner,job.id());
            assertEquals(State.FAILED,done.state());assertTrue(done.message().contains("截断"));assertTrue(done.possiblyCharged());assertNull(done.reportId());
            assertEquals(before+15,input());assertEquals(callsBefore+2,diary.calls.get());
            assertEquals(job.id(),diary.store.enqueue(diary.owner,Kind.QUESTION,request).id());ignored.worker.tick();
            assertEquals(before+15,input());assertEquals(callsBefore+2,diary.calls.get());assertEquals(1,diary.diary.read(diary.owner,entry).revision());
        }
    }
    @Test void embeddingAuthenticationFailureNeverInvokesGeneration() throws Exception {
        diaryFixture(true);var job=diary.store.enqueue(diary.owner,Kind.QUESTION,diary.question(null));
        try(var ignored=new WorkerClose(worker())) {ignored.worker.tick();var done=diary.store.job(diary.owner,job.id());
            assertEquals(State.FAILED,done.state());assertTrue(done.message().contains("问题向量"));assertTrue(done.message().contains("拒绝认证"));assertNull(done.reportId());assertEquals(1,diary.calls.get());
            int generations=diary.store.tx(diary.owner,()->DiaryAiPostgresTest.jdbc.queryForObject("SELECT generations FROM diary_ai.usage_budget WHERE owner_id=:owner",Map.of("owner",diary.owner),Integer.class));assertEquals(0,generations);
        }
    }
    @Test void periodReviewKeepsTheSameReasonAndFailedUsage() throws Exception {
        period=new CoachingPeriodAiTest();period.fixture();period.server.removeContext("/model");period.server.createContext("/model",e->{
            period.calls.incrementAndGet();e.getRequestBody().readAllBytes();byte[] bytes=truncated().getBytes(StandardCharsets.UTF_8);
            try{e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}
        });
        var job=period.coaching.enqueue(period.owner,period.request());period.worker.tick();var done=period.coaching.job(period.owner,job.id());
        assertEquals("FAILED",done.state());assertTrue(done.message().contains("截断"));assertTrue(done.possiblyCharged());assertNull(done.reportId());assertEquals(1,period.calls.get());
        period.worker.tick();assertEquals(1,period.calls.get());
        assertEquals(7L,period.ai.tx(period.owner,()->CoachingPeriodAiTest.jdbc.queryForObject("SELECT output_tokens FROM diary_ai.usage_budget WHERE owner_id=:owner",Map.of("owner",period.owner),Long.class)));
    }
    record WorkerClose(DiaryAiWorker worker) implements AutoCloseable {public void close(){worker.close();}}
}
