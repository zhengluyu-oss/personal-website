package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.kuailemao.diary.DiaryFailure;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Private resource, never log objects from this class. Caller must authenticate the diary owner. */
public final class DiaryDiagnostics {
    public record Source(String sourceId,String kind,UUID entityId,long revision,String text) {}
    public record View(String status,String reason,String fieldPath,String model,String promptVersion,Instant createdAt,Instant expiresAt,String raw,List<Source> sources) {}
    private final NamedParameterJdbcTemplate jdbc;private final DataSourceTransactionManager manager;
    public DiaryDiagnostics(NamedParameterJdbcTemplate jdbc,DataSourceTransactionManager manager){this.jdbc=jdbc;this.manager=manager;}
    private <T>T tx(long owner,Supplier<T> operation){var tx=new TransactionTemplate(manager);tx.setTimeout(8);try{return tx.execute(s->{jdbc.queryForObject("SELECT set_config('diary.owner_id',:owner,true)",Map.of("owner",Long.toString(owner)),String.class);return operation.get();});}catch(DiaryFailure e){throw e;}catch(RuntimeException e){throw DiaryFailure.unavailable();}}
    public boolean available(){return jdbc.queryForObject("SELECT to_regclass('diary_diagnostics.failure') IS NOT NULL",Map.of(),Boolean.class);}
    /** Called only within the job's fenced failure transaction. */
    public void save(long owner,UUID id,String kind,DiaryValidation.Issue issue,String model,String prompt,String raw,List<Source> sources){
        if(!available())return;
        String encoded=JSON.toJSONString(sources);
        long count=jdbc.queryForObject("SELECT count(*) FROM diary_diagnostics.failure WHERE owner_id=:owner AND retention='AVAILABLE'",Map.of("owner",owner),Long.class);
        boolean keep=raw!=null&&raw.getBytes(StandardCharsets.UTF_8).length<=131072&&encoded.getBytes(StandardCharsets.UTF_8).length<=524288&&count<100;
        var p=new HashMap<String,Object>();p.put("owner",owner);p.put("id",id);p.put("kind",kind);p.put("reason",issue.reason().name());p.put("path",issue.path());p.put("model",model);p.put("prompt",prompt);p.put("raw",keep?raw:null);p.put("sources",keep?encoded:null);p.put("status",keep?"AVAILABLE":"NOT_RETAINED");
        jdbc.update("INSERT INTO diary_diagnostics.failure(owner_id,job_id,kind,reason,field_path,model,prompt_version,raw,sources,retention) VALUES(:owner,:id,:kind,:reason,:path,:model,:prompt,:raw,:sources,:status) ON CONFLICT DO NOTHING",p);
    }
    private boolean current(long owner,List<Source> sources){
        for(var source:sources){String table=switch(source.kind()){case "D"->"diary.entry";case "G"->"diary_coaching.goal";case "A"->"diary_coaching.action";case "F"->"diary_coaching.feedback";default->null;};if(table==null)return false;
            Boolean ok=jdbc.queryForObject("SELECT EXISTS(SELECT FROM "+table+" WHERE owner_id=:owner AND id=:id AND revision=:revision AND deleted_at IS NULL)",Map.of("owner",owner,"id",source.entityId(),"revision",source.revision()),Boolean.class);if(!Boolean.TRUE.equals(ok))return false;
        }return true;
    }
    public View read(long owner,UUID id,String kind,boolean body){return tx(owner,()->{
        if(!available())return missing();
        var p=Map.of("owner",owner,"id",id,"kind",kind);
        var rows=jdbc.query("SELECT * FROM diary_diagnostics.failure WHERE owner_id=:owner AND job_id=:id AND kind=:kind FOR UPDATE",p,(r,n)->new View(r.getString("retention"),r.getString("reason"),r.getString("field_path"),r.getString("model"),r.getString("prompt_version"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("expires_at").toInstant(),r.getString("raw"),r.getString("sources")==null?List.of():JSON.parseArray(r.getString("sources"),Source.class)));
        if(rows.isEmpty())return missing();var v=rows.get(0);String status=v.status();
        if(status.equals("AVAILABLE")){if(!v.expiresAt().isAfter(Instant.now()))status="EXPIRED";else if(!current(owner,v.sources()))status="STALE";if(!status.equals("AVAILABLE")){var values=new HashMap<String,Object>(p);values.put("status",status);jdbc.update("UPDATE diary_diagnostics.failure SET raw=NULL,sources=NULL,retention=:status WHERE owner_id=:owner AND job_id=:id AND kind=:kind",values);}}
        return new View(status,v.reason(),v.fieldPath(),v.model(),v.promptVersion(),v.createdAt(),v.expiresAt(),body&&status.equals("AVAILABLE")?v.raw():null,body&&status.equals("AVAILABLE")?v.sources():List.of());
    });}
    private static View missing(){return new View("NOT_RECORDED",null,null,null,null,null,null,null,List.of());}
    public void delete(long owner,UUID id,String kind){tx(owner,()->{if(available())jdbc.update("UPDATE diary_diagnostics.failure SET raw=NULL,sources=NULL,retention='DELETED' WHERE owner_id=:owner AND job_id=:id AND kind=:kind",Map.of("owner",owner,"id",id,"kind",kind));return null;});}
    public void cleanup(long owner){
        List<Map<String,Object>> rows=tx(owner,()->available()?jdbc.queryForList("SELECT job_id,kind FROM diary_diagnostics.failure WHERE owner_id=:owner AND retention='AVAILABLE' ORDER BY expires_at LIMIT 100",Map.of("owner",owner)):List.of());
        for(var r:rows)read(owner,(UUID)r.get("job_id"),(String)r.get("kind"),false);
    }
}
