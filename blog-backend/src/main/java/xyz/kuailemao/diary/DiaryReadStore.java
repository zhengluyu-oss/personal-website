package xyz.kuailemao.diary;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.kuailemao.diary.ai.DiaryAiText;
import xyz.kuailemao.diary.coaching.CoachingModels;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;

/** Read-only PG transactions: no profile creation, job polling, invalidation writes, or cloud client. */
public final class DiaryReadStore {
    private final NamedParameterJdbcTemplate jdbc;
    private final DataSourceTransactionManager manager;
    public DiaryReadStore(NamedParameterJdbcTemplate jdbc,DataSourceTransactionManager manager) { this.jdbc=jdbc;this.manager=manager; }
    private <T>T tx(long owner,Supplier<T> body) {
        var tx=new TransactionTemplate(manager);tx.setReadOnly(true);tx.setTimeout(8);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try { return tx.execute(s->{jdbc.queryForObject("SELECT set_config('diary.owner_id',:owner,true)",Map.of("owner",Long.toString(owner)),String.class);return body.get();}); }
        catch(DiaryFailure failure) {throw failure;} catch(RuntimeException failure) {throw DiaryFailure.unavailable();}
    }
    public record Summary(UUID id,String kind,String model,Instant createdAt) {}
    public record Source(String sourceId,UUID entryId,String title,String text) {}
    public record Report(UUID id,String kind,JSONObject result,List<Source> sources,String model,Instant createdAt) {}
    public record CoachingRow(UUID id,UUID parentId,JSONObject content,Instant updatedAt) {}
    private record Tables(String reports,String sources,String kind) {}
    private Tables tables(String type) {
        return switch(type) {
            case "ai" -> new Tables("diary_ai.report","diary_ai.report_source","r.kind");
            case "period" -> new Tables("diary_coaching.period_report","diary_coaching.period_source","'PERIOD'");
            default -> throw DiaryFailure.invalid("报告类型无效");
        };
    }
    private boolean installed(String table) {return jdbc.queryForObject("SELECT to_regclass(:table) IS NOT NULL",Map.of("table",table),Boolean.class);}
    // Every source must still refer to a current, undeleted revision. No historical text is returned.
    private String current(String type) {
        if(type.equals("ai")) return "NOT EXISTS (SELECT FROM diary_ai.report_source s LEFT JOIN diary.entry e ON e.owner_id=s.owner_id AND e.id=s.entry_id WHERE s.owner_id=r.owner_id AND s.report_id=r.id AND (e.id IS NULL OR e.deleted_at IS NOT NULL OR e.revision<>s.source_revision))";
        return "NOT EXISTS (SELECT FROM diary_coaching.period_source s "
            +"LEFT JOIN diary.entry d ON s.kind='D' AND d.owner_id=s.owner_id AND d.id=s.entity_id "
            +"LEFT JOIN diary_coaching.goal g ON s.kind='G' AND g.owner_id=s.owner_id AND g.id=s.entity_id "
            +"LEFT JOIN diary_coaching.action a ON s.kind='A' AND a.owner_id=s.owner_id AND a.id=s.entity_id "
            +"LEFT JOIN diary_coaching.feedback f ON s.kind='F' AND f.owner_id=s.owner_id AND f.id=s.entity_id "
            +"WHERE s.owner_id=r.owner_id AND s.report_id=r.id AND ("
            +"(s.kind='D' AND (d.id IS NULL OR d.deleted_at IS NOT NULL OR d.revision<>s.revision)) OR "
            +"(s.kind='G' AND (g.id IS NULL OR g.deleted_at IS NOT NULL OR g.revision<>s.revision)) OR "
            +"(s.kind='A' AND (a.id IS NULL OR a.deleted_at IS NOT NULL OR a.revision<>s.revision)) OR "
            +"(s.kind='F' AND (f.id IS NULL OR f.deleted_at IS NOT NULL OR f.revision<>s.revision))))";
    }
    public DiaryModels.Page<Summary> reports(long owner,String type,int page) {
        if(page<1 || page>10000) throw DiaryFailure.invalid("页码无效");
        Tables t=tables(type);
        return tx(owner,()->{
            if(!installed(t.reports())) return new DiaryModels.Page<>(List.of(),0,page,20);
            String where=" WHERE r.owner_id=:owner AND r.state='SUCCEEDED' AND r.result IS NOT NULL AND "+current(type);
            var params=Map.of("owner",owner,"offset",(page-1)*20);
            long count=jdbc.queryForObject("SELECT count(*) FROM "+t.reports()+" r"+where,params,Long.class);
            var rows=jdbc.query("SELECT r.id,"+t.kind()+" AS kind,r.model,r.created_at FROM "+t.reports()+" r"+where+" ORDER BY r.created_at DESC,r.id LIMIT 20 OFFSET :offset",params,
                (r,n)->new Summary(r.getObject("id",UUID.class),r.getString("kind"),r.getString("model"),r.getTimestamp("created_at").toInstant()));
            return new DiaryModels.Page<>(rows,count,page,20);
        });
    }
    public Report report(long owner,String type,UUID id) {
        Tables t=tables(type);
        return tx(owner,()->{
            if(!installed(t.reports())) throw new DiaryFailure(404,"报告不存在或不可查看");
            var params=Map.of("owner",owner,"id",id);
            var rows=jdbc.query("SELECT r.id,"+t.kind()+" AS kind,r.result,r.model,r.created_at FROM "+t.reports()+" r WHERE r.owner_id=:owner AND r.id=:id AND r.state='SUCCEEDED' AND r.result IS NOT NULL AND "+current(type),params,
                (r,n)->new Report(id,r.getString("kind"),JSON.parseObject(r.getString("result")),List.of(),r.getString("model"),r.getTimestamp("created_at").toInstant()));
            if(rows.isEmpty()) throw new DiaryFailure(404,"报告不存在或来源已变化");
            List<Source> sources;
            if(type.equals("ai")) {
                sources=jdbc.query("SELECT s.source_id,s.start_cp,s.end_cp,s.text_hash,e.id,e.title,e.body FROM diary_ai.report_source s JOIN diary.entry e ON e.owner_id=s.owner_id AND e.id=s.entry_id WHERE s.owner_id=:owner AND s.report_id=:id ORDER BY s.source_id",params,
                    (r,n)->new Source(r.getString("source_id"),r.getObject("id",UUID.class),r.getString("title"),verified(r.getString("body"),r.getInt("start_cp"),r.getInt("end_cp"),r.getString("text_hash"))));
            } else {
                sources=jdbc.query("SELECT * FROM diary_coaching.period_source WHERE owner_id=:owner AND report_id=:id ORDER BY source_id",params,(r,n)->{
                    String kind=r.getString("kind"),table=switch(kind){case "D"->"diary.entry";case "G"->"diary_coaching.goal";case "A"->"diary_coaching.action";case "F"->"diary_coaching.feedback";default->throw DiaryFailure.unavailable();};
                    UUID entity=r.getObject("entity_id",UUID.class);
                    String text=jdbc.queryForObject("SELECT "+(kind.equals("D")?"body":"data::text")+" FROM "+table+" WHERE owner_id=:owner AND id=:id AND deleted_at IS NULL",Map.of("owner",owner,"id",entity),String.class);
                    if(!kind.equals("D")) text=CoachingModels.canonical(JSON.parseObject(text));
                    return new Source(r.getString("source_id"),kind.equals("D")?entity:null,switch(kind){case "D"->"日记原文";case "G"->"目标";case "A"->"行动计划";default->"实际反馈";},verified(text,r.getInt("start_cp"),r.getInt("end_cp"),r.getString("text_hash")));
                });
            }
            Report row=rows.get(0);return new Report(row.id(),row.kind(),row.result(),sources,row.model(),row.createdAt());
        });
    }
    static String verified(String text,int start,int end,String hash) {
        if(text==null || start<0 || end<start || end>text.codePointCount(0,text.length())) throw new DiaryFailure(404,"来源不可查看");
        String slice=xyz.kuailemao.diary.ai.DiaryAiModels.slice(text,start,end);
        if(!DiaryAiText.hash(slice).equals(hash)) throw new DiaryFailure(404,"来源已变化");return slice;
    }
    public DiaryModels.Page<CoachingRow> coaching(long owner,String type,int page) {
        if(page<1 || page>10000) throw DiaryFailure.invalid("页码无效");
        String table="diary_coaching."+CoachingModels.Kind.parse(type).table;
        return tx(owner,()->{
            if(!installed(table)) return new DiaryModels.Page<>(List.of(),0,page,20);
            var params=Map.of("owner",owner,"offset",(page-1)*20);
            long total=jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE owner_id=:owner AND deleted_at IS NULL",params,Long.class);
            var rows=jdbc.query("SELECT id,parent_id,data,updated_at FROM "+table+" WHERE owner_id=:owner AND deleted_at IS NULL ORDER BY updated_at DESC,id LIMIT 20 OFFSET :offset",params,
                (r,n)->new CoachingRow(r.getObject("id",UUID.class),r.getObject("parent_id",UUID.class),JSON.parseObject(r.getString("data")),r.getTimestamp("updated_at").toInstant()));
            return new DiaryModels.Page<>(rows,total,page,20);
        });
    }
}
