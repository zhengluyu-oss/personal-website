package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Real loopback HTTP only, no production credentials or diary data. */
@ExtendWith(OutputCaptureExtension.class)
class DiaryAiResponseTest {
    HttpServer server; DiaryAiCloud cloud; DiaryAiProperties p;
    volatile String reply; volatile int status=200; volatile JSONObject lastRequest;final AtomicInteger calls=new AtomicInteger();
    @BeforeEach void fixture() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/model",exchange->{
            calls.incrementAndGet();lastRequest=JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body=reply.getBytes(StandardCharsets.UTF_8);
            try {exchange.sendResponseHeaders(status,body.length);exchange.getResponseBody().write(body);} finally {exchange.close();}
        });server.start();p=new DiaryAiProperties();
        p.setGenerationModel("fictional-model");p.setGenerationKey("fictional-secret");p.setDimensions(3);
        p.setGenerationEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/model");
        p.setEmbeddingEndpoint(p.getGenerationEndpoint());p.setEmbeddingKey("fictional-secret");p.setEmbeddingModel("fictional-vector");
        cloud=new DiaryAiCloud(p,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),uri->{});
    }
    @AfterEach void close(){if(server!=null)server.stop(0);}
    void response(String finish,Map<String,Object> message) {
        reply=JSON.toJSONString(Map.of("choices",List.of(Map.of("finish_reason",finish,"message",message)),"usage",Map.of("prompt_tokens",21,"completion_tokens",13)));
    }
    @Test void truncationAndEmptyResponsesHaveDifferentReasonsAndNeverRetry() {
        response("length",Map.of("content","{}"));
        assertEquals("GENERATION_TRUNCATED",assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("fictional JSON system","fictional context")).code());
        response("stop",Map.of("content"," "));
        assertEquals("GENERATION_EMPTY",assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("fictional JSON system","fictional context")).code());
        assertEquals(2,calls.get());
    }
    @Test void absentNullableAndEmptyToolFieldsDoNotMeanAToolWasInvoked() {
        var message=new LinkedHashMap<String,Object>();message.put("content","{}");message.put("tool_calls",null);message.put("function_call",null);
        response("stop",message);assertEquals("{}",assertDoesNotThrow(()->cloud.generate("fictional JSON system","fictional context")).content());
        message.put("tool_calls",List.of());response("stop",message);
        assertDoesNotThrow(()->cloud.generate("fictional JSON system","fictional context"));
        message.put("tool_calls",List.of(Map.of("id","fictional","function",Map.of("name","must_not_execute"))));response("stop",message);
        assertEquals("GENERATION_TOOL_CALL",assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("a","b")).code());
        assertEquals(3,calls.get());
    }
    @Test void jsonModeIsExplicitAndDoesNotChangeEmbeddingThinkingOrBudgets() {
        response("stop",Map.of("content","{}"));cloud.generate("JSON system","fictional");assertFalse(lastRequest.containsKey("response_format"));
        p.setGenerationJsonOutput(true);p.setGenerationThinking(false);cloud.generate("JSON system","fictional");
        assertEquals("json_object",lastRequest.getJSONObject("response_format").getString("type"));
        assertEquals("disabled",lastRequest.getJSONObject("thinking").getString("type"));assertEquals(2000,lastRequest.getIntValue("max_tokens"));
        reply="{\"data\":[{\"index\":0,\"embedding\":[1,0,0]}]}";cloud.embed(List.of("fictional"));
        assertFalse(lastRequest.containsKey("response_format"));assertFalse(lastRequest.containsKey("thinking"));
        var source=new MapConfigurationPropertySource(Map.of("diary.ai.generation-json-output","true"));
        assertTrue(new Binder(source).bind("diary.ai",DiaryAiProperties.class).orElseThrow(IllegalStateException::new).getGenerationJsonOutput());
        assertNull(new DiaryAiProperties().getGenerationJsonOutput());
    }
    @Test void protocolAuthenticationAndRateFailuresRemainDistinctAndDoNotLeak(CapturedOutput output) {
        String secret="PRIVATE_DIARY_SECRET_PROBE";
        for(String raw:List.of("{}","{\"choices\":[]}","{\"choices\":\""+secret+"\"}",
                "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":{\"private\":\""+secret+"\"}}}]}")) {
            reply=raw;assertEquals("GENERATION_RESPONSE_INVALID",assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate(secret,secret)).code());
        }
        response(secret,Map.of("content",secret));var stopped=assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate(secret,secret));
        assertEquals("GENERATION_STOP_INVALID",stopped.code());assertEquals("OTHER",stopped.diagnostic().finish());
        response("length",Map.of("content",secret));var truncated=assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate(secret,secret));
        assertEquals(21L,truncated.diagnostic().inputTokens());assertEquals(13L,truncated.diagnostic().outputTokens());
        assertEquals(2000,truncated.diagnostic().outputLimit());assertTrue(truncated.diagnostic().possiblySent());
        reply=secret;status=401;assertEquals("GENERATION_AUTH_REJECTED",assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate(secret,secret)).code());
        status=429;assertEquals("EMBEDDING_RATE_LIMIT",assertThrows(DiaryAiCloud.Failure.class,()->cloud.embed(List.of(secret))).code());
        assertFalse(output.getAll().contains(secret));assertFalse(truncated.toString().contains(secret));assertNull(truncated.getCause());
    }
    @Test void invalidUsageNeverBecomesTokenAccounting() {
        for(String value:List.of("-1","1.5","true","\"123\"","9223372036854775808")) {
            reply="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}],\"usage\":{\"completion_tokens\":"+value+"}}";
            var failure=assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("JSON","fictional"));
            assertEquals("GENERATION_RESPONSE_INVALID",failure.code());assertNull(failure.diagnostic().outputTokens());
        }
    }
    @Test void feedbackRetainsLegacyUncertaintyAndNeverEchoesUnknownCodes() {
        assertTrue(DiaryAiFeedback.message("FAILED","GENERATION_TRUNCATED").contains("截断"));
        assertTrue(DiaryAiFeedback.message("FAILED","GENERATION_AUTH_REJECTED").contains("拒绝认证"));
        assertTrue(DiaryAiFeedback.message("UNCERTAIN","EMBEDDING_CONNECTION_FAILED").contains("问题向量"));
        assertTrue(DiaryAiFeedback.message("FAILED","GENERATION_FORMAT").contains("不足以区分"));
        assertFalse(DiaryAiFeedback.message("FAILED","PRIVATE_CODE_PROBE").contains("PRIVATE_CODE_PROBE"));
    }
}
