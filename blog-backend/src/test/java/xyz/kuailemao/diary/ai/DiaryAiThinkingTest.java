package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DiaryAiThinkingTest {
    @Test void unspecifiedThinkingDoesNotChangeOtherProviders() throws Exception {
        assertFalse(request(null).containsKey("thinking"));
    }

    @Test void deepSeekNonThinkingModeIsSentInTheActualHttpBody() throws Exception {
        JSONObject body=request(false);
        assertEquals("disabled",body.getJSONObject("thinking").getString("type"));
        assertEquals("deepseek-flash",body.getString("model"));
        assertEquals(2000,body.getIntValue("max_tokens"));
        assertEquals(2,body.getJSONArray("messages").size());
    }

    @Test void explicitThinkingRemainsAvailable() throws Exception {
        assertEquals("enabled",request(true).getJSONObject("thinking").getString("type"));
    }

    @Test void externalHyphenatedFalseIsBoundAsFalseRatherThanAbsent() {
        var source=new MapConfigurationPropertySource(Map.of("diary.ai.generation-thinking","false"));
        var properties=new Binder(source).bind("diary.ai",Bindable.of(DiaryAiProperties.class)).get();
        assertEquals(Boolean.FALSE,properties.getGenerationThinking());
        assertNull(new DiaryAiProperties().getGenerationThinking());
    }

    private JSONObject request(Boolean thinking) throws Exception {
        AtomicReference<JSONObject> captured=new AtomicReference<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/generation",exchange->{
            captured.set(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
            byte[] body="{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}]}".getBytes(StandardCharsets.UTF_8);
            try { exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            DiaryAiProperties p=new DiaryAiProperties();p.setGenerationThinking(thinking);
            p.setGenerationEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/generation");
            p.setGenerationModel("deepseek-flash");p.setGenerationKey("synthetic-test-key");
            var cloud=new DiaryAiCloud(p,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),uri->{});
            assertEquals("{}",cloud.generate("Synthetic system","Synthetic user").content());
            return captured.get();
        } finally { server.stop(0); }
    }
}
