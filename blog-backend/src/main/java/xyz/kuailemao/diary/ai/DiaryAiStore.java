package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.kuailemao.diary.DiaryFailure;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** 所有数据库访问绑定本人上下文；网络请求不在本类或事务中执行。 */
public final class DiaryAiStore {
    final NamedParameterJdbcTemplate jdbc;
    private final DataSourceTransactionManager manager;
    final DiaryAiProperties properties;
    private final Supplier<DiaryAiExtension> extension;
    public DiaryAiStore(NamedParameterJdbcTemplate jdbc,DataSourceTransactionManager manager,DiaryAiProperties properties) {
        this(jdbc,manager,properties,()->null);
    }
    public DiaryAiStore(NamedParameterJdbcTemplate jdbc,DataSourceTransactionManager manager,DiaryAiProperties properties,Supplier<DiaryAiExtension> extension) {
        this.jdbc=jdbc;this.manager=manager;this.properties=properties;this.extension=extension;
    }
    public void recordInvalid(long owner,UUID id,UUID token,String kind,DiaryValidation.Issue issue,String raw,List<DiaryDiagnostics.Source> sources,String prompt) {
        tx(owner,()->{
            lease(owner,token);
            String table="PERIOD".equals(kind)?"diary_coaching.period_job":"diary_ai.job";
            var p=params(owner);p.put("id",id);p.put("token",token);
            if(jdbc.update("UPDATE "+table+" SET state='FAILED',error_code='OUTPUT_EVIDENCE_INVALID',possibly_charged=true,sources=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id AND state='RUNNING' AND lease_token=:token",p)==1)
                new DiaryDiagnostics(jdbc,manager).save(owner,id,kind,issue,properties.getGenerationModel(),prompt,raw,sources);
            return null;
        });
        org.slf4j.LoggerFactory.getLogger(DiaryAiStore.class).info("diary_ai_validation task={} reason={} field={}",id,issue.reason().name(),issue.path());
    }
    <T>T tx(long owner,Supplier<T> operation) {
        TransactionTemplate tx=new TransactionTemplate(manager);tx.setTimeout(8);
        try { return tx.execute(status->{jdbc.queryForObject("SELECT set_config('diary.owner_id',:owner,true)",Map.of("owner",Long.toString(owner)),String.class);return operation.get();}); }
        catch(DiaryFailure e){throw e;}catch(RuntimeException failure){throw DiaryFailure.unavailable();}
    }
    <T>T snapshot(long owner,Supplier<T> operation) {
        TransactionTemplate tx=new TransactionTemplate(manager);tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);tx.setTimeout(8);
        try {return tx.execute(status->tx(owner,operation));}catch(DiaryFailure e){throw e;}catch(RuntimeException failure){throw DiaryFailure.unavailable();}
    }
    static Map<String,Object> params(long owner) {var p=new HashMap<String,Object>();p.put("owner",owner);return p;}
    record Filter(String where,Map<String,Object> parameters) {}
    Filter filter(long owner,Scope scope) {
        var p=params(owner);String where=" e.owner_id=:owner AND e.deleted_at IS NULL";
        if(scope!=null) {
            scope.validate(false);
            if(scope.entryIds()!=null){p.put("ids",scope.entryIds());where+=" AND e.id IN(:ids)";}
            else if(scope.from()!=null) {
                if("covered".equals(scope.timeAxis())) {p.put("from",scope.from());p.put("to",scope.to());where+=" AND e.covered_from<=:to AND e.covered_to>=:from";}
                else {p.put("from",Timestamp.from(scope.from().atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));p.put("to",Timestamp.from(scope.to().plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant()));where+=" AND e.created_at>=:from AND e.created_at<:to";}
            }
        }
        return new Filter(where,p);
    }
    static Entry mapEntry(ResultSet r,int row) throws SQLException {
        return new Entry(r.getObject("id",UUID.class),r.getString("title"),r.getString("body"),r.getObject("covered_from",LocalDate.class),
                r.getObject("covered_to",LocalDate.class),r.getLong("revision"),r.getTimestamp("created_at").toInstant());
    }
    List<Entry> select(long owner,Scope scope,int limit) {
        var f=filter(owner,scope);f.parameters.put("limit",limit);
        return jdbc.query("SELECT e.* FROM diary.entry e WHERE "+f.where+" ORDER BY e.id LIMIT :limit",f.parameters,DiaryAiStore::mapEntry);
    }
    Entry entry(long owner,UUID id) {
        var values=select(owner,new Scope(List.of(id),null,null,null),1);
        if(values.isEmpty())throw new DiaryFailure(404,"日记不存在或不可访问");return values.get(0);
    }
    String scopeDigest(long owner,Scope scope) {
        var f=filter(owner,scope);
        var values=jdbc.query("SELECT e.id,e.revision,e.covered_from,e.covered_to FROM diary.entry e WHERE "+f.where+" ORDER BY e.id LIMIT 10001",f.parameters,
                (r,n)->r.getString(1)+":"+r.getLong(2)+":"+r.getString(3)+":"+r.getString(4));
        if(values.size()>10000)throw DiaryFailure.invalid("记录范围过大，请缩小范围");
        return DiaryAiText.hash(String.join("\n",values));
    }
    public boolean available(long owner) {
        try{return tx(owner,()->Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT FROM diary_ai.schema_version WHERE version=1) AND (SELECT count(*)=9 AND bool_and(c.relrowsecurity AND c.relforcerowsecurity AND r.rolname<>'diary_runtime') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='diary_ai' AND c.relkind='r' AND c.relname<>'schema_version') AND NOT EXISTS(SELECT FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls))",Map.of(),Boolean.class)));}
        catch(DiaryFailure failure){return false;}
    }
    public String ensureProfile(long owner) {
        String id=DiaryAiText.profile(properties);
        tx(owner,()->{var p=params(owner);p.put("id",id);p.put("provider",properties.getProvider());p.put("model",properties.getEmbeddingModel());p.put("dimensions",properties.getDimensions());p.put("chunker",DiaryAiText.VERSION);
            jdbc.update("INSERT INTO diary_ai.embedding_profile(owner_id,id,provider,model,dimensions,chunker) VALUES(:owner,:id,:provider,:model,:dimensions,:chunker) ON CONFLICT DO NOTHING",p);
            jdbc.update("INSERT INTO diary_ai.owner_settings(owner_id,profile_id) VALUES(:owner,:id) ON CONFLICT(owner_id) DO UPDATE SET syncing=CASE WHEN diary_ai.owner_settings.profile_id=excluded.profile_id THEN diary_ai.owner_settings.syncing ELSE false END,profile_id=excluded.profile_id",p);return null;});return id;
    }
    public Preview preview(long owner,Scope scope) {
        if(scope==null)throw DiaryFailure.invalid("请选择复盘范围");scope.validate(true);
        return snapshot(owner,()->{
            List<Entry> entries=select(owner,scope,41);
            if(scope.entryIds()!=null && entries.size()!=scope.entryIds().size())throw new DiaryFailure(404,"部分日记不存在或不可访问");
            if(entries.isEmpty())throw DiaryFailure.invalid("范围内没有可复盘的日记");
            int chars=entries.stream().mapToInt(e->points(e.title())+points(e.body())+200).sum();
            if(entries.size()>40 || chars>properties.getMaxReviewCharacters())throw DiaryFailure.invalid("复盘最多40篇且总输入不超过预算，请缩小范围");
            return new Preview(entries.size(),chars,scopeDigest(owner,scope),entries.stream().map(e->new EntrySummary(e.id(),e.title(),e.coveredFrom(),e.coveredTo(),e.revision())).toList(),
                    "内容范围重叠不代表逐日都有记录；按内容日期筛选不包含未填日期的篇目。");
        });
    }
    public Job enqueue(long owner,Kind kind,Request request) {
        request.validate(kind);String requestHash=DiaryAiText.hash(kind+"\n"+JSON.toJSONString(request));
        return tx(owner,()->{
            var p=params(owner);p.put("request",request.requestId());
            jdbc.queryForObject("SELECT owner_id FROM diary_ai.owner_settings WHERE owner_id=:owner FOR UPDATE",p,Long.class);
            var previous=jdbc.query("SELECT * FROM diary_ai.job WHERE owner_id=:owner AND request_id=:request",p,(r,n)->r.getString("request_hash"));
            if(!previous.isEmpty()) {if(!requestHash.equals(previous.get(0)))throw new DiaryFailure(409,"请求标识已用于不同内容");return jobByRequest(owner,request.requestId());}
            long queued=jdbc.queryForObject("SELECT count(*) FROM diary_ai.job WHERE owner_id=:owner AND state IN('QUEUED','RUNNING')",p,Long.class);
            long reports=jdbc.queryForObject("SELECT count(*) FROM diary_ai.report WHERE owner_id=:owner",p,Long.class);
            DiaryAiExtension extra=extension.get();
            if(extra!=null) { queued+=extra.queued(owner);reports+=extra.reports(owner); }
            if(queued>=properties.getMaxQueue() || reports+queued>=properties.getMaxReports())throw new DiaryFailure(429,"任务或报告已达到上限，请稍后处理或清理报告");
            String digest=scopeDigest(owner,request.scope());
            if(kind==Kind.REVIEW && !preview(owner,request.scope()).snapshotDigest().equals(request.snapshotDigest()))throw new DiaryFailure(409,"日记范围已变化，请重新预览");
            UUID id=UUID.randomUUID();p.put("id",id);p.put("hash",requestHash);p.put("kind",kind.name());p.put("payload",JSON.toJSONString(request));p.put("digest",digest);
            jdbc.update("INSERT INTO diary_ai.job(owner_id,id,request_id,request_hash,kind,state,payload,scope_digest) VALUES(:owner,:id,:request,:hash,:kind,'QUEUED',CAST(:payload AS jsonb),:digest)",p);
            return job(owner,id);
        });
    }
    public Job indexAction(long owner,IndexRequest request,boolean syncing) {
        if(request==null || request.requestId()==null)throw DiaryFailure.invalid("缺少请求标识");
        String profile=ensureProfile(owner),hash=DiaryAiText.hash(syncing+":"+request.retryFailed()+":"+profile);
        return tx(owner,()->{
            var p=params(owner);p.put("request",request.requestId());
            jdbc.queryForObject("SELECT owner_id FROM diary_ai.owner_settings WHERE owner_id=:owner FOR UPDATE",p,Long.class);
            var old=jdbc.query("SELECT request_hash FROM diary_ai.job WHERE owner_id=:owner AND request_id=:request",p,(r,n)->r.getString(1));
            if(!old.isEmpty()){if(!hash.equals(old.get(0)))throw new DiaryFailure(409,"请求标识已用于不同内容");return jobByRequest(owner,request.requestId());}
            p.put("syncing",syncing);p.put("profile",profile);p.put("id",UUID.randomUUID());p.put("hash",hash);p.put("kind",syncing?"INDEX_START":"INDEX_PAUSE");
            jdbc.update("UPDATE diary_ai.owner_settings SET syncing=:syncing WHERE owner_id=:owner",p);
            if(syncing && request.retryFailed())jdbc.update("UPDATE diary_ai.entry_index SET status='PENDING',error_code=NULL WHERE owner_id=:owner AND profile_id=:profile AND status IN('FAILED','UNCERTAIN','QUOTA')",p);
            jdbc.update("INSERT INTO diary_ai.job(owner_id,id,request_id,request_hash,kind,state,payload) VALUES(:owner,:id,:request,:hash,:kind,'SUCCEEDED','{}')",p);
            return job(owner,(UUID)p.get("id"));
        });
    }
    Job jobByRequest(long owner,UUID request) {
        UUID id=jdbc.queryForObject("SELECT id FROM diary_ai.job WHERE owner_id=:owner AND request_id=:request",Map.of("owner",owner,"request",request),UUID.class);return job(owner,id);
    }
    public Job job(long owner,UUID id) {
        return tx(owner,()->{
            var p=params(owner);p.put("id",id);
            var jobs=jdbc.query("SELECT j.*,(SELECT r.id FROM diary_ai.report r WHERE r.owner_id=j.owner_id AND r.job_id=j.id) report_id FROM diary_ai.job j WHERE j.owner_id=:owner AND j.id=:id",p,(r,n)->new Job(r.getObject("id",UUID.class),Kind.valueOf(r.getString("kind")),State.valueOf(r.getString("state")),message(r.getString("state"),r.getString("error_code")),r.getObject("report_id",UUID.class),r.getBoolean("possibly_charged"),r.getTimestamp("created_at").toInstant()));
            if(jobs.isEmpty())throw new DiaryFailure(404,"任务不存在或不可访问");return jobs.get(0);
        });
    }
    static String message(String state,String code) {
        return DiaryAiFeedback.message(state,code);
    }
    public Job cancel(long owner,UUID id) {
        tx(owner,()->{jdbc.update("UPDATE diary_ai.job SET state='CANCELED',payload='{}',sources=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id AND state IN('QUEUED','RUNNING')",Map.of("owner",owner,"id",id));return null;});return job(owner,id);
    }
    boolean acquire(long owner,UUID token) {
        return tx(owner,()->{var p=params(owner);p.put("token",token);
            jdbc.update("INSERT INTO diary_ai.worker_slot(slot,owner_id) VALUES(1,:owner) ON CONFLICT DO NOTHING",p);
            return jdbc.update("UPDATE diary_ai.worker_slot SET token=:token,expires_at=now()+interval '90 seconds' WHERE slot=1 AND owner_id=:owner AND expires_at<now()",p)==1;
        });
    }
    void lease(long owner,UUID token) {
        if(jdbc.update("UPDATE diary_ai.worker_slot SET expires_at=now()+interval '90 seconds' WHERE slot=1 AND owner_id=:owner AND token=:token AND expires_at>now()",Map.of("owner",owner,"token",token))!=1)throw new DiaryFailure(409,"任务租约已失效");
    }
    void release(long owner,UUID token) {tx(owner,()->{jdbc.update("UPDATE diary_ai.worker_slot SET token=NULL,expires_at='-infinity' WHERE owner_id=:owner AND token=:token",Map.of("owner",owner,"token",token));return null;});}
    record Work(UUID id,Kind kind,Request request,String digest) {}
    java.time.Instant oldestQueued(long owner) {
        Timestamp value=tx(owner,()->jdbc.queryForObject("SELECT min(created_at) FROM diary_ai.job WHERE owner_id=:owner AND state='QUEUED'",Map.of("owner",owner),Timestamp.class));
        return value==null?null:value.toInstant();
    }
    Work claim(long owner,UUID token) {
        return tx(owner,()->{lease(owner,token);var p=params(owner);p.put("token",token);
            jdbc.update("UPDATE diary_ai.job SET state='UNCERTAIN',possibly_charged=true,error_code='RESTART_UNKNOWN',sources=NULL,updated_at=now() WHERE owner_id=:owner AND state='RUNNING'",p);
            jdbc.update("UPDATE diary_ai.entry_index SET status='UNCERTAIN',error_code='RESTART_UNKNOWN' WHERE owner_id=:owner AND status='RUNNING'",p);
            var jobs=jdbc.query("SELECT * FROM diary_ai.job WHERE owner_id=:owner AND state='QUEUED' ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",p,(r,n)->new Work(r.getObject("id",UUID.class),Kind.valueOf(r.getString("kind")),JSON.parseObject(r.getString("payload"),Request.class),r.getString("scope_digest")));
            if(jobs.isEmpty())return null;Work w=jobs.get(0);p.put("id",w.id);
            jdbc.update("UPDATE diary_ai.job SET state='RUNNING',lease_token=:token,updated_at=now() WHERE owner_id=:owner AND id=:id",p);return w;
        });
    }
    void reserve(long owner,int generations,int blocks) {
        tx(owner,()->{var p=params(owner);p.put("day",LocalDate.now(ZoneId.of("Asia/Shanghai")));p.put("g",generations);p.put("b",blocks);p.put("maxg",properties.getDailyGenerations());p.put("maxb",properties.getDailyEmbeddingBlocks());
            jdbc.update("INSERT INTO diary_ai.usage_budget(owner_id,day) VALUES(:owner,:day) ON CONFLICT DO NOTHING",p);
            if(jdbc.update("UPDATE diary_ai.usage_budget SET generations=generations+:g,embedding_blocks=embedding_blocks+:b WHERE owner_id=:owner AND day=:day AND generations+:g<=:maxg AND embedding_blocks+:b<=:maxb",p)!=1)throw new DiaryFailure(429,"今日 AI 调用额度已用完，请稍后再试");return null;
        });
    }
    void usage(long owner,Long input,Long output) {
        if(input==null && output==null)return;
        tx(owner,()->{var p=params(owner);p.put("day",LocalDate.now(ZoneId.of("Asia/Shanghai")));p.put("input",input);p.put("output",output);
            jdbc.update("UPDATE diary_ai.usage_budget SET input_tokens=CASE WHEN CAST(:input AS bigint) IS NULL THEN input_tokens ELSE coalesce(input_tokens,0)+:input END,output_tokens=CASE WHEN CAST(:output AS bigint) IS NULL THEN output_tokens ELSE coalesce(output_tokens,0)+:output END WHERE owner_id=:owner AND day=:day",p);return null;
        });
    }
    void preparing(long owner,Work work,UUID token,List<Source> sources,boolean partial) {
        snapshot(owner,()->{lease(owner,token);
            if(!work.digest.equals(scopeDigest(owner,work.request.scope())))throw new DiaryFailure(409,"发送前来源范围已变化");
            for(Source s:sources){Entry e=entry(owner,s.entryId());if(e.revision()!=s.revision() || s.end()>points(e.body()) || !DiaryAiText.hash(slice(e.body(),s.start(),s.end())).equals(s.sha256()))throw new DiaryFailure(409,"发送前来源版本已变化");}
            var p=params(owner);p.put("id",work.id);p.put("token",token);p.put("sources",JSON.toJSONString(sources));p.put("partial",partial);
            if(jdbc.update("UPDATE diary_ai.job SET sources=CAST(:sources AS jsonb),partial=:partial,possibly_charged=true WHERE owner_id=:owner AND id=:id AND lease_token=:token AND state='RUNNING'",p)!=1)throw new DiaryFailure(409,"任务已取消或失效");return null;
        });
    }
    void cloudIntent(long owner,Work work,UUID token) {
        tx(owner,()->{lease(owner,token);var p=params(owner);p.put("id",work.id());p.put("token",token);
            if(jdbc.update("UPDATE diary_ai.job SET possibly_charged=true WHERE owner_id=:owner AND id=:id AND lease_token=:token AND state='RUNNING'",p)!=1)throw new DiaryFailure(409,"任务已取消或失效");return null;
        });
    }
    boolean current(long owner,Work work) {return snapshot(owner,()->work.digest.equals(scopeDigest(owner,work.request.scope())));}
    void fail(long owner,Work work,UUID token,State state,String code,boolean charged) {
        tx(owner,()->{var p=params(owner);p.put("id",work.id);p.put("token",token);p.put("state",state.name());p.put("code",code);p.put("charged",charged);
            jdbc.update("UPDATE diary_ai.job SET state=:state,error_code=:code,possibly_charged=possibly_charged OR :charged,sources=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id AND state='RUNNING' AND lease_token=:token",p);return null;
        });
    }
    void complete(long owner,Work work,UUID token,List<Source> sources,Result result,boolean partial) {
        snapshot(owner,()->{
            lease(owner,token);
            if(!work.digest.equals(scopeDigest(owner,work.request.scope())))throw new DiaryFailure(409,"来源范围已变化");
            for(Source s:sources){Entry e=entry(owner,s.entryId());if(e.revision()!=s.revision() || s.end()>points(e.body()) || !DiaryAiText.hash(slice(e.body(),s.start(),s.end())).equals(s.sha256()))throw new DiaryFailure(409,"来源版本已变化");}
            UUID report=UUID.randomUUID();var p=params(owner);p.put("job",work.id);p.put("token",token);
            if(jdbc.update("UPDATE diary_ai.job SET state='SUCCEEDED',sources=NULL,updated_at=now() WHERE owner_id=:owner AND id=:job AND state='RUNNING' AND lease_token=:token",p)!=1)throw new DiaryFailure(409,"任务已取消或失效");
            p.put("id",report);p.put("kind",work.kind.name());p.put("result",JSON.toJSONString(result));p.put("scope",JSON.toJSONString(work.request.scope()));p.put("model",properties.getGenerationModel());p.put("prompt",DiaryAiEvidence.PROMPT_VERSION);p.put("partial",partial);
            jdbc.update("INSERT INTO diary_ai.report(owner_id,id,job_id,kind,state,result,scope_description,model,prompt_version,partial) VALUES(:owner,:id,:job,:kind,'SUCCEEDED',CAST(:result AS jsonb),:scope,:model,:prompt,:partial)",p);
            for(Source s:sources){p.put("source",s.sourceId());p.put("entry",s.entryId());p.put("revision",s.revision());p.put("start",s.start());p.put("end",s.end());p.put("hash",s.sha256());
                jdbc.update("INSERT INTO diary_ai.report_source(owner_id,report_id,source_id,entry_id,source_revision,start_cp,end_cp,text_hash) VALUES(:owner,:id,:source,:entry,:revision,:start,:end,:hash)",p);}
            return null;
        });
    }
    void invalidate(long owner) {
        var p=params(owner);
        jdbc.update("UPDATE diary_ai.report r SET state='STALE',result=NULL WHERE r.owner_id=:owner AND r.state='SUCCEEDED' AND EXISTS(SELECT FROM diary_ai.report_source s LEFT JOIN diary.entry e ON e.id=s.entry_id AND e.owner_id=s.owner_id WHERE s.owner_id=r.owner_id AND s.report_id=r.id AND (e.id IS NULL OR e.deleted_at IS NOT NULL OR e.revision<>s.source_revision))",p);
        jdbc.update("UPDATE diary_ai.job j SET state='STALE',sources=NULL,payload='{}' WHERE j.owner_id=:owner AND j.state='SUCCEEDED' AND EXISTS(SELECT FROM diary_ai.report r WHERE r.owner_id=j.owner_id AND r.job_id=j.id AND r.state='STALE')",p);
    }
    public Page<ReportSummary> reports(long owner,int page) {
        if(page<1 || page>100000)throw DiaryFailure.invalid("页码无效");
        return tx(owner,()->{invalidate(owner);var p=params(owner);p.put("offset",(page-1)*20);
            long total=jdbc.queryForObject("SELECT count(*) FROM diary_ai.report WHERE owner_id=:owner",p,Long.class);
            var rows=jdbc.query("SELECT r.*,(SELECT count(DISTINCT entry_id) FROM diary_ai.report_source s WHERE s.owner_id=r.owner_id AND s.report_id=r.id) entries FROM diary_ai.report r WHERE r.owner_id=:owner ORDER BY r.created_at DESC,r.id DESC LIMIT 20 OFFSET :offset",p,(r,n)->new ReportSummary(r.getObject("id",UUID.class),Kind.valueOf(r.getString("kind")),State.valueOf(r.getString("state")),r.getTimestamp("created_at").toInstant(),r.getInt("entries"),r.getString("model")));
            return new Page<>(rows,total,page,20);
        });
    }
    public Report report(long owner,UUID id) {
        Report value=snapshot(owner,()->{invalidate(owner);var p=params(owner);p.put("id",id);
            var rows=jdbc.query("SELECT * FROM diary_ai.report WHERE owner_id=:owner AND id=:id",p,(r,n)->Map.of("state",r.getString("state"),"kind",r.getString("kind"),"result",Objects.toString(r.getString("result"),""),"scope",r.getString("scope_description"),"model",r.getString("model"),"created",r.getTimestamp("created_at").toInstant(),"partial",r.getBoolean("partial")));
            if(rows.isEmpty())throw new DiaryFailure(404,"报告不存在或不可访问");var row=rows.get(0);if(!"SUCCEEDED".equals(row.get("state")))return null;
            List<Source> sources=jdbc.query("SELECT s.*,e.title,e.body,e.covered_from,e.covered_to,e.created_at FROM diary_ai.report_source s JOIN diary.entry e ON e.id=s.entry_id AND e.owner_id=s.owner_id AND e.revision=s.source_revision AND e.deleted_at IS NULL WHERE s.owner_id=:owner AND s.report_id=:id ORDER BY source_id",p,(r,n)->{
                String text=slice(r.getString("body"),r.getInt("start_cp"),r.getInt("end_cp"));
                if(!DiaryAiText.hash(text).equals(r.getString("text_hash")))throw new DiaryFailure(409,"来源已变化");
                return new Source(r.getString("source_id"),r.getObject("entry_id",UUID.class),r.getLong("source_revision"),r.getString("title"),r.getObject("covered_from",LocalDate.class),r.getObject("covered_to",LocalDate.class),r.getTimestamp("created_at").toInstant(),r.getInt("start_cp"),r.getInt("end_cp"),text,r.getString("text_hash"));
            });
            return new Report(id,Kind.valueOf((String)row.get("kind")),(String)row.get("result"),sources,(String)row.get("scope"),(String)row.get("model"),(Instant)row.get("created"),(boolean)row.get("partial"));
        });
        if(value==null)throw new DiaryFailure(409,"来源已变化，请重新生成报告");return value;
    }
    public void deleteReport(long owner,UUID id) {
        tx(owner,()->{var p=params(owner);p.put("id",id);
            jdbc.update("UPDATE diary_ai.job SET state='CANCELED',sources=NULL,payload='{}' WHERE owner_id=:owner AND id IN(SELECT job_id FROM diary_ai.report WHERE owner_id=:owner AND id=:id)",p);
            jdbc.update("DELETE FROM diary_ai.report WHERE owner_id=:owner AND id=:id",p);return null;});
    }
}
