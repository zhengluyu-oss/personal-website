package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.*;
import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** 单次请求有总时限和响应上限；不记录供应商正文、URI、密钥或异常堆栈。 */
public final class DiaryAiCloud {
    public record Embeddings(List<float[]> vectors,Long inputTokens) {}
    public record Generation(String content,Long inputTokens,Long outputTokens) {}
    public static final class Failure extends RuntimeException {
        private final String code;private final boolean uncertain;
        public Failure(String code,boolean uncertain) { super("AI 请求未完成，请查看任务状态");this.code=code;this.uncertain=uncertain; }
        public String code(){return code;} public boolean uncertain(){return uncertain;}
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
    private JSONObject send(String endpoint,String key,Map<String,Object> payload) {
        URI uri;
        Future<?> validation=null;
        try { uri=URI.create(endpoint);validation=DESTINATIONS.submit(()->{try{destination.check(uri);}catch(Exception e){throw new CompletionException(e);}});validation.get(5,TimeUnit.SECONDS); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new Failure("ENDPOINT_REJECTED",false); }
        catch(Exception rejected) { throw new Failure("ENDPOINT_REJECTED",false); }
        finally {if(validation!=null && !validation.isDone())validation.cancel(true);}
        final HttpRequest request;
        try {
            request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(p.getHttpTimeoutSeconds()))
                    .header("Content-Type","application/json").header("Authorization","Bearer "+key)
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload))).build();
        } catch(RuntimeException invalid) { throw new Failure("CONFIGURATION_INVALID",false); }
        var future=client.sendAsync(request,ignored->new LimitedBody(p.getMaxResponseBytes()));
        try {
            HttpResponse<byte[]> response=future.get(p.getHttpTimeoutSeconds(),TimeUnit.SECONDS);
            if(response.statusCode()!=200) throw new Failure(response.statusCode()==429?"PROVIDER_RATE_LIMIT":"PROVIDER_REJECTED",false);
            JSONObject result=JSON.parseObject(response.body());
            if(result==null || result.containsKey("error")) throw new Failure("PROVIDER_FORMAT",false);
            return result;
        } catch(Failure failure) { throw failure; }
        catch(InterruptedException interrupted) { future.cancel(true);Thread.currentThread().interrupt();throw new Failure("INTERRUPTED",true); }
        catch(TimeoutException | ExecutionException failure) { future.cancel(true);throw new Failure("NETWORK_UNKNOWN",true); }
        catch(RuntimeException invalid) { throw new Failure("PROVIDER_FORMAT",false); }
    }
    public Embeddings embed(List<String> input) {
        if(input==null || input.isEmpty() || input.size()>8 || input.stream().anyMatch(s->s==null || s.isBlank() || DiaryAiModels.points(s)>1200))
            throw new Failure("EMBEDDING_INPUT",false);
        JSONObject response=send(p.getEmbeddingEndpoint(),p.getEmbeddingKey(),Map.of("model",p.getEmbeddingModel(),"input",input));
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
            return new Embeddings(Arrays.asList(vectors),usage(response,"prompt_tokens"));
        } catch(RuntimeException invalid) { throw new Failure("EMBEDDING_FORMAT",false); }
    }
    public Generation generate(String system,String context) {
        if(DiaryAiModels.points(system)+DiaryAiModels.points(context)>p.getMaxPromptCharacters())throw new Failure("CONTEXT_LIMIT",false);
        Map<String,Object> payload=new LinkedHashMap<>(Map.of("model",p.getGenerationModel(),
                "messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",context)),
                "max_tokens",p.getMaxOutputTokens()));
        if(p.getGenerationThinking()!=null)payload.put("thinking",Map.of("type",p.getGenerationThinking()?"enabled":"disabled"));
        JSONObject response=send(p.getGenerationEndpoint(),p.getGenerationKey(),payload);
        try {
            JSONArray choices=response.getJSONArray("choices");if(choices==null || choices.size()!=1)throw new IllegalArgumentException();
            JSONObject choice=choices.getJSONObject(0),message=choice.getJSONObject("message");
            if(!"stop".equals(choice.getString("finish_reason")) || message==null || message.containsKey("tool_calls") || message.containsKey("function_call"))throw new IllegalArgumentException();
            String content=message.getString("content");if(content==null || content.isBlank() || DiaryAiModels.points(content)>20000)throw new IllegalArgumentException();
            return new Generation(content,usage(response,"prompt_tokens"),usage(response,"completion_tokens"));
        } catch(RuntimeException invalid) { throw new Failure("GENERATION_FORMAT",false); }
    }
    private static Long usage(JSONObject response,String name) {
        JSONObject usage=response.getJSONObject("usage");if(usage==null || !usage.containsKey(name))return null;
        Long value=usage.getLong(name);if(value==null || value<0)throw new IllegalArgumentException();return value;
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
        private final int limit;private int size;private Flow.Subscription subscription;
        LimitedBody(int limit){this.limit=limit;}
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
        public void onNext(List<ByteBuffer> buffers){
            long addition=buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if(addition>limit-size){subscription.cancel();delegate.onError(new IllegalStateException("bounded response exceeded"));return;}
            size+=(int)addition;delegate.onNext(buffers);
        }
        public void onError(Throwable failure){delegate.onError(failure);}
        public void onComplete(){delegate.onComplete();}
    }
}
