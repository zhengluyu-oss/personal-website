package xyz.luyu.diary;

import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static xyz.luyu.diary.DiaryModels.*;

public final class DiaryStore {
    static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    static final int EXPORT_BYTES=20*1024*1024;
    private final NamedParameterJdbcTemplate jdbc;
    private final DataSourceTransactionManager manager;
    private final RowMapper<Entry> entryMapper=(r,n)->mapEntry(r);

    public DiaryStore(NamedParameterJdbcTemplate jdbc, DataSourceTransactionManager manager) {
        this.jdbc=jdbc; this.manager=manager;
    }

    private <T> T transaction(long owner, boolean snapshot, Supplier<T> operation) {
        TransactionTemplate tx=new TransactionTemplate(manager);
        tx.setTimeout(8);
        if(snapshot) tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            return tx.execute(status->{
                jdbc.queryForObject("SELECT set_config('diary.owner_id',:owner,true)",
                        Map.of("owner",Long.toString(owner)),String.class);
                return operation.get();
            });
        } catch(DiaryFailure e) { throw e; }
        catch(RuntimeException e) { throw DiaryFailure.unavailable(); }
    }

    public boolean available(long owner) {
        try {
            return transaction(owner,false,()->Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT current_user='diary_runtime' AND NOT r.rolsuper AND NOT r.rolbypassrls "
                +"AND NOT EXISTS(SELECT 1 FROM pg_auth_members m WHERE m.member=r.oid) "
                +"AND (SELECT count(*)=3 AND bool_and(c.relrowsecurity AND c.relforcerowsecurity AND c.relowner<>r.oid) "
                +"FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace "
                +"WHERE n.nspname='diary' AND c.relname IN ('entry','entry_revision','mutation')) "
                +"FROM pg_roles r WHERE r.rolname=current_user",Map.of(),Boolean.class)));
        } catch(DiaryFailure e) { return false; }
    }

    private Entry get(long owner,UUID id) {
        List<Entry> rows=jdbc.query("SELECT * FROM diary.entry WHERE owner_id=:owner AND id=:id",
                Map.of("owner",owner,"id",id),entryMapper);
        if(rows.isEmpty()) throw new DiaryFailure(404,"日记不存在或不可访问");
        return rows.get(0);
    }
    public Entry read(long owner,UUID id) {
        return transaction(owner,false,()->get(owner,id));
    }

    public Ack create(long owner,Write write) {
        if(write.entryId()==null || write.mutationId()==null || write.expectedRevision()!=0)
            throw DiaryFailure.invalid("新建请求标识或版本无效");
        write.content().validate();
        return mutate(owner,"CREATE",write.entryId(),write.mutationId(),0L,write.content(),null);
    }
    public Ack save(long owner,UUID id,Write write) {
        if(write.entryId()!=null && !id.equals(write.entryId())) throw DiaryFailure.invalid("日记标识不一致");
        write.content().validate();
        return mutate(owner,"SAVE",id,write.mutationId(),write.expectedRevision(),write.content(),null);
    }
    public Ack delete(long owner,UUID id,Change change) {
        return mutate(owner,"DELETE",id,change.mutationId(),change.expectedRevision(),null,null);
    }
    public Ack restore(long owner,UUID id,Change change) {
        if(change.sourceRevision()==null || change.sourceRevision()<1) throw DiaryFailure.invalid("请选择历史版本");
        return mutate(owner,"RESTORE",id,change.mutationId(),change.expectedRevision(),null,change.sourceRevision());
    }

    private Ack mutate(long owner,String action,UUID id,UUID mutation,long expected,Content content,Long source) {
        if(id==null || mutation==null || expected<0 || !"CREATE".equals(action) && expected<1)
            throw DiaryFailure.invalid("请求标识或版本无效");
        final String digest;
        try {
            // 有序固定字段完整摘要，不依赖对象toString，不输出或记录摘要原文。
            digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(JSON.toJSONBytes(Arrays.asList(owner,action,id,mutation,expected,content,source))));
        } catch(Exception e) { throw DiaryFailure.unavailable(); }
        return transaction(owner,false,()->{
            // 同一站主的变更在数据库内串行；版本条件仍是写入权威，不依赖应用进程锁。
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(:owner)",Map.of("owner",owner),Object.class);
            var prior=jdbc.query("SELECT * FROM diary.mutation WHERE owner_id=:owner AND mutation_id=:mutation",
                Map.of("owner",owner,"mutation",mutation),(r,n)->{
                    if(!digest.equals(r.getString("request_digest"))) throw DiaryFailure.conflict();
                    return new Ack(r.getObject("entry_id",UUID.class),mutation,r.getLong("result_revision"),r.getTimestamp("saved_at").toInstant());
                });
            if(!prior.isEmpty()) return prior.get(0);
            Entry current=null;
            Content next=content;
            if(!"CREATE".equals(action)) {
                current=get(owner,id);
                if(current.revision()!=expected || current.deletedAt()!=null && !"RESTORE".equals(action))
                    throw DiaryFailure.conflict();
                if("DELETE".equals(action)) next=current.content();
                if("RESTORE".equals(action)) {
                    var snapshots=jdbc.query("SELECT title,body,covered_from,covered_to FROM diary.entry_revision "
                            +"WHERE owner_id=:owner AND entry_id=:id AND revision=:source",
                            Map.of("owner",owner,"id",id,"source",source),
                            (r,n)->new Content(r.getString("title"),r.getString("body"),r.getObject("covered_from",LocalDate.class),r.getObject("covered_to",LocalDate.class)));
                    if(snapshots.isEmpty()) throw new DiaryFailure(404,"历史版本不存在");
                    next=snapshots.get(0);
                }
            }
            boolean unchanged="SAVE".equals(action) && current.content().equals(next);
            Instant savedAt=unchanged ? current.updatedAt() : jdbc.queryForObject("SELECT transaction_timestamp()",Map.of(),java.sql.Timestamp.class).toInstant();
            long revision=unchanged ? current.revision() : expected+1;
            var p=new MapSqlParameterSource().addValue("owner",owner).addValue("id",id)
                    .addValue("title",next.title()).addValue("body",next.body())
                    .addValue("from",next.coveredFrom()).addValue("to",next.coveredTo())
                    .addValue("expected",expected).addValue("revision",revision)
                    .addValue("saved",java.sql.Timestamp.from(savedAt)).addValue("source",source)
                    .addValue("action",action).addValue("deleted","DELETE".equals(action))
                    .addValue("deletedAt","DELETE".equals(action)?java.sql.Timestamp.from(savedAt):null)
                    .addValue("mutation",mutation).addValue("digest",digest);
            if(!unchanged) {
                int changed;
                if("CREATE".equals(action)) {
                    changed=jdbc.update("INSERT INTO diary.entry(id,owner_id,title,body,covered_from,covered_to,revision,created_at,updated_at) "
                            +"VALUES(:id,:owner,:title,:body,:from,:to,:revision,:saved,:saved) ON CONFLICT(id) DO NOTHING",p);
                } else {
                    changed=jdbc.update("UPDATE diary.entry SET title=:title,body=:body,covered_from=:from,covered_to=:to,"
                            +"revision=:revision,updated_at=:saved,deleted_at=:deletedAt "
                            +"WHERE id=:id AND owner_id=:owner AND revision=:expected",p);
                }
                if(changed!=1) throw DiaryFailure.conflict();
                jdbc.update("INSERT INTO diary.entry_revision(entry_id,owner_id,revision,title,body,covered_from,covered_to,action,saved_at,source_revision,deleted) "
                        +"VALUES(:id,:owner,:revision,:title,:body,:from,:to,:action,:saved,:source,:deleted)",p);
            }
            jdbc.update("INSERT INTO diary.mutation(owner_id,mutation_id,entry_id,action,request_digest,result_revision,saved_at) "
                    +"VALUES(:owner,:mutation,:id,:action,:digest,:revision,:saved)",p);
            return new Ack(id,mutation,revision,savedAt);
        });
    }

    private record Filter(String where,MapSqlParameterSource values,int page,int size) {}
    private Filter filter(long owner,Search s,boolean exporting) {
        if(s==null) s=new Search(null,null,null,null,null,null,false);
        String axis=s.axis()==null?"created":s.axis();
        if(!Set.of("created","covered").contains(axis)) throw DiaryFailure.invalid("时间筛选类型无效");
        int page=s.page()==null?1:s.page(), size=s.pageSize()==null?20:s.pageSize();
        if(page<1 || page>100000 || size<1 || size>100) throw DiaryFailure.invalid("分页范围无效");
        if((s.from()==null)!=(s.to()==null) || s.from()!=null && s.from().isAfter(s.to()))
            throw DiaryFailure.invalid("请选择完整有效的筛选范围");
        if(exporting && (s.from()==null || ChronoUnit.DAYS.between(s.from(),s.to())>=366))
            throw DiaryFailure.invalid("导出请选择最多366天的范围");
        String word=s.keyword()==null?"":s.keyword();
        if(word.codePointCount(0,word.length())>200) throw DiaryFailure.invalid("搜索词最多200字");
        var p=new MapSqlParameterSource("owner",owner);
        String where=" WHERE owner_id=:owner AND deleted_at IS "+(Boolean.TRUE.equals(s.deleted())&&!exporting?"NOT NULL":"NULL");
        if("covered".equals(axis) && s.from()==null) throw DiaryFailure.invalid("按内容时间筛选时请选择范围");
        if(s.from()!=null) {
            if("covered".equals(axis)) {
                where+=" AND covered_from<=:to AND covered_to>=:from";
                p.addValue("from",s.from()).addValue("to",s.to());
            } else {
                where+=" AND created_at>=:from AND created_at<:to";
                p.addValue("from",java.sql.Timestamp.from(s.from().atStartOfDay(ZONE).toInstant()))
                    .addValue("to",java.sql.Timestamp.from(s.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
            }
        }
        if(!word.isEmpty()) {
            where+=" AND (strpos(lower(title),lower(:word))>0 OR strpos(lower(body),lower(:word))>0)";
            p.addValue("word",word);
        }
        p.addValue("limit",size).addValue("offset",(long)(page-1)*size);
        return new Filter(where,p,page,size);
    }

    public Page<Entry> search(long owner,Search search) {
        Filter f=filter(owner,search,false);
        return transaction(owner,true,()->{
            long total=jdbc.queryForObject("SELECT count(*) FROM diary.entry"+f.where(),f.values(),Long.class);
            List<Entry> rows=jdbc.query("SELECT id,title,''::text AS body,covered_from,covered_to,revision,created_at,updated_at,deleted_at "
                    +"FROM diary.entry"+f.where()+" ORDER BY updated_at DESC,id DESC LIMIT :limit OFFSET :offset",f.values(),entryMapper);
            return new Page<>(rows,total,f.page(),f.size());
        });
    }

    public List<CalendarDay> calendar(long owner,YearMonth month) {
        var p=new MapSqlParameterSource("owner",owner)
                .addValue("from",java.sql.Timestamp.from(month.atDay(1).atStartOfDay(ZONE).toInstant()))
                .addValue("to",java.sql.Timestamp.from(month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant()));
        return transaction(owner,false,()->jdbc.query("SELECT (created_at AT TIME ZONE 'Asia/Shanghai')::date AS day,count(*) AS total "
                +"FROM diary.entry WHERE owner_id=:owner AND deleted_at IS NULL AND created_at>=:from AND created_at<:to "
                +"GROUP BY day ORDER BY day",p,(r,n)->new CalendarDay(r.getObject("day",LocalDate.class),r.getLong("total"))));
    }

    public Page<History> history(long owner,UUID id,int page) {
        if(page<1 || page>100000) throw DiaryFailure.invalid("历史页码无效");
        return transaction(owner,true,()->{
            get(owner,id);
            var p=Map.of("owner",owner,"id",id,"offset",(page-1)*20);
            long total=jdbc.queryForObject("SELECT count(*) FROM diary.entry_revision WHERE owner_id=:owner AND entry_id=:id",p,Long.class);
            var rows=jdbc.query("SELECT * FROM diary.entry_revision WHERE owner_id=:owner AND entry_id=:id "
                    +"ORDER BY revision DESC LIMIT 20 OFFSET :offset",p,(r,n)->new History(r.getLong("revision"),r.getString("title"),
                    r.getString("body"),r.getObject("covered_from",LocalDate.class),r.getObject("covered_to",LocalDate.class),
                    r.getString("action"),r.getTimestamp("saved_at").toInstant(),r.getObject("source_revision",Long.class),r.getBoolean("deleted")));
            return new Page<>(rows,total,page,20);
        });
    }

    public Download export(long owner,Export request) {
        if(request==null || request.format()==null || !Set.of("json","markdown").contains(request.format())) throw DiaryFailure.invalid("请选择导出格式");
        Filter f=filter(owner,request.filter(),true);
        return transaction(owner,true,()->{
            long count=jdbc.queryForObject("SELECT count(*) FROM diary.entry"+f.where(),f.values(),Long.class);
            if(count>1000) throw DiaryFailure.invalid("超过1000篇，请缩小范围分段导出");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            boolean markdown="markdown".equals(request.format());
            try(ZipOutputStream zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
                if(!markdown) bytes.write('[');
                final long[] used={0}, rows={0};
                jdbc.query("SELECT * FROM diary.entry"+f.where()+" ORDER BY updated_at DESC,id DESC LIMIT 1000",
                        f.values(),(org.springframework.jdbc.core.RowCallbackHandler)r->{
                    Entry entry=mapEntry(r);
                    byte[] data=markdown?("# "+entry.title()+"\n\n记录ID："+entry.id()+"\n\n创建："+entry.createdAt()
                            +"\n\n修改："+entry.updatedAt()+"\n\n记录范围："+entry.coveredFrom()+" 至 "+entry.coveredTo()
                            +"\n\n---\n\n"+entry.body()).getBytes(StandardCharsets.UTF_8):JSON.toJSONBytes(entry);
                    used[0]+=data.length;
                    if(used[0]>EXPORT_BYTES) throw DiaryFailure.invalid("导出超过20MiB，请分段导出");
                    try {
                        if(markdown) { zip.putNextEntry(new ZipEntry(entry.id()+".md"));zip.write(data);zip.closeEntry(); }
                        else { if(rows[0]>0) bytes.write(',');bytes.write(data); }
                        rows[0]++;
                    } catch(java.io.IOException e) { throw DiaryFailure.unavailable(); }
                });
                if(!markdown) bytes.write(']');
                if(markdown) zip.finish();
                if(bytes.size()>EXPORT_BYTES) throw DiaryFailure.invalid("导出超过20MiB，请分段导出");
                return new Download(bytes.toByteArray(),markdown?"diary-markdown.zip":"diary.json",
                        markdown?"application/zip":"application/json;charset=UTF-8");
            } catch(java.io.IOException e) { throw DiaryFailure.unavailable(); }
        });
    }

    private static Entry mapEntry(ResultSet r) throws SQLException {
        var deleted=r.getTimestamp("deleted_at");
        return new Entry(r.getObject("id",UUID.class),r.getString("title"),r.getString("body"),
                r.getObject("covered_from",LocalDate.class),r.getObject("covered_to",LocalDate.class),r.getLong("revision"),
                r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),deleted==null?null:deleted.toInstant());
    }
}
