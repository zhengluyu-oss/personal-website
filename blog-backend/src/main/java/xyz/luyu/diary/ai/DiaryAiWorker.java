package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import jakarta.annotation.PreDestroy;
import xyz.luyu.diary.DiaryFailure;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static xyz.luyu.diary.ai.DiaryAiModels.*;

/** 一个调度线程加数据库全局租约；所有 HTTP 调用都在数据库事务之外。 */
public final class DiaryAiWorker {
    private final DiaryAiStore store;private final DiaryAiIndex index;private final DiaryAiCloud cloud;
    private final DiaryAiProperties p;private final long owner;private final BooleanSupplier active;
    private final java.util.function.Supplier<DiaryAiExtension> extensions;
    private final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"private-diary-ai");t.setDaemon(true);return t;});
    public DiaryAiWorker(DiaryAiStore store,DiaryAiIndex index,DiaryAiCloud cloud,DiaryAiProperties p,long owner,BooleanSupplier active) {
        this(store,index,cloud,p,owner,active,()->null);
    }
    public DiaryAiWorker(DiaryAiStore store,DiaryAiIndex index,DiaryAiCloud cloud,DiaryAiProperties p,long owner,BooleanSupplier active,java.util.function.Supplier<DiaryAiExtension> extensions) {
        this.store=store;this.index=index;this.cloud=cloud;this.p=p;this.owner=owner;this.active=active;this.extensions=extensions;
    }
    @EventListener(ApplicationReadyEvent.class) public void start(){executor.scheduleWithFixedDelay(this::tick,5,5,TimeUnit.SECONDS);}
    @PreDestroy public void close(){executor.shutdownNow();}
    public void tick() {
        if(!p.configured())return;UUID token=UUID.randomUUID();boolean acquired=false;
        try {
            if(!active.getAsBoolean() || !store.available(owner))return;
            String profile=store.ensureProfile(owner);acquired=store.acquire(owner,token);if(!acquired)return;
            DiaryAiExtension extra=extensions.get();
            if(extra!=null) {
                extra.recover(owner,token);java.time.Instant next=extra.oldest(owner);
                if(next!=null) {
                    var own=store.oldestQueued(owner);
                    if(own==null || next.isBefore(own)) { extra.process(owner,token,active);return; }
                }
            }
            var work=store.claim(owner,token);
            if(work==null)index.processBatch(owner,profile,token,active);else process(work,profile,token);
        } catch(RuntimeException ignored) {
            // 下一轮只恢复数据库持久状态；不记录可能携带正文的异常，不重发未知生成。
        } finally {if(acquired)try{store.release(owner,token);}catch(RuntimeException ignored){/* 租约自行到期 */}}
    }
    private void check(DiaryAiStore.Work work) {
        if(!active.getAsBoolean())throw new DiaryFailure(403,"账号已不可用");
        if(!store.current(owner,work) || store.job(owner,work.id()).state()!=State.RUNNING)throw new DiaryFailure(409,"来源或任务已变化");
    }
    private void process(DiaryAiStore.Work work,String profile,UUID token) {
        long started=System.nanoTime();Long inputTokens=null,outputTokens=null;
        boolean sent=false;
        String invalidOutput=null;List<DiaryDiagnostics.Source> diagnosticSources=List.of();
        try {
            check(work);List<Source> sources;boolean partial=false;
            DiaryConversationStore.Context conversation=new DiaryConversationStore(store).context(owner,work.id());
            boolean fullReview=work.kind()==Kind.REVIEW || conversation!=null && conversation.conversationKind()==Kind.REVIEW;
            if(!fullReview) {
                Coverage c=index.coverage(owner,profile,work.request().scope());partial=c.total()!=c.indexed();
                if(partial && !work.request().allowPartialIndex())throw new DiaryFailure(409,"索引尚未完成");
                store.reserve(owner,0,1);
                store.cloudIntent(owner,work,token);sent=true;
                String search=conversation==null?work.request().question():DiaryConversationText.retrievalQuestion(work.request().question(),conversation.history());
                var embedding=cloud.embed(List.of(search),work.id());store.usage(owner,embedding.inputTokens(),null);check(work);
                Request retrieval=conversation==null?work.request():new Request(work.request().requestId(),search,work.request().scope(),null,work.request().allowPartialIndex());
                sources=index.retrieve(owner,profile,retrieval,embedding.vectors().get(0));
            } else {
                store.preview(owner,work.request().scope());
                sources=store.snapshot(owner,()->{
                    if(!work.digest().equals(store.scopeDigest(owner,work.request().scope())))throw new DiaryFailure(409,"范围已变化");
                    List<Source> values=new ArrayList<>();
                    for(Entry e:store.select(owner,work.request().scope(),41))values.add(new Source("S"+(values.size()+1),e.id(),e.revision(),e.title(),e.coveredFrom(),e.coveredTo(),e.createdAt(),0,points(e.body()),e.body(),DiaryAiText.hash(e.body())));
                    return List.copyOf(values);
                });
            }
            check(work);
            if(sources.isEmpty()){store.complete(owner,work,token,sources,DiaryAiEvidence.insufficient(),partial);DiaryAiAudit.event(work.id(),work.kind().name(),"COMPLETE","INSUFFICIENT",elapsed(started),inputTokens,outputTokens,sent);return;}
            String system=DiaryAiEvidence.system(work.kind());
            if(conversation!=null)system+="历史对话只用于理解本轮追问的指代；历史AI回答不是日记证据，不可引用或据此断言个人事实。本轮所有事实必须重新由本轮sources和quoteCatalog支持。";
            String history=conversation==null?"":DiaryConversationText.history(conversation.history(),1600);
            String context=JSON.toJSONString(Map.of("question",Objects.toString(work.request().question(),""),"scope",Objects.toString(work.request().scope(),"全部当前记录"),"partialIndex",partial,"sources",sources,"conversationHistory",history));
            int remaining=p.getMaxPromptCharacters()-points(system)-points(context)-32;
            DiaryQuoteCatalog catalog=DiaryQuoteCatalog.build(work.id(),sources.stream().map(s->new DiaryQuoteCatalog.SourceText(s.sourceId(),"D",s.revision(),s.text())).toList(),Math.min(5000,remaining));
            if(catalog.items().isEmpty())throw DiaryFailure.invalid("输入超过引文预算，请缩小范围");
            context=catalog.attach(context);
            if(points(system)+points(context)>p.getMaxPromptCharacters())throw DiaryFailure.invalid("输入超过模型预算，请缩小范围");
            store.reserve(owner,1,0);check(work);store.preparing(owner,work,token,sources,partial);sent=true;
            var generation=cloud.generate(system,context,work.id());
            inputTokens=generation.inputTokens();outputTokens=generation.outputTokens();
            invalidOutput=generation.content();
            diagnosticSources=sources.stream().map(s->new DiaryDiagnostics.Source(s.sourceId(),"D",s.entryId(),s.revision(),s.text())).toList();
            store.usage(owner,generation.inputTokens(),generation.outputTokens());check(work);
            String hydrated=catalog.hydrate(generation.content());
            DiaryValidation.check(hydrated,sources.stream().map(s->new DiaryValidation.Evidence(s.sourceId(),s.text(),"D")).toList(),work.kind().name());
            Result result;
            try {result=DiaryAiEvidence.validate(hydrated,sources,work.kind());}
            catch(DiaryAiCloud.Failure invalid){throw DiaryValidation.unknown();}
            store.complete(owner,work,token,sources,result,partial);
            DiaryAiAudit.event(work.id(),work.kind().name(),"COMPLETE","OK",elapsed(started),inputTokens,outputTokens,sent);
        } catch(RuntimeException error) {
            if(error instanceof DiaryValidation.Issue issue) {
                DiaryAiAudit.event(work.id(),work.kind().name(),"VALIDATION",issue.reason().name(),elapsed(started),inputTokens,outputTokens,sent);
                try {store.recordInvalid(owner,work.id(),token,work.kind().name(),issue,invalidOutput,diagnosticSources,DiaryAiEvidence.PROMPT_VERSION);}
                catch(RuntimeException unavailable){store.fail(owner,work,token,State.FAILED,"OUTPUT_EVIDENCE_INVALID",sent);}
                return;
            }
            State state=error instanceof DiaryAiCloud.Failure f && f.uncertain()?State.UNCERTAIN:error instanceof DiaryFailure d && d.status()==409?State.STALE:State.FAILED;
            String code=error instanceof DiaryAiCloud.Failure f?f.code():error instanceof DiaryFailure d && d.status()==429?"DAILY_LIMIT":"TASK_REJECTED";
            DiaryAiAudit.event(work.id(),work.kind().name(),"FAILURE",code,elapsed(started),inputTokens,outputTokens,sent);
            try {
                if(error instanceof DiaryAiCloud.Failure f && f.diagnostic()!=null)store.usage(owner,f.diagnostic().inputTokens(),f.diagnostic().outputTokens());
            } finally {store.fail(owner,work,token,state,code,sent);}
        }
    }
    private static long elapsed(long start){return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
}
