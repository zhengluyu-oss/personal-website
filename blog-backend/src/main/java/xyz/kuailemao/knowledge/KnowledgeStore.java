package xyz.kuailemao.knowledge;

import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import static xyz.kuailemao.knowledge.KnowledgeModels.*;

public final class KnowledgeStore {
    final NamedParameterJdbcTemplate jdbc;
    final DataSourceTransactionManager manager;
    public KnowledgeStore(NamedParameterJdbcTemplate jdbc,DataSourceTransactionManager manager){this.jdbc=jdbc;this.manager=manager;}
    <T>T tx(long owner,Supplier<T> operation){var tx=new TransactionTemplate(manager);tx.setTimeout(8);try{return tx.execute(status->{jdbc.queryForObject("SELECT set_config('knowledge.owner_id',:owner,true)",Map.of("owner",Long.toString(owner)),String.class);return operation.get();});}catch(KnowledgeFailure f){throw f;}catch(RuntimeException f){throw KnowledgeFailure.unavailable();}}
    static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw KnowledgeFailure.unavailable();}}
    static MapSqlParameterSource params(long owner,UUID id){return new MapSqlParameterSource("owner",owner).addValue("id",id);}
    public boolean available(long owner){try{return tx(owner,()->Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT FROM knowledge.schema_version WHERE version=1) AND (SELECT count(*)=12 AND bool_and(c.relrowsecurity AND c.relforcerowsecurity AND r.rolname='knowledge_migrator') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='knowledge' AND c.relkind='r' AND c.relname<>'schema_version') AND current_user='knowledge_runtime' AND NOT EXISTS(SELECT FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls))",Map.of(),Boolean.class)));}catch(KnowledgeFailure f){return false;}}
    void lockOwner(long owner){jdbc.update("INSERT INTO knowledge.owner_control(owner_id) VALUES(:owner) ON CONFLICT DO NOTHING",Map.of("owner",owner));jdbc.queryForObject("SELECT owner_id FROM knowledge.owner_control WHERE owner_id=:owner FOR UPDATE",Map.of("owner",owner),Long.class);}
    private Content content(ResultSet r)throws SQLException {UUID category=r.getObject("category_id",UUID.class);List<UUID> tags=jdbc.query("SELECT tag_id FROM knowledge.entry_tag WHERE owner_id=:owner AND entry_id=:id ORDER BY tag_id",params(r.getLong("owner_id"),r.getObject("id",UUID.class)),(a,n)->a.getObject(1,UUID.class));return new Content(Kind.valueOf(r.getString("kind")),r.getString("title"),r.getString("body"),r.getString("source_url"),category,tags);}
    private Entry map(ResultSet r,int n)throws SQLException{return new Entry(r.getObject("id",UUID.class),r.getLong("revision"),content(r),r.getObject("current_file_version",UUID.class),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant());}
    Entry entry(long owner,UUID id,boolean deleted,boolean lock){var records=jdbc.query("SELECT * FROM knowledge.entry WHERE owner_id=:owner AND id=:id"+(deleted?"":" AND deleted_at IS NULL")+(lock?" FOR UPDATE":""),params(owner,id),this::map);if(records.isEmpty())throw new KnowledgeFailure(404,"条目不存在或不可访问");return records.get(0);}
    public Entry read(long owner,UUID id,boolean deleted){return tx(owner,()->entry(owner,id,deleted,false));}
    private void labels(long owner,Content content){var expected=new ArrayList<UUID>(content.tagIds());if(content.categoryId()!=null)expected.add(content.categoryId());for(UUID id:expected){String kind=jdbc.query("SELECT kind FROM knowledge.label WHERE owner_id=:owner AND id=:id",params(owner,id),(r,n)->r.getString(1)).stream().findFirst().orElse("");if(!kind.equals(id.equals(content.categoryId())?"CATEGORY":"TAG"))throw KnowledgeFailure.invalid("分类或标签已不存在，请重新选择");}}
    private Ack previous(long owner,UUID mutation,String digest){var rows=jdbc.query("SELECT request_hash,result FROM knowledge.mutation WHERE owner_id=:owner AND id=:id",params(owner,mutation),(r,n)->{if(!digest.equals(r.getString(1)))throw KnowledgeFailure.conflict();return JSON.parseObject(r.getString(2),Ack.class);});return rows.isEmpty()?null:rows.get(0);}
    private Ack record(long owner,UUID mutation,String digest,UUID id,String action){Entry e=entry(owner,id,true,false);Ack ack=new Ack(id,e.revision(),e.updatedAt(),e.deletedAt()!=null);var p=params(owner,id).addValue("revision",e.revision()).addValue("content",JSON.toJSONString(e.content())).addValue("file",e.fileVersion()).addValue("saved",Timestamp.from(e.updatedAt())).addValue("deleted",e.deletedAt()==null?null:Timestamp.from(e.deletedAt())).addValue("action",action);
        jdbc.update("INSERT INTO knowledge.entry_revision(owner_id,entry_id,revision,content,current_file_version,saved_at,deleted_at,action) VALUES(:owner,:id,:revision,CAST(:content AS jsonb),:file,:saved,:deleted,:action)",p);
        p.addValue("mutation",mutation).addValue("digest",digest).addValue("result",JSON.toJSONString(ack));jdbc.update("INSERT INTO knowledge.mutation(owner_id,id,request_hash,result) VALUES(:owner,:mutation,:digest,CAST(:result AS jsonb))",p);return ack;}
    public Ack save(long owner,Write write){write.validate();String digest=hash(write);return tx(owner,()->{
        lockOwner(owner);Ack old=previous(owner,write.mutationId(),digest);if(old!=null)return old;labels(owner,write.content());
        Content c=write.content();var p=params(owner,write.entryId()).addValue("kind",c.kind().name()).addValue("title",c.title().strip()).addValue("body",c.body()).addValue("source",Objects.toString(c.sourceUrl(),"")).addValue("category",c.categoryId()).addValue("expected",write.expectedRevision());
        if(write.expectedRevision()==0){if(jdbc.update("INSERT INTO knowledge.entry(owner_id,id,kind,title,body,source_url,category_id,revision) VALUES(:owner,:id,:kind,:title,:body,:source,:category,1) ON CONFLICT DO NOTHING",p)!=1)throw KnowledgeFailure.conflict();}
        else{Entry before=entry(owner,write.entryId(),false,true);if(before.content().kind()!=c.kind())throw KnowledgeFailure.invalid("已有条目的类型不可更改");if(jdbc.update("UPDATE knowledge.entry SET title=:title,body=:body,source_url=:source,category_id=:category,revision=revision+1,updated_at=now() WHERE owner_id=:owner AND id=:id AND revision=:expected AND deleted_at IS NULL",p)!=1)throw KnowledgeFailure.conflict();}
        jdbc.update("DELETE FROM knowledge.entry_tag WHERE owner_id=:owner AND entry_id=:id",p);for(UUID tag:c.tagIds())jdbc.update("INSERT INTO knowledge.entry_tag(owner_id,entry_id,tag_id) VALUES(:owner,:id,:tag)",params(owner,write.entryId()).addValue("tag",tag));return record(owner,write.mutationId(),digest,write.entryId(),"SAVE");
    });}
    public Ack action(long owner,UUID id,Action request,String action){if(request==null||request.mutationId()==null||request.expectedRevision()<1||!Set.of("DELETE","RESTORE","RECOVER").contains(action))throw KnowledgeFailure.invalid("操作标识或版本无效");String digest=hash(List.of(id,request,action));return tx(owner,()->{
        lockOwner(owner);Ack previous=previous(owner,request.mutationId(),digest);if(previous!=null)return previous;Entry before=entry(owner,id,true,true);if(before.revision()!=request.expectedRevision())throw KnowledgeFailure.conflict();var p=params(owner,id);
        if(action.equals("DELETE")){if(before.deletedAt()!=null)throw KnowledgeFailure.conflict();jdbc.update("UPDATE knowledge.entry SET deleted_at=now(),updated_at=now(),revision=revision+1 WHERE owner_id=:owner AND id=:id",p);jdbc.update("UPDATE knowledge.processing_job SET state='CANCELED',fence=NULL,updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND state IN('QUEUED','RUNNING','RETRY_WAIT')",p);}
        else if(action.equals("RESTORE")){if(before.deletedAt()==null||before.deletedAt().isBefore(Instant.now().minusSeconds(30*86400L)))throw KnowledgeFailure.invalid("仅可恢复保留期内的回收站条目");jdbc.update("UPDATE knowledge.entry SET deleted_at=NULL,updated_at=now(),revision=revision+1 WHERE owner_id=:owner AND id=:id",p);}
        else{if(before.deletedAt()!=null||request.sourceRevision()==null)throw KnowledgeFailure.invalid("请选择有效历史版本");var records=jdbc.query("SELECT content FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id AND revision=:revision",p.addValue("revision",request.sourceRevision()),(r,n)->JSON.parseObject(r.getString(1),Content.class));if(records.isEmpty())throw KnowledgeFailure.invalid("历史版本不存在");Content c=records.get(0);c.validate();labels(owner,c);p.addValue("title",c.title()).addValue("body",c.body()).addValue("source",Objects.toString(c.sourceUrl(),"")).addValue("category",c.categoryId());jdbc.update("UPDATE knowledge.entry SET title=:title,body=:body,source_url=:source,category_id=:category,updated_at=now(),revision=revision+1 WHERE owner_id=:owner AND id=:id",p);jdbc.update("DELETE FROM knowledge.entry_tag WHERE owner_id=:owner AND entry_id=:id",p);for(UUID tag:c.tagIds())jdbc.update("INSERT INTO knowledge.entry_tag VALUES(:owner,:id,:tag)",params(owner,id).addValue("tag",tag));}
        return record(owner,request.mutationId(),digest,id,action);
    });}
    public Page<History> history(long owner,UUID id,int page){if(page<1||page>10000)throw KnowledgeFailure.invalid("页码无效");return tx(owner,()->{entry(owner,id,true,false);var p=params(owner,id).addValue("offset",(page-1)*20);long count=jdbc.queryForObject("SELECT count(*) FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id",p,Long.class);var values=jdbc.query("SELECT * FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id ORDER BY revision DESC LIMIT 20 OFFSET :offset",p,(r,n)->new History(r.getLong("revision"),JSON.parseObject(r.getString("content"),Content.class),r.getObject("current_file_version",UUID.class),r.getTimestamp("saved_at").toInstant(),r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant(),r.getString("action")));return new Page<>(values,count,page,20);});}
    private record Query(String where,MapSqlParameterSource params) {}
    private Query query(long owner,Filter filter){filter.validate();var p=new MapSqlParameterSource("owner",owner);String where="e.owner_id=:owner AND e.deleted_at IS "+(filter.deleted()?"NOT NULL":"NULL");if(filter.kind()!=null){where+=" AND e.kind=:kind";p.addValue("kind",filter.kind().name());}if(filter.categoryId()!=null){where+=" AND e.category_id=:category";p.addValue("category",filter.categoryId());}if(filter.tagId()!=null){where+=" AND EXISTS(SELECT FROM knowledge.entry_tag et WHERE et.owner_id=e.owner_id AND et.entry_id=e.id AND et.tag_id=:tag)";p.addValue("tag",filter.tagId());}if(filter.updatedFrom()!=null){where+=" AND e.updated_at>=:from";p.addValue("from",Timestamp.from(filter.updatedFrom()));}if(filter.updatedTo()!=null){where+=" AND e.updated_at<=:to";p.addValue("to",Timestamp.from(filter.updatedTo()));}
        if(filter.keyword()!=null){p.addValue("q",filter.keyword());where+=" AND (position(lower(:q) in lower(e.title))>0 OR position(lower(:q) in lower(e.body))>0 OR EXISTS(SELECT FROM knowledge.entry_tag et JOIN knowledge.label l ON l.owner_id=et.owner_id AND l.id=et.tag_id WHERE et.owner_id=e.owner_id AND et.entry_id=e.id AND position(lower(:q) in lower(l.name))>0) OR EXISTS(SELECT FROM knowledge.extracted_segment s JOIN knowledge.processing_job j ON j.owner_id=s.owner_id AND j.file_version=s.file_version AND j.state='READY' WHERE s.owner_id=e.owner_id AND s.entry_id=e.id AND s.file_version=e.current_file_version AND position(lower(:q) in lower(s.body))>0))";}return new Query(where,p);}
    public Page<Entry> list(long owner,Filter filter){return tx(owner,()->{var q=query(owner,filter);long total=jdbc.queryForObject("SELECT count(*) FROM knowledge.entry e WHERE "+q.where,q.params,Long.class);q.params.addValue("offset",(filter.number()-1)*filter.size()).addValue("limit",filter.size());String order=filter.keyword()==null?"":"CASE WHEN position(lower(:q) in lower(e.title))>0 THEN 0 WHEN EXISTS(SELECT FROM knowledge.entry_tag et JOIN knowledge.label l ON l.owner_id=et.owner_id AND l.id=et.tag_id WHERE et.owner_id=e.owner_id AND et.entry_id=e.id AND position(lower(:q) in lower(l.name))>0) THEN 1 ELSE 2 END,";var values=jdbc.query("SELECT e.owner_id,e.id,e.kind,e.title,CAST('' AS text) AS body,e.source_url,e.category_id,e.revision,e.current_file_version,e.created_at,e.updated_at,e.deleted_at FROM knowledge.entry e WHERE "+q.where+" ORDER BY "+order+"e.updated_at DESC,e.id LIMIT :limit OFFSET :offset",q.params,this::map);return new Page<>(values,total,filter.number(),filter.size());});}
    public List<Label> labels(long owner){return tx(owner,()->jdbc.query("SELECT * FROM knowledge.label WHERE owner_id=:owner ORDER BY kind,name,id",Map.of("owner",owner),(r,n)->new Label(r.getObject("id",UUID.class),r.getString("kind"),r.getString("name"),r.getLong("revision"))));}
    /** Only bounded plain text excerpts leave this query; snippets are never HTML. */
    public Page<SearchHit> search(long owner,Filter filter){
        return tx(owner,()->{
            Page<Entry> entries=list(owner,filter);
            List<SearchHit> hits=new ArrayList<>();
            for(Entry entry:entries.records()){
                var p=params(owner,entry.id()).addValue("q",Objects.toString(filter.keyword(),""));
                var snippets=jdbc.query("""
                    SELECT substring(body FROM greatest(1,position(lower(:q) in lower(body))-80) FOR 320) AS excerpt,
                           NULL::integer AS page,NULL::integer AS from_line,NULL::integer AS to_line
                      FROM knowledge.entry WHERE owner_id=:owner AND id=:id
                       AND (:q='' OR position(lower(:q) in lower(body))>0)
                    UNION ALL
                    (SELECT substring(s.body FROM greatest(1,position(lower(:q) in lower(s.body))-80) FOR 320),s.page,s.from_line,s.to_line
                       FROM knowledge.extracted_segment s
                       JOIN knowledge.entry e ON e.owner_id=s.owner_id AND e.id=s.entry_id AND e.current_file_version=s.file_version
                      WHERE s.owner_id=:owner AND s.entry_id=:id AND e.deleted_at IS NULL
                        AND position(lower(:q) in lower(s.body))>0
                        AND EXISTS(SELECT FROM knowledge.processing_job j WHERE j.owner_id=s.owner_id AND j.file_version=s.file_version AND j.state='READY')
                      ORDER BY s.ordinal LIMIT 1)
                    LIMIT 1
                    """,p,(r,n)->new SearchHit(entry,r.getString("excerpt"),r.getObject("page",Integer.class),r.getObject("from_line",Integer.class),r.getObject("to_line",Integer.class)));
                hits.add(snippets.isEmpty()?new SearchHit(entry,"",null,null,null):snippets.get(0));
            }
            return new Page<>(hits,entries.total(),entries.page(),entries.pageSize());
        });
    }
    public Label saveLabel(long owner,LabelWrite w){if(w==null||w.id()==null||!Set.of("CATEGORY","TAG").contains(Objects.toString(w.kind(),""))||w.name()==null||w.name().isBlank()||w.name().codePointCount(0,w.name().length())>80||w.name().indexOf('\0')>=0||w.expectedRevision()<0)throw KnowledgeFailure.invalid("分类标签需为1至80字且版本有效");return tx(owner,()->{lockOwner(owner);var p=params(owner,w.id()).addValue("kind",w.kind()).addValue("name",w.name().strip()).addValue("revision",w.expectedRevision());if(w.expectedRevision()==0){if(jdbc.update("INSERT INTO knowledge.label(owner_id,id,kind,name,revision) VALUES(:owner,:id,:kind,:name,1) ON CONFLICT DO NOTHING",p)!=1)throw KnowledgeFailure.conflict();}else if(jdbc.update("UPDATE knowledge.label SET name=:name,revision=revision+1 WHERE owner_id=:owner AND id=:id AND kind=:kind AND revision=:revision",p)!=1)throw KnowledgeFailure.conflict();return new Label(w.id(),w.kind(),w.name().strip(),w.expectedRevision()+1);});}
    public void deleteLabel(long owner,UUID id,long revision){tx(owner,()->{lockOwner(owner);var p=params(owner,id).addValue("revision",revision);long count=jdbc.queryForObject("SELECT (SELECT count(*) FROM knowledge.entry WHERE owner_id=:owner AND category_id=:id)+(SELECT count(*) FROM knowledge.entry_tag WHERE owner_id=:owner AND tag_id=:id)",p,Long.class);if(count>0)throw KnowledgeFailure.invalid("仍有条目使用该分类或标签，不能删除");if(jdbc.update("DELETE FROM knowledge.label WHERE owner_id=:owner AND id=:id AND revision=:revision",p)!=1)throw KnowledgeFailure.conflict();return null;});}
    /** Explicit owner action; private-file records remain until their objects can be safely reclaimed. */
    public PurgeResult purgeExpired(long owner){return tx(owner,()->{
        lockOwner(owner);
        var p=new MapSqlParameterSource("owner",owner);
        long blocked=jdbc.queryForObject("SELECT count(*) FROM knowledge.entry e WHERE e.owner_id=:owner AND e.deleted_at<now()-interval '30 days' AND EXISTS(SELECT FROM knowledge.file_version f WHERE f.owner_id=e.owner_id AND f.entry_id=e.id AND f.state<>'CLEANED')",p,Long.class);
        List<UUID> ids=jdbc.query("SELECT e.id FROM knowledge.entry e WHERE e.owner_id=:owner AND e.deleted_at<now()-interval '30 days' AND NOT EXISTS(SELECT FROM knowledge.file_version f WHERE f.owner_id=e.owner_id AND f.entry_id=e.id) ORDER BY e.deleted_at,e.id LIMIT 50 FOR UPDATE OF e SKIP LOCKED",p,(r,n)->r.getObject(1,UUID.class));
        for(UUID id:ids){
            var item=params(owner,id).addValue("entryKey",id.toString());
            jdbc.update("DELETE FROM knowledge.relation WHERE owner_id=:owner AND ((from_kind='ENTRY' AND from_id=:entryKey) OR (to_kind='ENTRY' AND to_id=:entryKey))",item);
            jdbc.update("DELETE FROM knowledge.outbox WHERE owner_id=:owner AND job_id IN(SELECT id FROM knowledge.processing_job WHERE owner_id=:owner AND entry_id=:id)",item);
            jdbc.update("DELETE FROM knowledge.processing_job WHERE owner_id=:owner AND entry_id=:id",item);
            jdbc.update("DELETE FROM knowledge.entry_tag WHERE owner_id=:owner AND entry_id=:id",item);
            jdbc.update("DELETE FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id",item);
            jdbc.update("DELETE FROM knowledge.mutation WHERE owner_id=:owner AND result->>'id'=:entryKey",item);
            jdbc.update("DELETE FROM knowledge.entry WHERE owner_id=:owner AND id=:id AND deleted_at<now()-interval '30 days'",item);
        }
        return new PurgeResult(ids.size(),blocked);
    });}
}
