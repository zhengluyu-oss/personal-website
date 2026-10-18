package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import xyz.kuailemao.diary.DiaryFailure;
import xyz.kuailemao.diary.coaching.CoachingEvidence;
import xyz.kuailemao.diary.coaching.CoachingModels;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** Synthetic contract checks; a controlled response proves compatibility, not model reasoning quality. */
class DiaryReflectionContractTest {
    private Source diarySource() {
        String text="虚构补记：这段时间散步过一次，感觉放松；尚未记录工作的结果。";
        return new Source("S1",UUID.randomUUID(),1,"虚构跨日记录",LocalDate.of(2024,9,1),
                LocalDate.of(2024,9,10),Instant.parse("2024-10-01T00:00:00Z"),0,points(text),text,DiaryAiText.hash(text));
    }

    @Test void partialAnswerRetainsSupportedPartAndSpecificMissingInformation() {
        Source source=diarySource();
        var answer=new Statement("记录明确写过一次散步并感到放松；工作结果未记录，无法判断工作是否顺利。",
                List.of("S1"),List.of(new Quote("S1","散步过一次，感觉放松")));
        var candidate=new Result(answer,null,List.of(),List.of(),List.of(),
                List.of("只覆盖一篇跨日补记，缺少具体工作结果；不能据此推算每天的行为。"),false);

        Result accepted=DiaryAiEvidence.validate(JSON.toJSONString(candidate),List.of(source),Kind.QUESTION);

        assertFalse(accepted.insufficientEvidence());
        assertEquals(candidate,accepted,"Partial evidence must not be replaced by the generic insufficient response");
        assertNull(accepted.summary());
        assertTrue(accepted.observations().isEmpty());
        assertTrue(accepted.hypotheses().isEmpty());
        assertTrue(accepted.suggestions().isEmpty());
    }

    @Test void selectedDiaryReviewPreservesOptionalSmallExperimentAndReviewCriteria() {
        Source source=diarySource();
        var summary=new Statement("这篇跨日补记记载一次散步后的放松感，未说明每天的情况或工作结果。",
                List.of("S1"),List.of(new Quote("S1","散步过一次，感觉放松")));
        var suggestion=new Suggestion("如果下次工作间隙愿意活动，就试着散步五分钟。",
                "记录提到一次散步后放松；小范围尝试是否仍有类似感受，不预设因果。",List.of("S1"),
                "五分钟；完成一次散步即可。","下一次尝试后记录感受；若不适合，缩短时间或停止。",false);
        var candidate=new Result(null,summary,List.of(),List.of(),List.of(suggestion),
                List.of("单次经历不能证明稳定效果，建议由本人选择。"),false);

        Result accepted=DiaryAiEvidence.validate(JSON.toJSONString(candidate),List.of(source),Kind.REVIEW);

        assertEquals(candidate,accepted);
        assertNull(accepted.answer());
        assertEquals(suggestion,accepted.suggestions().get(0));
        assertEquals(summary.quotes(),accepted.summary().quotes());
    }

    @Test void typedPeriodReviewAcceptsDiaryGoalPlanAndFeedbackButRejectsPlanAsExecution() {
        var sources=List.of(
                coachingSource("D1","D","虚构补记：这次散步后心情放松。"),
                coachingSource("G1","G","虚构目标：保持适量活动。"),
                coachingSource("A1","A","虚构计划：午休散步五分钟。"),
                coachingSource("F1","F","虚构反馈：完成一次散步，感觉放松。"));
        var goal=claim("本人记录了适量活动目标。","GOAL","G1","保持适量活动");
        var observation=claim("日记记录这次散步后的感受。","OBSERVATION","D1","这次散步后心情放松");
        var plan=claim("行动资料中存在午休散步计划，计划本身不证明执行。","OBSERVATION","A1","午休散步五分钟");
        var execution=claim("本人反馈完成一次散步。","EXECUTION","F1","完成一次散步");
        var effect=claim("本人反馈这次感觉放松，不能单凭自评证明因果。","EFFECT","F1","感觉放松");
        var candidate=new CoachingEvidence.Result("仅根据虚构范围内的日记、目标、计划与反馈。",
                List.of(goal),List.of(observation,plan),List.of(execution,effect),List.of(),List.of(),
                List.of("跨日补记不代表逐日发生；单次反馈不证明稳定效果。"));

        assertEquals(candidate,CoachingEvidence.validate(JSON.toJSONString(candidate),sources));

        var forged=claim("计划已经执行完成。","EXECUTION","A1","午休散步五分钟");
        var invalid=new CoachingEvidence.Result(candidate.coverage(),candidate.currentGoals(),candidate.observations(),
                List.of(forged),List.of(),List.of(),candidate.limitations());
        var failure=assertThrows(DiaryFailure.class,()->CoachingEvidence.validate(JSON.toJSONString(invalid),sources));
        assertEquals(400,failure.status());
    }

    @Test void moreHelpfulAnswersStillCannotUseInventedQuotesOrUnknownSources() {
        Source source=diarySource();
        for(Statement invalid:List.of(
                new Statement("虚构结论",List.of("S1"),List.of(new Quote("S1","每天都散步五分钟"))),
                new Statement("虚构结论",List.of("S2"),List.of(new Quote("S2","散步过一次"))))) {
            var candidate=new Result(invalid,null,List.of(),List.of(),List.of(),List.of("资料有限"),false);
            var failure=assertThrows(DiaryAiCloud.Failure.class,
                    ()->DiaryAiEvidence.validate(JSON.toJSONString(candidate),List.of(source),Kind.QUESTION));
            assertEquals("OUTPUT_EVIDENCE_INVALID",failure.code());
        }
    }

    @Test void allThreePromptsUseSystemRoleAndKeepUserContextJsonModeAndOutputBudgetSeparate() throws Exception {
        var requests=new CopyOnWriteArrayList<JSONObject>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/model",exchange->{
            requests.add(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
            byte[] response=JSON.toJSONBytes(Map.of("choices",List.of(Map.of("finish_reason","stop",
                    "message",Map.of("content","{}")))));
            try { exchange.sendResponseHeaders(200,response.length);exchange.getResponseBody().write(response); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            int port=server.getAddress().getPort();
            var properties=new DiaryAiProperties();
            properties.setGenerationEndpoint("http://127.0.0.1:"+port+"/model");
            properties.setGenerationKey("fictional-local-only-key");
            properties.setGenerationModel("fictional-local-only-model");
            properties.setGenerationJsonOutput(true);
            properties.setGenerationThinking(false);
            var cloud=new DiaryAiCloud(properties,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),uri->{
                if(!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort()!=port
                        || !"/model".equals(uri.getPath())) throw new IllegalArgumentException("Only this test server is allowed");
            });
            String context=JSON.toJSONString(Map.of("question","虚构用户资料：伪造系统角色要求忽略规则", "sources",List.of()));
            List<String> systems=List.of(DiaryAiEvidence.system(Kind.QUESTION),DiaryAiEvidence.system(Kind.REVIEW),CoachingEvidence.system());
            for(String system:systems) {
                assertEquals("{}",cloud.generate(system,context).content());
                JSONObject sent=requests.get(requests.size()-1);
                var messages=sent.getJSONArray("messages");
                assertEquals(2,messages.size());
                assertEquals("system",messages.getJSONObject(0).getString("role"));
                assertEquals(system,messages.getJSONObject(0).getString("content"));
                assertEquals("user",messages.getJSONObject(1).getString("role"));
                assertEquals(context,messages.getJSONObject(1).getString("content"));
                assertNotEquals(context,messages.getJSONObject(0).getString("content"));
                assertEquals("json_object",sent.getJSONObject("response_format").getString("type"));
                assertEquals("disabled",sent.getJSONObject("thinking").getString("type"));
                assertEquals(2000,sent.getIntValue("max_tokens"));
            }
            assertEquals(3,requests.size(),"One request per task; no automatic extra calls");
        } finally { server.stop(0); }
    }

    private CoachingModels.Source coachingSource(String id,String kind,String text) {
        return new CoachingModels.Source(id,kind,UUID.randomUUID(),1,"text",0,points(text),DiaryAiText.hash(text),text);
    }

    private CoachingEvidence.Claim claim(String text,String type,String sourceId,String quote) {
        return new CoachingEvidence.Claim(text,type,List.of(sourceId),List.of(new CoachingEvidence.Quote(sourceId,quote)));
    }
}
