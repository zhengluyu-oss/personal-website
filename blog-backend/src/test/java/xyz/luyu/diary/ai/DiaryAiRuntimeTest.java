package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import xyz.luyu.diary.DiaryFailure;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Synthetic responses and deterministic wait slices, never calls a provider. */
class DiaryAiRuntimeTest {
    private DiaryAiProperties configured() {
        var p=new DiaryAiProperties();p.setEnabled(true);p.setProvider("synthetic");p.setDataPolicy("synthetic fixtures");
        p.setGenerationModel("synthetic-model");p.setEmbeddingModel("synthetic-vector");p.setDimensions(3);
        p.setGenerationEndpoint("https://provider.example/generate");p.setEmbeddingEndpoint("https://provider.example/embed");
        p.setGenerationKey("fixture-only");p.setEmbeddingKey("fixture-only");p.setAllowedHosts(Set.of("provider.example"));p.setMinimumSimilarity(0.5);
        return p;
    }
    @Test void outputBudgetAndLongGenerationDeadlineHaveIndependentBounds() {
        var p=configured();assertTrue(p.configured());assertEquals(100000,p.getMaxOutputTokens());assertEquals(1800,p.getGenerationTimeoutSeconds());
        assertEquals(30,p.getHttpTimeoutSeconds());assertEquals(16*1024*1024,p.getMaxResponseBytes());
        p.setMaxOutputTokens(100001);assertFalse(p.configured());p.setMaxOutputTokens(100000);
        p.setGenerationTimeoutSeconds(3600);assertTrue(p.configured());p.setGenerationTimeoutSeconds(3601);assertFalse(p.configured());
        p.setGenerationTimeoutSeconds(1800);p.setHttpTimeoutSeconds(31);assertFalse(p.configured());p.setHttpTimeoutSeconds(30);
        p.setMaxResponseBytes(DiaryAiProperties.MAX_RESPONSE_BYTES+1);assertFalse(p.configured());p.setMaxResponseBytes(DiaryAiProperties.MAX_RESPONSE_BYTES);
        p.setMaxOutputTokens(0);assertFalse(p.configured());
        var bound=new Binder(new MapConfigurationPropertySource(Map.of("diary.ai.max-output-tokens","100000","diary.ai.generation-timeout-seconds","1800")))
                .bind("diary.ai",DiaryAiProperties.class).orElseThrow(IllegalStateException::new);
        assertEquals(100000,bound.getMaxOutputTokens());assertEquals(1800,bound.getGenerationTimeoutSeconds());
    }
    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> response(String content) {
        var response=(HttpResponse<byte[]>)mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(JSON.toJSONBytes(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",content))),"usage",Map.of("prompt_tokens",21,"completion_tokens",13))));
        return response;
    }
    @SuppressWarnings("unchecked")
    @Test void longResponseSurvivesWaitSlicesAndKeepsOnlyOneModelRequest() throws Exception {
        var p=configured();var client=mock(HttpClient.class);
        var future=(CompletableFuture<HttpResponse<byte[]>>)mock(CompletableFuture.class);
        String text="有依据的新视角。".repeat(4000);
        var completed=response(text);
        when(future.get(anyLong(),eq(TimeUnit.NANOSECONDS))).thenThrow(new TimeoutException()).thenReturn(completed);
        when(client.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(future);
        var cloud=new DiaryAiCloud(p,client,uri->{});var heartbeats=new AtomicInteger();
        assertEquals(text,cloud.generate("JSON synthetic system","synthetic diary context",UUID.randomUUID(),heartbeats::incrementAndGet).content());
        assertEquals(1,heartbeats.get(),"renew while waiting; the worker checks completion after accounting for returned usage");
        var request=ArgumentCaptor.forClass(HttpRequest.class);
        verify(client,times(1)).sendAsync(request.capture(),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertEquals(Duration.ofSeconds(1800),request.getValue().timeout().orElseThrow());
        verify(future,never()).cancel(anyBoolean());
    }
    @SuppressWarnings("unchecked")
    @Test void inactiveTaskCancelsThePendingHttpAndRetainsItsOriginalFailure() throws Exception {
        var p=configured();var client=mock(HttpClient.class);
        var future=(CompletableFuture<HttpResponse<byte[]>>)mock(CompletableFuture.class);
        when(future.get(anyLong(),eq(TimeUnit.NANOSECONDS))).thenThrow(new TimeoutException());
        when(client.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(future);
        var cloud=new DiaryAiCloud(p,client,uri->{});var stopped=new DiaryFailure(409,"synthetic canceled task");
        var error=assertThrows(DiaryFailure.class,()->cloud.generate("JSON","synthetic",UUID.randomUUID(),()->{throw stopped;}));
        assertSame(stopped,error);verify(future).cancel(true);
        verify(client,times(1)).sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }
    @SuppressWarnings("unchecked")
    @Test void completedHttpPreservesUsageForTheWorkersFinalStateChecks() throws Exception {
        var p=configured();var client=mock(HttpClient.class);
        var future=(CompletableFuture<HttpResponse<byte[]>>)mock(CompletableFuture.class);
        var completed=response("late synthetic answer");
        when(future.get(anyLong(),eq(TimeUnit.NANOSECONDS))).thenReturn(completed);
        when(client.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any())).thenReturn(future);
        var cloud=new DiaryAiCloud(p,client,uri->{});var stopped=new DiaryFailure(409,"synthetic changed source");
        var result=cloud.generate("JSON","synthetic",UUID.randomUUID(),()->{throw stopped;});
        assertEquals("late synthetic answer",result.content());assertEquals(21L,result.inputTokens());assertEquals(13L,result.outputTokens());
        verify(future,never()).cancel(anyBoolean());
    }
}
