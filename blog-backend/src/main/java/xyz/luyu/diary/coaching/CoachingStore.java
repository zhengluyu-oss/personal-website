package xyz.luyu.diary.coaching;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.luyu.diary.DiaryFailure;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static xyz.luyu.diary.coaching.CoachingModels.*;

/** 独立 schema，共用三连接私密连接池；所有外部输入只作为绑定参数。 */
public final class CoachingStore {
    final NamedParameterJdbcTemplate jdbc;
    final DataSourceTransactionManager manager;
    public CoachingStore(NamedParameterJdbcTemplate jdbc,DataSourceTransactionManager manager) {
        this.jdbc=jdbc;this.manager=manager;
        // A commit exception must not fall through to an implicit commit when a pooled connection resets autocommit.
        // An actually committed but unacknowledged request is still recovered through its immutable mutation receipt.
        this.manager.setRollbackOnCommitFailure(true);
    }
    <T>T tx(long owner,boolean snapshot,Supplier<T> operation) {
        var tx=new TransactionTemplate(manager);tx.setTimeout(8);
        if(snapshot) {
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
        try { return tx.execute(s->{
            jdbc.queryForObject("SELECT set_config('diary.owner_id',:id,true)",Map.of("id",Long.toString(owner)),String.class);
            return operation.get();
        }); } catch(DiaryFailure failure) { throw failure; } catch(RuntimeException failure) { throw DiaryFailure.unavailable(); }
    }
    public boolean available(long owner) {
        try { return tx(owner,false,()->Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT current_user='diary_runtime' AND NOT r.rolsuper AND NOT r.rolbypassrls "
            +"AND NOT EXISTS(SELECT FROM pg_auth_members m WHERE m.member=r.oid) "
            +"AND EXISTS(SELECT FROM diary_coaching.schema_version WHERE version=1) "
            +"AND (SELECT count(*)=12 AND bool_and(c.relrowsecurity AND c.relforcerowsecurity AND c.relowner<>r.oid) "
            +"FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='diary_coaching' "
            +"AND c.relkind='r' AND c.relname<>'schema_version') FROM pg_roles r WHERE r.rolname=current_user",Map.of(),Boolean.class))); }
        catch(DiaryFailure failure) { return false; }
    }
    static MapSqlParameterSource parameters(long owner,UUID id) { return new MapSqlParameterSource().addValue("owner",owner).addValue("id",id); }
    private Row map(Kind kind,ResultSet r) throws SQLException {
        Timestamp deleted=r.getTimestamp("deleted_at");
        return new Row(kind,r.getObject("id",UUID.class),r.getObject("parent_id",UUID.class),r.getLong("revision"),JSON.parseObject(r.getString("data")),
            r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),deleted==null?null:deleted.toInstant());
    }
    Row get(long owner,Kind kind,UUID id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM diary_coaching."+kind.table+" WHERE owner_id=:owner AND id=:id"+(lock?" FOR UPDATE":""),
            parameters(owner,id),(r,n)->map(kind,r));
        if(rows.isEmpty()) throw new DiaryFailure(404,"记录不存在或不可访问");return rows.get(0);
    }
    public Row read(long owner,Kind kind,UUID id) { return tx(owner,false,()->get(owner,kind,id,false)); }
    void lockOwner(long owner) {
        var p=Map.of("owner",owner);
        jdbc.update("INSERT INTO diary_coaching.owner_control(owner_id) VALUES(:owner) ON CONFLICT DO NOTHING",p);
        jdbc.queryForObject("SELECT owner_id FROM diary_coaching.owner_control WHERE owner_id=:owner FOR UPDATE",p,Long.class);
    }
    public Page<Row> list(long owner,Kind kind,UUID parent,String state,int page,int size) {
        if(page<1 || page>10000 || size<1 || size>100 || state!=null && !state.matches("[A-Z_]{1,20}")) throw DiaryFailure.invalid("分页或状态无效");
        return tx(owner,true,()->{
            var p=parameters(owner,parent).addValue("state",state).addValue("limit",size).addValue("offset",(page-1)*size);
            String where=" WHERE owner_id=:owner AND deleted_at IS NULL"+(parent==null?"":" AND parent_id=:id")+(state==null?"":" AND data->>'state'=:state");
            long total=jdbc.queryForObject("SELECT count(*) FROM diary_coaching."+kind.table+where,p,Long.class);
            var rows=jdbc.query("SELECT * FROM diary_coaching."+kind.table+where+" ORDER BY updated_at DESC,id LIMIT :limit OFFSET :offset",p,(r,n)->map(kind,r));
            return new Page<>(rows,total,page,size);
        });
    }
    public Page<History> history(long owner,Kind kind,UUID id,int page) {
        if(page<1 || page>10000) throw DiaryFailure.invalid("页码无效");
        return tx(owner,true,()->{
            get(owner,kind,id,false);var p=parameters(owner,id).addValue("offset",(page-1)*20);
            long total=jdbc.queryForObject("SELECT count(*) FROM diary_coaching."+kind.table+"_revision WHERE owner_id=:owner AND id=:id",p,Long.class);
            var rows=jdbc.query("SELECT * FROM diary_coaching."+kind.table+"_revision WHERE owner_id=:owner AND id=:id ORDER BY revision DESC LIMIT 20 OFFSET :offset",p,(r,n)->{
                Timestamp deleted=r.getTimestamp("deleted_at");return new History(r.getLong("revision"),JSON.parseObject(r.getString("data")),r.getTimestamp("saved_at").toInstant(),deleted==null?null:deleted.toInstant());
            });return new Page<>(rows,total,page,20);
        });
    }
    @FunctionalInterface public interface OriginCheck { void verify(long owner,Origin origin); }
    public Ack write(long owner,Kind kind,Write write,boolean delete,OriginCheck sourceCheck) {
        if(write==null || write.id()==null || write.mutationId()==null || write.expectedRevision()<0
                || delete && (kind!=Kind.FEEDBACK || write.expectedRevision()<1 || write.content()!=null)
                || write.origin()!=null && (kind!=Kind.ACTION || write.expectedRevision()!=0)
                || write.completeActionRevision()!=null && (kind!=Kind.FEEDBACK || delete || write.completeActionRevision()<1)) throw DiaryFailure.invalid("请求标识、版本或操作无效");
        JSONObject next=delete?null:normalize(kind,write.content(),LocalDate.now(ZONE));
        if(write.origin()!=null) write.origin().validate();
        String digest=hash(Arrays.asList(owner,kind,write.id(),write.mutationId(),write.expectedRevision(),next,write.origin(),write.completeActionRevision(),delete));
        return tx(owner,false,()->{
            lockOwner(owner);
            var p=parameters(owner,write.id()).addValue("mutation",write.mutationId()).addValue("hash",digest);
            var previous=jdbc.query("SELECT request_hash,ack FROM diary_coaching.mutation WHERE owner_id=:owner AND mutation_id=:mutation",p,(r,n)->{
                if(!digest.equals(r.getString(1))) throw DiaryFailure.conflict();return JSON.parseObject(r.getString(2),Ack.class);
            });
            if(!previous.isEmpty()) return previous.get(0);
            Row current=write.expectedRevision()==0?null:get(owner,kind,write.id(),true);
            if(current!=null && (current.revision()!=write.expectedRevision() || current.deletedAt()!=null)) throw DiaryFailure.conflict();
            if(current==null && Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT FROM diary_coaching."+kind.table+" WHERE owner_id=:owner AND id=:id)",p,Boolean.class))) throw DiaryFailure.conflict();
            JSONObject data=delete?current.content():next;
            UUID parent=kind==Kind.GOAL?null:UUID.fromString(data.getString(kind==Kind.ACTION?"goalId":"actionId"));
            if(parent!=null) {
                Row parentRow=get(owner,kind==Kind.ACTION?Kind.GOAL:Kind.ACTION,parent,true);
                if(parentRow.deletedAt()!=null || current!=null && !Objects.equals(current.parentId(),parent)) throw DiaryFailure.invalid("父记录无效或不允许更换归属");
            }
            if(write.origin()!=null) sourceCheck.verify(owner,write.origin());
            checkLimits(owner,kind,current,data);
            Instant time=jdbc.queryForObject("SELECT transaction_timestamp()",Map.of(),Timestamp.class).toInstant();
            boolean unchanged=!delete && current!=null && canonical(current.content()).equals(canonical(data));
            long revision=unchanged?current.revision():write.expectedRevision()+1;
            Instant saved=unchanged?current.updatedAt():time;
            if(!unchanged) persist(owner,kind,write.id(),parent,write.expectedRevision(),revision,data,saved,delete?saved:null,current==null);
            if(write.completeActionRevision()!=null) {
                if(!"COMPLETED".equals(data.getString("execution"))) throw DiaryFailure.invalid("仅明确完成的反馈可以联动完成行动");
                Row action=get(owner,Kind.ACTION,parent,true);
                if(action.revision()!=write.completeActionRevision() || action.deletedAt()!=null) throw DiaryFailure.conflict();
                if(!"COMPLETED".equals(action.content().getString("state"))) {
                    JSONObject updated=JSON.parseObject(action.content().toJSONString());updated.put("state","COMPLETED");
                    persist(owner,Kind.ACTION,action.id(),action.parentId(),action.revision(),action.revision()+1,updated,time,null,false);
                }
            }
            if(write.origin()!=null) {
                Origin origin=write.origin();p.addValue("type",origin.type()).addValue("report",origin.reportId()).addValue("index",origin.suggestionIndex()).addValue("suggestion",origin.suggestionHash());
                jdbc.update("INSERT INTO diary_coaching.origin_link VALUES(:owner,:id,:type,:report,:index,:suggestion)",p);
            }
            Ack ack=new Ack(write.id(),write.mutationId(),revision,saved);
            p.addValue("ack",JSON.toJSONString(ack));jdbc.update("INSERT INTO diary_coaching.mutation VALUES(:owner,:mutation,:hash,CAST(:ack AS jsonb))",p);
            return ack;
        });
    }
    private void checkLimits(long owner,Kind kind,Row current,JSONObject data) {
        var p=Map.of("owner",owner);
        if(kind==Kind.GOAL && current==null && jdbc.queryForObject("SELECT count(*) FROM diary_coaching.goal WHERE owner_id=:owner",p,Long.class)>=1000) throw DiaryFailure.invalid("目标总量已达1000，请先导出整理");
        boolean active=kind==Kind.GOAL && "ACTIVE".equals(data.getString("state"));
        boolean unfinished=kind==Kind.ACTION && Set.of("PLANNED","IN_PROGRESS").contains(data.getString("state"));
        if(active || unfinished) {
            String clause=active?"data->>'state'='ACTIVE'":"data->>'state' IN('PLANNED','IN_PROGRESS')";
            long count=jdbc.queryForObject("SELECT count(*) FROM diary_coaching."+kind.table+" WHERE owner_id=:owner AND deleted_at IS NULL AND "+clause,p,Long.class);
            boolean already=current!=null && (active?"ACTIVE".equals(current.content().getString("state")):Set.of("PLANNED","IN_PROGRESS").contains(current.content().getString("state")));
            if(!already && count>=(active?20:100)) throw DiaryFailure.invalid(active?"活动目标最多20个":"未结束行动最多100个");
        }
    }
    private void persist(long owner,Kind kind,UUID id,UUID parent,long expected,long revision,JSONObject data,Instant time,Instant deleted,boolean create) {
        var p=parameters(owner,id).addValue("parent",parent).addValue("expected",expected).addValue("revision",revision)
            .addValue("data",data.toJSONString()).addValue("time",Timestamp.from(time)).addValue("deleted",deleted==null?null:Timestamp.from(deleted));
        int rows=create?jdbc.update("INSERT INTO diary_coaching."+kind.table+" VALUES(:owner,:id,:parent,:revision,CAST(:data AS jsonb),:time,:time,:deleted)",p)
            :jdbc.update("UPDATE diary_coaching."+kind.table+" SET revision=:revision,data=CAST(:data AS jsonb),updated_at=:time,deleted_at=:deleted WHERE owner_id=:owner AND id=:id AND revision=:expected",p);
        if(rows!=1) throw DiaryFailure.conflict();
        jdbc.update("INSERT INTO diary_coaching."+kind.table+"_revision VALUES(:owner,:id,:revision,CAST(:data AS jsonb),:time,:deleted)",p);
    }
    List<Row> selected(long owner,Kind kind,List<UUID> ids,boolean byParent,int limit) {
        if(ids.isEmpty()) return List.of();
        return jdbc.query("SELECT * FROM diary_coaching."+kind.table+" WHERE owner_id=:owner AND deleted_at IS NULL AND "+(byParent?"parent_id":"id")+" IN(:ids) ORDER BY id LIMIT :limit",
            Map.of("owner",owner,"ids",ids,"limit",limit),(r,n)->map(kind,r));
    }
    public byte[] export(long owner) {
        return tx(owner,true,()->{
            var result=new LinkedHashMap<String,Object>();result.put("format","private-coaching-v1");result.put("exportedAt",Instant.now());int count=0;
            for(Kind kind:Kind.values()) {
                var rows=jdbc.query("SELECT * FROM diary_coaching."+kind.table+" WHERE owner_id=:owner ORDER BY id LIMIT 1001",Map.of("owner",owner),(r,n)->map(kind,r));
                count+=rows.size();if(count>1000) throw DiaryFailure.invalid("导出超过1000实体，请分阶段整理记录");result.put(kind.table,rows);
                if(JSON.toJSONBytes(result).length>20*1024*1024) throw DiaryFailure.invalid("导出超过20MiB");
            }
            return JSON.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        });
    }
}
