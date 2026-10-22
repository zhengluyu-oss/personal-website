package xyz.kuailemao.diary.coaching;

import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import xyz.kuailemao.diary.DiaryFailure;
import xyz.kuailemao.diary.ai.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.BooleanSupplier;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.points;

/** 仅由 DiaryAiWorker 在既有全局租约内执行；HTTP 前后不持有PG连接。 */
public final class CoachingPeriodAi implements DiaryAiExtension {
    public record Request(UUID requestId,CoachingModels.Period period,String snapshotDigest) {}
    public record Job(UUID id,String state,String message,UUID reportId,boolean possiblyCharged,Instant createdAt) {}
    public record Report(UUID id,String result,Window window,List<Source> sources,String model,Instant createdAt) {}
    public record ReportSummary(UUID id,String state,Window window,String model,Instant createdAt) {}
    private record Work(UUID id,Request request,String digest) {}
    private final CoachingStore store;private final CoachingPeriods periods;private final CoachingProperties options;
    private final DiaryAiStore ai;private final DiaryAiCloud cloud;private final DiaryAiProperties p;private final DiaryAiExecution execution;
    public CoachingPeriodAi(CoachingStore store,CoachingPeriods periods,CoachingProperties options,DiaryAiStore ai,DiaryAiCloud cloud,DiaryAiProperties p) {
        this.store=store;this.periods=periods;this.options=options;this.ai=ai;this.cloud=cloud;this.p=p;this.execution=new DiaryAiExecution(ai);
    }
    public boolean available(long owner) { return options.isAiEnabled() && p.configured() && ai.available(owner) && store.available(owner); }
    public String description() { return "将发送至 "+p.getProvider()+" / "+p.getGenerationModel()+"；数据处理说明："+p.getDataPolicy(); }
    private void require(long owner) { if(!available(owner)) throw new DiaryFailure(503,"期间AI尚未配置或关闭，人工目标与反馈仍可使用"); }
    @Override public long queued(long owner) { return store.tx(owner,false,()->store.jdbc.queryForObject("SELECT count(*) FROM diary_coaching.period_job WHERE owner_id=:owner AND state IN('QUEUED','RUNNING')",Map.of("owner",owner),Long.class)); }
    @Override public long reports(long owner) { return store.tx(owner,false,()->store.jdbc.queryForObject("SELECT count(*) FROM diary_coaching.period_report WHERE owner_id=:owner",Map.of("owner",owner),Long.class)); }
    @Override public Instant oldest(long owner) {
        if(!available(owner)) return null;
        return store.tx(owner,false,()->{Timestamp time=store.jdbc.queryForObject("SELECT min(created_at) FROM diary_coaching.period_job WHERE owner_id=:owner AND state='QUEUED'",Map.of("owner",owner),Timestamp.class);return time==null?null:time.toInstant();});
    }
    public Job enqueue(long owner,Request request) {
        require(owner);
        if(request==null || request.requestId()==null || request.period()==null || request.snapshotDigest()==null || !request.snapshotDigest().matches("[0-9a-f]{64}")) throw DiaryFailure.invalid("请先预览并确认本次范围");
        String hash=hash(request);
        return execution.queue(owner,()->store.tx(owner,false,()->{
            var params=CoachingStore.parameters(owner,null).addValue("request",request.requestId()).addValue("hash",hash);
            var previous=store.jdbc.query("SELECT id,request_hash FROM diary_coaching.period_job WHERE owner_id=:owner AND request_id=:request",params,(r,n)->{
                if(!hash.equals(r.getString("request_hash"))) throw DiaryFailure.conflict();return r.getObject("id",UUID.class);
            });
            if(!previous.isEmpty()) return job(owner,previous.get(0));
            execution.queueCounts(owner,queued(owner),reports(owner));
            Preview preview=periods.preview(owner,request.period());validatePreview(preview,request.snapshotDigest());
            UUID id=UUID.randomUUID();params.addValue("id",id).addValue("payload",JSON.toJSONString(request)).addValue("digest",preview.snapshotDigest());
            store.jdbc.update("INSERT INTO diary_coaching.period_job(owner_id,id,request_id,request_hash,state,payload,scope_digest) VALUES(:owner,:id,:request,:hash,'QUEUED',CAST(:payload AS jsonb),:digest)",params);
            return job(owner,id);
        }));
    }
    public Job job(long owner,UUID id) {
        return store.tx(owner,false,()->{
            var rows=store.jdbc.query("SELECT j.*,(SELECT id FROM diary_coaching.period_report r WHERE r.owner_id=j.owner_id AND r.job_id=j.id) report_id FROM diary_coaching.period_job j WHERE owner_id=:owner AND id=:id",CoachingStore.parameters(owner,id),(r,n)->{
                String state=r.getString("state");String message=DiaryAiFeedback.message(state,r.getString("error_code"));
                return new Job(id,state,message,r.getObject("report_id",UUID.class),r.getBoolean("possibly_charged"),r.getTimestamp("created_at").toInstant());
            });if(rows.isEmpty()) throw new DiaryFailure(404,"任务不存在或不可访问");return rows.get(0);
        });
    }
    public Job cancel(long owner,UUID id) {
        store.tx(owner,false,()->{store.jdbc.update("UPDATE diary_coaching.period_job SET state='CANCELED',payload=NULL,sources=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id AND state IN('QUEUED','RUNNING')",CoachingStore.parameters(owner,id));return null;});return job(owner,id);
    }
    @Override public void recover(long owner,UUID lease) {
        execution.lease(owner,lease);
        store.tx(owner,false,()->{store.jdbc.update("UPDATE diary_coaching.period_job SET state='UNCERTAIN',possibly_charged=true,error_code='RESTART_UNKNOWN',sources=NULL,updated_at=now() WHERE owner_id=:owner AND state='RUNNING' AND lease_token IS DISTINCT FROM :token",new MapSqlParameterSource("owner",owner).addValue("token",lease));return null;});
    }
    @Override public void process(long owner,UUID lease,BooleanSupplier active) {
        require(owner);execution.lease(owner,lease);
        Work work=store.tx(owner,false,()->{
            var params=new MapSqlParameterSource("owner",owner).addValue("token",lease);
            var rows=store.jdbc.query("SELECT * FROM diary_coaching.period_job WHERE owner_id=:owner AND state='QUEUED' ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",params,
                (r,n)->new Work(r.getObject("id",UUID.class),JSON.parseObject(r.getString("payload"),Request.class),r.getString("scope_digest")));
            if(rows.isEmpty()) return null;Work found=rows.get(0);params.addValue("id",found.id());
            store.jdbc.update("UPDATE diary_coaching.period_job SET state='RUNNING',lease_token=:token,updated_at=now() WHERE owner_id=:owner AND id=:id",params);return found;
        });
        if(work==null) return;boolean sent=false;long started=System.nanoTime();Long inputTokens=null,outputTokens=null;
        String invalidOutput=null;List<DiaryDiagnostics.Source> diagnosticSources=List.of();
        try {
            if(!active.getAsBoolean()) throw new DiaryFailure(403,"账号不可用");
            Preview preview=periods.preview(owner,work.request().period());validatePreview(preview,work.digest());
            String system=CoachingEvidence.system();
            String context=canonical(CoachingPeriods.context(preview));
            int remaining=Math.min(32000,p.getMaxPromptCharacters())-points(context)-points(system)-32;
            DiaryQuoteCatalog catalog=DiaryQuoteCatalog.build(work.id(),preview.sources().stream().map(s->new DiaryQuoteCatalog.SourceText(s.sourceId(),s.kind(),s.revision(),s.text())).toList(),Math.min(5000,remaining));
            if(catalog.items().isEmpty())throw DiaryFailure.invalid("完整资料超出引文预算，请缩小范围");
            context=catalog.attach(context);
            if(points(context)+points(system)>Math.min(32000,p.getMaxPromptCharacters())) throw DiaryFailure.invalid("整体提示超过预算");
            execution.reserveGeneration(owner);
            store.tx(owner,true,()->{
                execution.lease(owner,lease);validatePreview(periods.build(owner,work.request().period()),work.digest());
                if(!active.getAsBoolean()) throw new DiaryFailure(403,"账号不可用");
                var params=CoachingStore.parameters(owner,work.id()).addValue("token",lease).addValue("sources",JSON.toJSONString(preview.sources()));
                if(store.jdbc.update("UPDATE diary_coaching.period_job SET sources=CAST(:sources AS jsonb),possibly_charged=true WHERE owner_id=:owner AND id=:id AND state='RUNNING' AND lease_token=:token",params)!=1) throw DiaryFailure.conflict();return null;
            });
            sent=true;var generation=cloud.generate(system,context,work.id());
            inputTokens=generation.inputTokens();outputTokens=generation.outputTokens();
            invalidOutput=generation.content();
            diagnosticSources=preview.sources().stream().map(s->new DiaryDiagnostics.Source(s.sourceId(),s.kind(),s.entityId(),s.revision(),s.text())).toList();
            execution.usage(owner,generation.inputTokens(),generation.outputTokens());
            String hydrated=catalog.hydrate(generation.content());
            DiaryValidation.check(hydrated,preview.sources().stream().map(s->new DiaryValidation.Evidence(s.sourceId(),s.text(),s.kind())).toList(),"PERIOD");
            final CoachingEvidence.Result result;
            try {result=CoachingEvidence.validate(hydrated,preview.sources());}
            catch(DiaryFailure invalid){throw DiaryValidation.unknown();}
            store.tx(owner,true,()->{
                execution.lease(owner,lease);validatePreview(periods.build(owner,work.request().period()),work.digest());
                if(!active.getAsBoolean()) throw new DiaryFailure(403,"账号不可用");
                var params=CoachingStore.parameters(owner,work.id()).addValue("token",lease).addValue("report",UUID.randomUUID())
                    .addValue("result",JSON.toJSONString(result)).addValue("scope",JSON.toJSONString(work.request().period())).addValue("digest",work.digest()).addValue("model",p.getGenerationModel());
                if(store.jdbc.update("UPDATE diary_coaching.period_job SET state='SUCCEEDED',sources=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id AND state='RUNNING' AND lease_token=:token",params)!=1) throw DiaryFailure.conflict();
                params.addValue("promptVersion",CoachingEvidence.PROMPT_VERSION);
                store.jdbc.update("INSERT INTO diary_coaching.period_report(owner_id,id,job_id,state,result,scope,scope_digest,model,prompt_version) VALUES(:owner,:report,:id,'SUCCEEDED',CAST(:result AS jsonb),CAST(:scope AS jsonb),:digest,:model,:promptVersion)",params);
                for(Source source:preview.sources()) {
                    params.addValue("source",source.sourceId()).addValue("kind",source.kind()).addValue("entity",source.entityId()).addValue("revision",source.revision()).addValue("field",source.field()).addValue("start",source.start()).addValue("end",source.end()).addValue("hash",source.sha256());
                    store.jdbc.update("INSERT INTO diary_coaching.period_source VALUES(:owner,:report,:source,:kind,:entity,:revision,:field,:start,:end,:hash)",params);
                }return null;
            });
            DiaryAiAudit.event(work.id(),"PERIOD","COMPLETE","OK",elapsed(started),inputTokens,outputTokens,sent);
        } catch(RuntimeException failure) {
            if(failure instanceof DiaryValidation.Issue issue) {
                DiaryAiAudit.event(work.id(),"PERIOD","VALIDATION",issue.reason().name(),elapsed(started),inputTokens,outputTokens,sent);
                try {ai.recordInvalid(owner,work.id(),lease,"PERIOD",issue,invalidOutput,diagnosticSources,CoachingEvidence.PROMPT_VERSION);return;}
                catch(RuntimeException unavailable){failure=new DiaryAiCloud.Failure("OUTPUT_EVIDENCE_INVALID",false);}
            }
            String state=failure instanceof DiaryAiCloud.Failure f && f.uncertain()?"UNCERTAIN":failure instanceof DiaryFailure d && d.status()==409?"STALE":"FAILED";
            boolean charged=sent;
            String code=failure instanceof DiaryAiCloud.Failure f?f.code():failure instanceof DiaryFailure d && d.status()==429?"DAILY_LIMIT":"TASK_REJECTED";
            DiaryAiAudit.event(work.id(),"PERIOD","FAILURE",code,elapsed(started),inputTokens,outputTokens,sent);
            try {
                if(failure instanceof DiaryAiCloud.Failure f && f.diagnostic()!=null)execution.usage(owner,f.diagnostic().inputTokens(),f.diagnostic().outputTokens());
            } finally {
                store.tx(owner,false,()->{store.jdbc.update("UPDATE diary_coaching.period_job SET state=:state,error_code=:code,possibly_charged=possibly_charged OR :charged,sources=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id AND state='RUNNING' AND lease_token=:token",
                    CoachingStore.parameters(owner,work.id()).addValue("token",lease).addValue("state",state).addValue("code",code).addValue("charged",charged));return null;});
            }
        }
    }
    private static long elapsed(long start){return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);}
    private void validatePreview(Preview preview,String digest) {
        if(!preview.snapshotDigest().equals(digest)) throw DiaryFailure.conflict();
        if(!preview.withinBudget()) throw DiaryFailure.invalid("完整资料超过预算，请缩小范围");
        if(preview.diaries().isEmpty() && preview.feedback().isEmpty()) throw DiaryFailure.invalid("没有日记或实际反馈，不能据此生成行为回顾");
    }
    public Report report(long owner,UUID id) {
        var value=store.tx(owner,true,()->{
            var rows=store.jdbc.query("SELECT * FROM diary_coaching.period_report WHERE owner_id=:owner AND id=:id",CoachingStore.parameters(owner,id),
                (r,n)->new Object[]{r.getString("state"),r.getString("result"),r.getString("scope"),r.getString("scope_digest"),r.getString("model"),r.getTimestamp("created_at").toInstant(),r.getObject("job_id",UUID.class)});
            if(rows.isEmpty()) throw new DiaryFailure(404,"报告不存在或不可访问");Object[] row=rows.get(0);
            if(!"SUCCEEDED".equals(row[0]) || row[1]==null || row[2]==null) return null;
            Preview preview=periods.build(owner,JSON.parseObject((String)row[2],CoachingModels.Period.class));
            if(!preview.snapshotDigest().equals(row[3])) {
                var params=CoachingStore.parameters(owner,id).addValue("job",row[6]);
                store.jdbc.update("UPDATE diary_coaching.period_report SET state='STALE',result=NULL WHERE owner_id=:owner AND id=:id",params);
                store.jdbc.update("UPDATE diary_coaching.period_job SET state='STALE',sources=NULL WHERE owner_id=:owner AND id=:job",params);return null;
            }
            List<Source> stored=store.jdbc.query("SELECT * FROM diary_coaching.period_source WHERE owner_id=:owner AND report_id=:id ORDER BY source_id",CoachingStore.parameters(owner,id),(r,n)->new Source(r.getString("source_id"),r.getString("kind"),r.getObject("entity_id",UUID.class),r.getLong("revision"),r.getString("field"),r.getInt("start_cp"),r.getInt("end_cp"),r.getString("text_hash"),""));
            if(stored.size()!=preview.sources().size()) throw DiaryFailure.conflict();
            for(Source source:stored) if(preview.sources().stream().noneMatch(s->s.sourceId().equals(source.sourceId()) && s.entityId().equals(source.entityId()) && s.kind().equals(source.kind()) && s.revision()==source.revision() && s.start()==source.start() && s.end()==source.end() && s.field().equals(source.field()) && s.sha256().equals(source.sha256()))) throw DiaryFailure.conflict();
            Window original=JSON.parseObject((String)row[2],CoachingModels.Period.class).normalize(((Instant)row[5]).atZone(ZONE).toLocalDate());
            return new Report(id,(String)row[1],original,preview.sources(),(String)row[4],(Instant)row[5]);
        });
        if(value==null) throw new DiaryFailure(409,"报告已过期，请重新预览");return value;
    }
    public Page<ReportSummary> list(long owner,int page) {
        if(page<1 || page>10000) throw DiaryFailure.invalid("页码无效");
        return store.tx(owner,true,()->{
            var params=new MapSqlParameterSource("owner",owner).addValue("offset",(page-1)*20);
            long total=reports(owner);
            var rows=store.jdbc.query("SELECT id,state,scope,model,created_at FROM diary_coaching.period_report WHERE owner_id=:owner ORDER BY created_at DESC,id LIMIT 20 OFFSET :offset",params,
                (r,n)->new ReportSummary(r.getObject("id",UUID.class),r.getString("state"),JSON.parseObject(r.getString("scope"),CoachingModels.Period.class).normalize(r.getTimestamp("created_at").toInstant().atZone(ZONE).toLocalDate()),r.getString("model"),r.getTimestamp("created_at").toInstant()));
            return new Page<>(rows,total,page,20);
        });
    }
    public void delete(long owner,UUID id) {
        store.tx(owner,false,()->{
            var params=CoachingStore.parameters(owner,id);
            store.jdbc.update("UPDATE diary_coaching.period_job SET state='CANCELED',payload=NULL,sources=NULL WHERE owner_id=:owner AND id IN(SELECT job_id FROM diary_coaching.period_report WHERE owner_id=:owner AND id=:id)",params);
            store.jdbc.update("DELETE FROM diary_coaching.period_report WHERE owner_id=:owner AND id=:id",params);return null;
        });
    }
}
