package xyz.luyu.diary.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class DiaryAiCloudTest {
    private HttpServer server;private DiaryAiCloud cloud;private DiaryAiProperties p;
    private final AtomicReference<String> reply=new AtomicReference<>();
    private volatile int status=200,delay;
    @BeforeEach void fixture() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/fixture",exchange->{
            assertEquals("Bearer fixture-secret",exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().readAllBytes();
            if(delay>0)try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            byte[] bytes=reply.get().getBytes(StandardCharsets.UTF_8);
            try{exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);}finally{exchange.close();}
        });server.start();
        p=new DiaryAiProperties();p.setDimensions(3);p.setEmbeddingModel("fixture-vector");p.setGenerationModel("fixture-generator");
        p.setEmbeddingKey("fixture-secret");p.setGenerationKey("fixture-secret");
        p.setEmbeddingEndpoint("http://127.0.0.1:"+server.getAddress().getPort()+"/fixture");p.setGenerationEndpoint(p.getEmbeddingEndpoint());
        cloud=new DiaryAiCloud(p,HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),uri->{});
    }
    @AfterEach void close(){if(server!=null)server.stop(0);}
    @Test void embeddingOrderingDimensionsAndMissingUsageAreChecked() {
        reply.set("{\"data\":[{\"index\":1,\"embedding\":[0,1,0]},{\"index\":0,\"embedding\":[1,0,0]}]}");
        var result=cloud.embed(List.of("synthetic one","synthetic two"));assertArrayEquals(new float[]{1,0,0},result.vectors().get(0));assertNull(result.inputTokens());
        for(String vector:List.of("[1,2]","[0,0,0]","[1e999,0,0]","[1,null,0]","[1,true,0]","[1,\"2\",0]")) {
            reply.set("{\"data\":[{\"index\":0,\"embedding\":"+vector+"}]}");
            assertEquals("EMBEDDING_FORMAT",assertThrows(DiaryAiCloud.Failure.class,()->cloud.embed(List.of("synthetic"))).code());
        }
    }
    @Test void generationRejectsToolsTruncationAndProviderErrorsWithoutLeakingBody() {
        reply.set("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"{}\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}");
        var result=cloud.generate("fixture system","fixture context");assertEquals(10L,result.inputTokens());assertEquals(5L,result.outputTokens());
        reply.set("{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"content\":\"partial private output\"}}]}");
        assertEquals("GENERATION_TRUNCATED",assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("a","b")).code());
        reply.set("fixture-secret and private context must not escape");status=401;
        var error=assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("a","b"));assertFalse(error.getMessage().contains("fixture-secret"));
        status=429;assertEquals("EMBEDDING_RATE_LIMIT",assertThrows(DiaryAiCloud.Failure.class,()->cloud.embed(List.of("x"))).code());
    }
    @Test void totalResponseTimeAndSizeAreBounded() {
        p.setMaxResponseBytes(128);reply.set("x".repeat(1024));
        assertThrows(DiaryAiCloud.Failure.class,()->cloud.embed(List.of("x")));
        p.setMaxResponseBytes(4096);p.setHttpTimeoutSeconds(1);delay=1800;reply.set("{}");long began=System.nanoTime();
        assertTrue(assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("a","b")).uncertain());
        assertTrue((System.nanoTime()-began)/1e9<1.6,"includes the entire response body deadline");
    }
    @Test void productionDestinationPolicyRejectsLocalHttpUserinfoAndUnapprovedHosts() {
        for(String uri:List.of("http://example.com/embeddings","https://127.0.0.1/embeddings","https://[::1]/embeddings", "https://user:pass@example.com/embeddings","https://example.com/embeddings?key=secret"))
            assertThrows(Exception.class,()->DiaryAiCloud.validateDestination(URI.create(uri),Set.of("example.com","127.0.0.1","[::1]")));
        assertThrows(Exception.class,()->DiaryAiCloud.validateDestination(URI.create("https://unapproved.invalid/"),Set.of("example.com")));
    }
    @Test void invalidJsonRedirectAndDisconnectNeverPublishAResponse() {
        reply.set("not-json-private-probe");assertEquals("EMBEDDING_RESPONSE_INVALID",assertThrows(DiaryAiCloud.Failure.class,()->cloud.embed(List.of("fictional"))).code());
        status=302;reply.set("redirect-private-probe");assertEquals("GENERATION_PROVIDER_REJECTED",assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("a","b")).code());
        server.stop(0);assertTrue(assertThrows(DiaryAiCloud.Failure.class,()->cloud.generate("a","b")).uncertain());
    }
}
