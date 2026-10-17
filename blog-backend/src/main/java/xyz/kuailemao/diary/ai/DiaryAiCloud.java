package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.IOException;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 单次请求有总时限和响应上限；不记录供应商正文、URI、密钥或异常堆栈。 */
public final class DiaryAiCloud {
    private static final Logger LOG=LoggerFactory.getLogger(DiaryAiCloud.class);
    private enum Stage { EMBEDDING, GENERATION }
    public record Diagnostic(String stage,String finish,Integer httpStatus,Long inputTokens,Long outputTokens,
                             int outputLimit,int responseBytes,long elapsedMillis,boolean possiblySent) {}
    public record Embeddings(List<float[]> vectors,Long inputTokens) {}
    public record Generation(String content,Long inputTokens,Long outputTokens) {}
    public static final class Failure extends RuntimeException {
        private final String code;private final boolean uncertain;
        private final Diagnostic diagnostic;
        public Failure(String code,boolean uncertain) { this(code,uncertain,null); }
        private Failure(String code,boolean uncertain,Diagnostic diagnostic) { super("AI 请求未完成，请查看任务状态");this.code=code;this.uncertain=uncertain;this.diagnostic=diagnostic; }
        public String code(){return code;} public boolean uncertain(){return uncertain;}
        public Diagnostic diagnostic(){return diagnostic;}
    }
    private final class Attempt {
        final Stage stage;final UUID task;final long started=System.nanoTime();
        String code="NOT_COMPLETED",finish="NONE";Integer http;Long input,output;int bytes;boolean sent,usageValid=true;
        Attempt(Stage stage,UUID task){this.stage=stage;this.task=task;}
        Diagnostic snapshot(){return new Diagnostic(stage.name(),finish,http,input,output,p.getMaxOutputTokens(),bytes,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),sent);}
        Failure fail(String suffix,boolean uncertain){code=stage.name()+"_"+suffix;return new Failure(code,uncertain,snapshot());}
        void readUsage(JSONObject response){try{input=usage(response,"prompt_tokens");output=usage(response,"completion_tokens");}catch(RuntimeException invalid){input=null;output=null;usageValid=false;}}
        void record(){
            // Only fixed internal labels, validated numbers and an internal UUID. Never log a Throwable or provider strings.
            LOG.info("diary_ai_request task={} stage={} code={} finish={} http={} input_tokens={} output_tokens={} output_limit={} response_bytes={} elapsed_ms={} possibly_sent={}",
                task,stage.name(),code,finish,http,input,output,p.getMaxOutputTokens(),bytes,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),sent);
        }
    }
    @FunctionalInterface interface Destination { void check(URI uri) throws Exception; }
    private static final ExecutorService DESTINATIONS=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new SynchronousQueue<>(),r->{Thread t=new Thread(r,"diary-ai-destination");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private final DiaryAiProperties p;private final HttpClient client;private final Destination destination;
    public DiaryAiCloud(DiaryAiProperties properties) {
        this(properties,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build(),
                uri->validateDestination(uri,properties.getAllowedHosts()));
    }
    // Package-private injection seam for an actual loopback HTTP test fixture, never exposed as configuration.
    DiaryAiCloud(DiaryAiProperties properties,HttpClient client,Destination destination) {
        this.p=properties;this.client=client;this.destination=destination;
    }
    static void validateDestination(URI uri,Set<String> allowed) throws Exception {
        String host=uri.getHost();
        if(!"https".equals(uri.getScheme()) || host==null || !allowed.contains(host.toLowerCase(Locale.ROOT))
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || uri.getPort()!=-1 && uri.getPort()!=443)
            throw new Failure("ENDPOINT_REJECTED",false);
        for(InetAddress address:InetAddress.getAllByName(host)) {
            byte[] bytes=address.getAddress();int first=bytes[0]&255;
            if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress()
                    || bytes.length==4 && (first==0 || first==100 && ((bytes[1]&255)>=64 && (bytes[1]&255)<=127) || first>=224)
                    || bytes.length==16 && (first&254)==252)
                throw new Failure("ENDPOINT_REJECTED",false);
        }
    }
    private JSONObject send(Attempt attempt,String endpoint,String key,Map<String,Object> payload) {
        URI uri;
        Future<?> validation=null;
        try { uri=URI.create(endpoint);validation=DESTINATIONS.submit(()->{try{destination.check(uri);}catch(Exception e){throw new CompletionException(e);}});validation.get(5,TimeUnit.SECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw attempt.fail("ENDPOINT_REJECTED",false); }
        catch(Exception rejected) { throw attempt.fail("ENDPOINT_REJECTED",false); }
        finally {if(validation!=null && !validation.isDone())validation.cancel(true);}
        final HttpRequest request;
        try {
            request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(p.getHttpTimeoutSeconds()))
                    .header("Content-Type","application/json").header("Authorization","Bearer "+key)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload))).build();
        } catch(RuntimeException invalid) { throw attempt.fail("CONFIGURATION_INVALID",false); }
        attempt.sent=true;
        var future=client.sendAsync(request,ignored->new LimitedBody(p.getMaxResponseBytes()));
        try {
            HttpResponse<byte[]> response=future.get(p.getHttpTimeoutSeconds(),TimeUnit.SECONDS);
            attempt.http=response.statusCode();attempt.bytes=response.body().length;
            if(response.statusCode()!=200) throw attempt.fail(switch(response.statusCode()) {case 401,403->"AUTH_REJECTED";case 429->"RATE_LIMIT";default->"PROVIDER_REJECTED";},false);
            JSONObject result=JSON.parseObject(response.body());
            if(result==null || result.get("error")!=null) throw attempt.fail("RESPONSE_INVALID",false);
            attempt.readUsage(result);
            return result;
        } catch(Failure failure) { throw failure; }
        catch(InterruptedException interrupted) { future.cancel(true);Thread.currentThread().interrupt();throw attempt.fail("INTERRUPTED",true); }
        catch(TimeoutException timeout) { future.cancel(true);throw attempt.fail("REQUEST_TIMEOUT",true); }
        catch(ExecutionException failure) {
            future.cancel(true);Throwable cause=failure;boolean connection=false;
            for(int i=0;i<8 && cause!=null;i++,cause=cause.getCause()) {
                if(cause instanceof ResponseLimit)throw attempt.fail("RESPONSE_TOO_LARGE",false);
                if(cause instanceof HttpConnectTimeoutException)throw attempt.fail("CONNECT_TIMEOUT",true);
                if(cause instanceof HttpTimeoutException)throw attempt.fail("REQUEST_TIMEOUT",true);
                if(cause instanceof IOException)connection=true;
            }
            if(connection)throw attempt.fail("CONNECTION_FAILED",true);
            throw attempt.fail("NETWORK_UNKNOWN",true);
        }
        catch(RuntimeException invalid) { throw attempt.fail("RESPONSE_INVALID",false); }
    }
    public Embeddings embed(List<String> input) {
        return embed(input,null);
    }
    public Embeddings embed(List<String> input,UUID task) {
        Attempt attempt=new Attempt(Stage.EMBEDDING,task);
        try {
        if(input==null || input.isEmpty() || input.size()>8 || input.stream().anyMatch(s->s==null || s.isBlank() || DiaryAiModels.points(s)>1200))
            throw attempt.fail("INPUT",false);
        JSONObject response=send(attempt,p.getEmbeddingEndpoint(),p.getEmbeddingKey(),Map.of("model",p.getEmbeddingModel(),"input",input));
        try {
            JSONArray data=response.getJSONArray("data");
            if(data==null || data.size()!=input.size()) throw new IllegalArgumentException();
            float[][] vectors=new float[input.size()][];
            for(int i=0;i<data.size();i++) {
                JSONObject item=data.getJSONObject(i);Integer index=item.getInteger("index");JSONArray raw=item.getJSONArray("embedding");
                if(index==null || index<0 || index>=vectors.length || vectors[index]!=null || raw==null || raw.size()!=p.getDimensions())throw new IllegalArgumentException();
                float[] vector=new float[raw.size()];double norm=0;
                for(int j=0;j<raw.size();j++) { if(!(raw.get(j) instanceof Number))throw new IllegalArgumentException();vector[j]=raw.getFloatValue(j);if(!Float.isFinite(vector[j]))throw new IllegalArgumentException();norm+=(double)vector[j]*vector[j]; }
                if(norm==0)throw new IllegalArgumentException();vectors[index]=vector;
            }
            if(!attempt.usageValid)throw attempt.fail("RESPONSE_INVALID",false);
            attempt.code="OK";return new Embeddings(Arrays.asList(vectors),attempt.input);
        } catch(Failure failure){throw failure;}catch(RuntimeException invalid) { throw attempt.fail("FORMAT",false); }
        } finally {attempt.record();}
    }
    public Generation generate(String system,String context) {
        return generate(system,context,null);
    }
    public Generation generate(String system,String context,UUID task) {
        Attempt attempt=new Attempt(Stage.GENERATION,task);
        try {
        if(DiaryAiModels.points(system)+DiaryAiModels.points(context)>p.getMaxPromptCharacters())throw attempt.fail("CONTEXT_LIMIT",false);
        Map<String,Object> payload=new LinkedHashMap<>(Map.of("model",p.getGenerationModel(),
                "messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",context)),
                "max_tokens",p.getMaxOutputTokens()));
        if(p.getGenerationThinking()!=null)payload.put("thinking",Map.of("type",p.getGenerationThinking()?"enabled":"disabled"));
        if(Boolean.TRUE.equals(p.getGenerationJsonOutput()))payload.put("response_format",Map.of("type","json_object"));
        JSONObject response=send(attempt,p.getGenerationEndpoint(),p.getGenerationKey(),payload);
        try {
            if(!(response.get("choices") instanceof JSONArray choices) || choices.size()!=1 || !(choices.get(0) instanceof JSONObject choice))throw attempt.fail("RESPONSE_INVALID",false);
            Object finish=choice.get("finish_reason");
            attempt.finish=finish instanceof String value && Set.of("stop","length","tool_calls","function_call","content_filter","insufficient_system_resource").contains(value)?((String)finish).toUpperCase(Locale.ROOT):"OTHER";
            if("length".equals(finish))throw attempt.fail("TRUNCATED",false);
            if("tool_calls".equals(finish) || "function_call".equals(finish))throw attempt.fail("TOOL_CALL",false);
            if("content_filter".equals(finish))throw attempt.fail("REFUSED",false);
            if(!"stop".equals(finish))throw attempt.fail("STOP_INVALID",false);
            if(!(choice.get("message") instanceof JSONObject message))throw attempt.fail("RESPONSE_INVALID",false);
            Object tools=message.get("tool_calls"),function=message.get("function_call");
            if(tools!=null && !(tools instanceof JSONArray) || function!=null && !(function instanceof JSONObject))throw attempt.fail("RESPONSE_INVALID",false);
            if(tools instanceof JSONArray array && !array.isEmpty() || function instanceof JSONObject object && !object.isEmpty())throw attempt.fail("TOOL_CALL",false);
            if(function instanceof JSONObject)throw attempt.fail("RESPONSE_INVALID",false);
            if(message.get("refusal")!=null && !"".equals(message.get("refusal")))throw attempt.fail("REFUSED",false);
            Object raw=message.get("content");if(raw==null || raw instanceof String value && value.isBlank())throw attempt.fail("EMPTY",false);
            if(!(raw instanceof String content) || DiaryAiModels.points(content)>20000 || !attempt.usageValid)throw attempt.fail("RESPONSE_INVALID",false);
            attempt.code="OK";return new Generation(content,attempt.input,attempt.output);
        } catch(Failure failure){throw failure;}catch(RuntimeException invalid) { throw attempt.fail("RESPONSE_INVALID",false); }
        } finally {attempt.record();}
    }
    private static Long usage(JSONObject response,String name) {
        Object raw=response.get("usage");if(raw==null)return null;
        if(!(raw instanceof JSONObject usage))throw new IllegalArgumentException();
        if(!usage.containsKey(name) || usage.get(name)==null)return null;
        if(!(usage.get(name) instanceof Number number))throw new IllegalArgumentException();
        long value=new BigDecimal(number.toString()).longValueExact();if(value<0)throw new IllegalArgumentException();return value;
    }
    private static final class ResponseLimit extends RuntimeException {}
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;private int size;private Flow.Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
        public void onNext(List<ByteBuffer> buffers){
            long addition=buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if(addition>limit-size){subscription.cancel();delegate.onError(new ResponseLimit());return;}
            size+=(int)addition;delegate.onNext(buffers);
        }
        public void onError(Throwable failure){delegate.onError(failure);}
        public void onComplete(){delegate.onComplete();}
    }
}
