package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static xyz.luyu.diary.ai.DiaryAiModels.*;

/** Uses the disposable pgvector fixture and loopback model; never reads private configuration. */
@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class DiaryDeepReflectionPostgresTest {
    DiaryAiPostgresTest fixture;
    final AtomicReference<JSONObject> context=new AtomicReference<>();
    final AtomicReference<String> system=new AtomicReference<>();
    final AtomicBoolean reviewReply=new AtomicBoolean(true);

    @BeforeAll static void connect(){DiaryAiPostgresTest.connect();}
    @AfterAll static void close(){DiaryAiPostgresTest.close();}
    @BeforeEach void setup() throws Exception {
        fixture=new DiaryAiPostgresTest();fixture.fixture();
        fixture.server.removeContext("/fixture");
        fixture.server.createContext("/fixture",exchange->{
            JSONObject body=JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            fixture.calls.incrementAndGet();
            assertFalse(body.containsKey("input"),"Complete reviews must not replace the scope with an embedding search");
            var messages=body.getJSONArray("messages");
            system.set(messages.getJSONObject(0).getString("content"));
            context.set(JSON.parseObject(messages.getJSONObject(1).getString("content")));
            String quote=context.get().getJSONArray("quoteCatalog").getJSONObject(0).getString("quoteId");
            String generated=fixture.catalogReply(reviewReply.get()).replace("$QUOTE_ID$",quote);
            byte[] bytes=JSON.toJSONBytes(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",generated)))));
            try{exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        });
    }
    @AfterEach void stop(){fixture.stop();}

    @Test void customReviewQuestionAndFollowupsKeepTheCompleteScopeAndBoundedUserContext() {
        String firstBody="周一写下任务计划。当天确认项目排期。";
        String secondBody="周二根据工作安排调整计划。记录了已经完成的部分。";
        UUID first=fixture.entry(firstBody),second=fixture.entry(secondBody);
        Scope scope=new Scope(List.of(first,second),null,null,null);
        var preview=fixture.store.preview(fixture.owner,scope);
        String goal="请从我未考虑的角度复盘；我的目标是减少加班，而不是增加任务量。";
        Request request=new Request(UUID.randomUUID(),goal,scope,preview.snapshotDigest(),false);
        var conversations=new DiaryConversationStore(fixture.store);
        var options=spy(fixture.p);doReturn(true).when(options).configured();
        var worker=new DiaryAiWorker(fixture.store,fixture.index,fixture.cloud,options,fixture.owner,()->true);
        try {
            var opened=conversations.start(fixture.owner,Kind.REVIEW,request);
            assertEquals(opened.job().id(),conversations.start(fixture.owner,Kind.REVIEW,request).job().id());
            fixture.status(409,()->conversations.start(fixture.owner,Kind.REVIEW,new Request(request.requestId(),"不同的复盘重点",scope,preview.snapshotDigest(),false)));
            worker.tick();assertEquals(State.SUCCEEDED,fixture.store.job(fixture.owner,opened.job().id()).state());
            assertEquals(goal,context.get().getString("question"));
            assertEquals(Set.of(firstBody,secondBody),context.get().getJSONArray("sources").stream().map(value->((JSONObject)value).getString("text")).collect(java.util.stream.Collectors.toSet()));
            assertEquals(goal,conversations.detail(fixture.owner,opened.conversationId(),1).conversation().firstQuestion());
            assertEquals(goal,conversations.detail(fixture.owner,opened.conversationId(),1).turns().get(0).question());

            reviewReply.set(false);
            for(int i=0;i<10;i++) {
                String correction="第"+i+"轮补充：工作安排不能取消，请根据这个条件调整建议。";
                var submitted=conversations.followup(fixture.owner,opened.conversationId(),new DiaryConversationStore.Followup(UUID.randomUUID(),correction));
                worker.tick();assertEquals(State.SUCCEEDED,fixture.store.job(fixture.owner,submitted.job().id()).state());
                assertEquals(correction,context.get().getString("question"));
                assertEquals(2,context.get().getJSONArray("sources").size());
                assertTrue(system.get().startsWith(DiaryAiEvidence.system(Kind.QUESTION,true)));
            }
            var history=context.get().getJSONArray("conversationHistory");
            assertEquals(9,history.size());
            assertEquals(goal,history.getJSONObject(0).getString("question"));
            assertEquals("第8轮补充：工作安排不能取消，请根据这个条件调整建议。",history.getJSONObject(history.size()-1).getString("question"));
            assertEquals(11,fixture.calls.get());
        } finally {worker.close();}
    }
}
