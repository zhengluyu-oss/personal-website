package xyz.luyu.knowledge;

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
import static xyz.luyu.knowledge.KnowledgeModels.*;

public final class KnowledgeStore {
    final NamedParameterJdbcTemplate jdbc;
    final DataSourceTransactionManager manager;
    public KnowledgeStore(NamedParameterJdbcTemplate jdbc,DataSourceTransactionManager manager){this.jdbc=jdbc;this.manager=manager;}
    <T>T tx(long owner,Supplier<T> operation){var tx=new TransactionTemplate(manager);tx.setTimeout(8);try{return tx.execute(status->{jdbc.queryForObject("SELECT set_config('knowledge.owner_id',:owner,true)",Map.of("owner",Long.toString(owner)),String.class);return operation.get();});}catch(KnowledgeFailure f){throw f;}catch(RuntimeException f){throw KnowledgeFailure.unavailable();}}
    static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJSONString(value).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw KnowledgeFailure.unavailable();}}
    static MapSqlParameterSource params(long owner,UUID id){return new MapSqlParameterSource("owner",owner).addValue("id",id);}
    public boolean available(long owner){try{return tx(owner,()->Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT FROM knowledge.schema_version WHERE version=1) AND EXISTS(SELECT FROM knowledge.schema_version WHERE version=2) AND EXISTS(SELECT FROM knowledge.schema_version WHERE version=3) AND (SELECT count(*)=13 AND bool_and(c.relrowsecurity AND c.relforcerowsecurity AND r.rolname='knowledge_migrator') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace JOIN pg_roles r ON r.oid=c.relowner WHERE n.nspname='knowledge' AND c.relkind='r' AND c.relname<>'schema_version') AND current_user='knowledge_runtime' AND NOT EXISTS(SELECT FROM pg_roles WHERE rolname=current_user AND (rolsuper OR rolbypassrls))",Map.of(),Boolean.class)));}catch(KnowledgeFailure f){return false;}}
    void lockOwner(long owner){jdbc.update("INSERT INTO knowledge.owner_control(owner_id) VALUES(:owner) ON CONFLICT DO NOTHING",Map.of("owner",owner));jdbc.queryForObject("SELECT owner_id FROM knowledge.owner_control WHERE owner_id=:owner FOR UPDATE",Map.of("owner",owner),Long.class);}
    private Content content(ResultSet r)throws SQLException {UUID category=r.getObject("category_id",UUID.class);List<UUID> tags=jdbc.query("SELECT tag_id FROM knowledge.entry_tag WHERE owner_id=:owner AND entry_id=:id ORDER BY tag_id",params(r.getLong("owner_id"),r.getObject("id",UUID.class)),(a,n)->a.getObject(1,UUID.class));return new Content(Kind.valueOf(r.getString("kind")),r.getString("title"),r.getString("body"),r.getString("source_url"),category,tags);}
    private Entry map(ResultSet r,int n)throws SQLException{return new Entry(r.getObject("id",UUID.class),r.getLong("revision"),content(r),r.getObject("current_file_version",UUID.class),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant(),r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant());}
    Entry entry(long owner,UUID id,boolean deleted,boolean lock){var records=jdbc.query("SELECT * FROM knowledge.entry WHERE owner_id=:owner AND id=:id"+(deleted?"":" AND deleted_at IS NULL")+(lock?" FOR UPDATE":""),params(owner,id),this::map);if(records.isEmpty())throw new KnowledgeFailure(404,"条目不存在或不可访问");return records.get(0);}
    public Entry read(long owner,UUID id,boolean deleted){return tx(owner,()->entry(owner,id,deleted,false));}
    private void labels(long owner,Content content){
        if(content.categoryId()!=null){
            String kind=jdbc.query("SELECT kind FROM knowledge.label WHERE owner_id=:owner AND id=:id",params(owner,content.categoryId()),(r,n)->r.getString(1)).stream().findFirst().orElse("");
            if(!kind.equals("CATEGORY"))throw KnowledgeFailure.invalid("分类已不存在，请重新选择");
        }
        for(UUID id:content.tagIds()){
            boolean valid=jdbc.query("SELECT kind,parent_category_id FROM knowledge.label WHERE owner_id=:owner AND id=:id",params(owner,id),
                (r,n)->r.getString(1).equals("TAG")&&Objects.equals(r.getObject(2,UUID.class),content.categoryId())).stream().findFirst().orElse(false);
            if(!valid)throw KnowledgeFailure.invalid("标签不属于所选分类，请重新选择");
        }
    }
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
        if(action.equals("DELETE")){
            if(before.deletedAt()!=null)throw KnowledgeFailure.conflict();
            jdbc.update("UPDATE knowledge.entry SET deleted_at=now(),updated_at=now(),revision=revision+1 WHERE owner_id=:owner AND id=:id",p);
            jdbc.update("UPDATE knowledge.processing_job SET state='CANCELED',error_code='ENTRY_DELETED',fence=NULL,lease_until=NULL,retry_after=NULL,updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND state IN('QUEUED','RUNNING','RETRY_WAIT')",p);
        }
        else if(action.equals("RESTORE")){
            if(before.deletedAt()==null||before.deletedAt().isBefore(Instant.now().minusSeconds(30*86400L)))throw KnowledgeFailure.invalid("仅可恢复保留期内的回收站条目");
            jdbc.update("UPDATE knowledge.entry SET deleted_at=NULL,updated_at=now(),revision=revision+1 WHERE owner_id=:owner AND id=:id",p);
            if(before.fileVersion()!=null){
                var file=p.addValue("version",before.fileVersion());
                var canceled=jdbc.query("UPDATE knowledge.processing_job SET state='QUEUED',attempt=0,error_code=NULL,fence=NULL,lease_until=NULL,retry_after=NULL,updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND file_version=:version AND state='CANCELED' AND error_code='ENTRY_DELETED' RETURNING id",file,(r,n)->r.getObject(1,UUID.class));
                for(UUID job:canceled)jdbc.update("INSERT INTO knowledge.outbox(owner_id,id,job_id) VALUES(:owner,:event,:job)",file.addValue("event",UUID.randomUUID()).addValue("job",job));
            }
        }
        else{if(before.deletedAt()!=null||request.sourceRevision()==null)throw KnowledgeFailure.invalid("请选择有效历史版本");var records=jdbc.query("SELECT content FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id AND revision=:revision",p.addValue("revision",request.sourceRevision()),(r,n)->JSON.parseObject(r.getString(1),Content.class));if(records.isEmpty())throw KnowledgeFailure.invalid("历史版本不存在");Content c=records.get(0);c.validate();labels(owner,c);p.addValue("title",c.title()).addValue("body",c.body()).addValue("source",Objects.toString(c.sourceUrl(),"")).addValue("category",c.categoryId());jdbc.update("UPDATE knowledge.entry SET title=:title,body=:body,source_url=:source,category_id=:category,updated_at=now(),revision=revision+1 WHERE owner_id=:owner AND id=:id",p);jdbc.update("DELETE FROM knowledge.entry_tag WHERE owner_id=:owner AND entry_id=:id",p);for(UUID tag:c.tagIds())jdbc.update("INSERT INTO knowledge.entry_tag VALUES(:owner,:id,:tag)",params(owner,id).addValue("tag",tag));}
        return record(owner,request.mutationId(),digest,id,action);
    });}
    public Page<History> history(long owner,UUID id,int page){if(page<1||page>10000)throw KnowledgeFailure.invalid("页码无效");return tx(owner,()->{entry(owner,id,true,false);var p=params(owner,id).addValue("offset",(page-1)*20);long count=jdbc.queryForObject("SELECT count(*) FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id",p,Long.class);var values=jdbc.query("SELECT * FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id ORDER BY revision DESC LIMIT 20 OFFSET :offset",p,(r,n)->new History(r.getLong("revision"),JSON.parseObject(r.getString("content"),Content.class),r.getObject("current_file_version",UUID.class),r.getTimestamp("saved_at").toInstant(),r.getTimestamp("deleted_at")==null?null:r.getTimestamp("deleted_at").toInstant(),r.getString("action")));return new Page<>(values,count,page,20);});}
    private record Query(String where,MapSqlParameterSource params) {}
    private Query query(long owner,Filter filter){filter.validate();var p=new MapSqlParameterSource("owner",owner);String where="e.owner_id=:owner AND e.deleted_at IS "+(filter.deleted()?"NOT NULL":"NULL");if(filter.kind()!=null){where+=" AND e.kind=:kind";p.addValue("kind",filter.kind().name());}if(filter.categoryId()!=null){where+=" AND e.category_id=:category";p.addValue("category",filter.categoryId());}if(filter.tagId()!=null){where+=" AND EXISTS(SELECT FROM knowledge.entry_tag et WHERE et.owner_id=e.owner_id AND et.entry_id=e.id AND et.tag_id=:tag)";p.addValue("tag",filter.tagId());}if(filter.updatedFrom()!=null){where+=" AND e.updated_at>=:from";p.addValue("from",Timestamp.from(filter.updatedFrom()));}if(filter.updatedTo()!=null){where+=" AND e.updated_at<=:to";p.addValue("to",Timestamp.from(filter.updatedTo()));}
        if(filter.keyword()!=null){p.addValue("q",filter.keyword());where+=" AND (position(lower(:q) in lower(e.title))>0 OR position(lower(:q) in lower(e.body))>0 OR EXISTS(SELECT FROM knowledge.entry_tag et JOIN knowledge.label l ON l.owner_id=et.owner_id AND l.id=et.tag_id WHERE et.owner_id=e.owner_id AND et.entry_id=e.id AND position(lower(:q) in lower(l.name))>0) OR EXISTS(SELECT FROM knowledge.extracted_segment s JOIN knowledge.processing_job j ON j.owner_id=s.owner_id AND j.file_version=s.file_version AND j.state='READY' WHERE s.owner_id=e.owner_id AND s.entry_id=e.id AND s.file_version=e.current_file_version AND position(lower(:q) in lower(s.body))>0))";}return new Query(where,p);}
    public Page<Entry> list(long owner,Filter filter){return tx(owner,()->{var q=query(owner,filter);long total=jdbc.queryForObject("SELECT count(*) FROM knowledge.entry e WHERE "+q.where,q.params,Long.class);q.params.addValue("offset",(filter.number()-1)*filter.size()).addValue("limit",filter.size());String order=filter.keyword()==null?"":"CASE WHEN position(lower(:q) in lower(e.title))>0 THEN 0 WHEN EXISTS(SELECT FROM knowledge.entry_tag et JOIN knowledge.label l ON l.owner_id=et.owner_id AND l.id=et.tag_id WHERE et.owner_id=e.owner_id AND et.entry_id=e.id AND position(lower(:q) in lower(l.name))>0) THEN 1 ELSE 2 END,";var values=jdbc.query("SELECT e.owner_id,e.id,e.kind,e.title,CAST('' AS text) AS body,e.source_url,e.category_id,e.revision,e.current_file_version,e.created_at,e.updated_at,e.deleted_at FROM knowledge.entry e WHERE "+q.where+" ORDER BY "+order+"e.updated_at DESC,e.id LIMIT :limit OFFSET :offset",q.params,this::map);return new Page<>(values,total,filter.number(),filter.size());});}
    public List<Label> labels(long owner){return tx(owner,()->jdbc.query("SELECT * FROM knowledge.label WHERE owner_id=:owner ORDER BY kind,name,id",Map.of("owner",owner),(r,n)->new Label(r.getObject("id",UUID.class),r.getString("kind"),r.getString("name"),r.getObject("parent_category_id",UUID.class),r.getLong("revision"))));}
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
    public Label saveLabel(long owner,LabelWrite w){
        if(w==null||w.id()==null||!Set.of("CATEGORY","TAG").contains(Objects.toString(w.kind(),""))||w.name()==null||w.name().isBlank()||w.name().codePointCount(0,w.name().length())>80||w.name().indexOf('\0')>=0||w.expectedRevision()<0)throw KnowledgeFailure.invalid("分类标签需为1至80字且版本有效");
        if(w.kind().equals("TAG")&&w.parentCategoryId()==null||w.kind().equals("CATEGORY")&&w.parentCategoryId()!=null)throw KnowledgeFailure.invalid("标签必须选择一个所属分类");
        return tx(owner,()->{
            lockOwner(owner);
            if(w.kind().equals("TAG")){
                String parentKind=jdbc.query("SELECT kind FROM knowledge.label WHERE owner_id=:owner AND id=:id",params(owner,w.parentCategoryId()),(r,n)->r.getString(1)).stream().findFirst().orElse("");
                if(!parentKind.equals("CATEGORY"))throw KnowledgeFailure.invalid("所属分类不存在");
                long incompatible=jdbc.queryForObject("SELECT count(*) FROM knowledge.entry_tag et JOIN knowledge.entry e ON e.owner_id=et.owner_id AND e.id=et.entry_id WHERE et.owner_id=:owner AND et.tag_id=:id AND e.category_id IS DISTINCT FROM :parent",params(owner,w.id()).addValue("parent",w.parentCategoryId()),Long.class);
                if(incompatible>0)throw KnowledgeFailure.invalid("已有条目使用该标签，请先从相关条目移除标签再调整归属");
            }
            var p=params(owner,w.id()).addValue("kind",w.kind()).addValue("name",w.name().strip()).addValue("parent",w.parentCategoryId()).addValue("revision",w.expectedRevision());
            if(w.expectedRevision()==0){if(jdbc.update("INSERT INTO knowledge.label(owner_id,id,kind,name,parent_category_id,revision) VALUES(:owner,:id,:kind,:name,:parent,1) ON CONFLICT DO NOTHING",p)!=1)throw KnowledgeFailure.conflict();}
            else if(jdbc.update("UPDATE knowledge.label SET name=:name,parent_category_id=:parent,revision=revision+1 WHERE owner_id=:owner AND id=:id AND kind=:kind AND revision=:revision",p)!=1)throw KnowledgeFailure.conflict();
            return new Label(w.id(),w.kind(),w.name().strip(),w.parentCategoryId(),w.expectedRevision()+1);
        });
    }
    public void deleteLabel(long owner,UUID id,long revision){tx(owner,()->{lockOwner(owner);var p=params(owner,id).addValue("revision",revision);long count=jdbc.queryForObject("SELECT (SELECT count(*) FROM knowledge.entry WHERE owner_id=:owner AND category_id=:id)+(SELECT count(*) FROM knowledge.entry_tag WHERE owner_id=:owner AND tag_id=:id)+(SELECT count(*) FROM knowledge.label WHERE owner_id=:owner AND parent_category_id=:id)",p,Long.class);if(count>0)throw KnowledgeFailure.invalid("仍有条目或子标签使用该分类或标签，不能删除");if(jdbc.update("DELETE FROM knowledge.label WHERE owner_id=:owner AND id=:id AND revision=:revision",p)!=1)throw KnowledgeFailure.conflict();return null;});}
    /** Explicit owner action; private-file records remain until their objects can be safely reclaimed. */
    public PurgeResult purgeExpired(long owner){return tx(owner,()->{
        lockOwner(owner);
        var p=new MapSqlParameterSource("owner",owner);
        long blocked=jdbc.queryForObject("SELECT count(*) FROM knowledge.entry e WHERE e.owner_id=:owner AND e.deleted_at<now()-interval '30 days' AND (EXISTS(SELECT FROM knowledge.file_version f WHERE f.owner_id=e.owner_id AND f.entry_id=e.id AND f.state<>'CLEANED') OR EXISTS(SELECT FROM knowledge.image i WHERE i.owner_id=e.owner_id AND i.entry_id=e.id AND i.state<>'CLEANED'))",p,Long.class);
        List<UUID> ids=jdbc.query("SELECT e.id FROM knowledge.entry e WHERE e.owner_id=:owner AND e.deleted_at<now()-interval '30 days' AND NOT EXISTS(SELECT FROM knowledge.file_version f WHERE f.owner_id=e.owner_id AND f.entry_id=e.id AND f.state<>'CLEANED') AND NOT EXISTS(SELECT FROM knowledge.image i WHERE i.owner_id=e.owner_id AND i.entry_id=e.id AND i.state<>'CLEANED') ORDER BY e.deleted_at,e.id LIMIT 50 FOR UPDATE OF e SKIP LOCKED",p,(r,n)->r.getObject(1,UUID.class));
        for(UUID id:ids){
            var item=params(owner,id).addValue("entryKey",id.toString());
            jdbc.update("DELETE FROM knowledge.relation WHERE owner_id=:owner AND ((from_kind='ENTRY' AND from_id=:entryKey) OR (to_kind='ENTRY' AND to_id=:entryKey))",item);
            jdbc.update("DELETE FROM knowledge.outbox WHERE owner_id=:owner AND job_id IN(SELECT id FROM knowledge.processing_job WHERE owner_id=:owner AND entry_id=:id)",item);
            jdbc.update("DELETE FROM knowledge.processing_job WHERE owner_id=:owner AND entry_id=:id",item);
            jdbc.update("DELETE FROM knowledge.extracted_segment WHERE owner_id=:owner AND entry_id=:id",item);
            jdbc.update("UPDATE knowledge.entry SET current_file_version=NULL WHERE owner_id=:owner AND id=:id",item);
            jdbc.update("DELETE FROM knowledge.file_version WHERE owner_id=:owner AND entry_id=:id AND state='CLEANED'",item);
            jdbc.update("DELETE FROM knowledge.image WHERE owner_id=:owner AND entry_id=:id AND state='CLEANED'",item);
            jdbc.update("DELETE FROM knowledge.entry_tag WHERE owner_id=:owner AND entry_id=:id",item);
            jdbc.update("DELETE FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id",item);
            jdbc.update("DELETE FROM knowledge.mutation WHERE owner_id=:owner AND result->>'id'=:entryKey",item);
            jdbc.update("DELETE FROM knowledge.entry WHERE owner_id=:owner AND id=:id AND deleted_at<now()-interval '30 days'",item);
        }
        return new PurgeResult(ids.size(),blocked);
    });}
    /** OSS transfer happens before this short PG transaction; a rejected commit must delete the new object. */
    public FileCommit attachFile(long owner,UUID id,long revision,UUID mutation,UUID version,String objectKey,String filename,String mime,long size,String digest,long quota){
        if(mutation==null||version==null||revision<1||size<1||quota<1)throw KnowledgeFailure.invalid("文件版本请求无效");
        String requestHash=hash(List.of(id,revision,filename,mime,size,digest));
        return tx(owner,()->{
            lockOwner(owner);
            Ack prior=previous(owner,mutation,requestHash);
            if(prior!=null)return new FileCommit(prior,false,null);
            Entry entry=entry(owner,id,false,true);
            if(entry.content().kind()!=Kind.DOCUMENT)throw KnowledgeFailure.invalid("请先创建文件资料条目");
            if(entry.revision()!=revision)throw KnowledgeFailure.conflict();
            long used=jdbc.queryForObject("SELECT (SELECT coalesce(sum(size_bytes),0) FROM knowledge.file_version WHERE owner_id=:owner AND state<>'CLEANED')+(SELECT coalesce(sum(size_bytes),0) FROM knowledge.image WHERE owner_id=:owner AND state<>'CLEANED')",Map.of("owner",owner),Long.class);
            if(size>quota-used)throw KnowledgeFailure.invalid("知识库文件空间不足，保留版本也计入配额");
            var p=params(owner,id).addValue("version",version).addValue("next",revision+1).addValue("objectKey",objectKey)
                .addValue("filename",filename).addValue("mime",mime).addValue("size",size).addValue("digest",digest);
            jdbc.update("INSERT INTO knowledge.file_version(owner_id,id,entry_id,entry_revision,object_key,filename,mime,sha256,size_bytes,state) VALUES(:owner,:version,:id,:next,:objectKey,:filename,:mime,:digest,:size,'CLAIMED')",p);
            jdbc.update("UPDATE knowledge.entry SET current_file_version=:version,revision=revision+1,updated_at=now() WHERE owner_id=:owner AND id=:id",p);
            Ack ack=record(owner,mutation,requestHash,id,"FILE_ATTACH");
            UUID job=UUID.randomUUID();
            p.addValue("job",job).addValue("event",UUID.randomUUID());
            jdbc.update("INSERT INTO knowledge.processing_job(owner_id,id,entry_id,file_version,state) VALUES(:owner,:job,:id,:version,'QUEUED')",p);
            jdbc.update("INSERT INTO knowledge.outbox(owner_id,id,job_id) VALUES(:owner,:event,:job)",p);
            return new FileCommit(ack,true,version);
        });
    }
    public FileInfo file(long owner,UUID id,UUID version){return tx(owner,()->{
        Entry entry=entry(owner,id,false,false);
        if(!version.equals(entry.fileVersion()))throw new KnowledgeFailure(404,"文件版本不可访问");
        var p=params(owner,id).addValue("version",version);
        var rows=jdbc.query("SELECT f.filename,f.mime,f.size_bytes,f.created_at,j.state,j.error_code FROM knowledge.file_version f LEFT JOIN knowledge.processing_job j ON j.owner_id=f.owner_id AND j.file_version=f.id WHERE f.owner_id=:owner AND f.entry_id=:id AND f.id=:version AND f.state='CLAIMED' ORDER BY j.created_at DESC LIMIT 1",p,(r,n)->new FileInfo(version,r.getString(1),r.getString(2),r.getLong(3),ProcessingState.valueOf(Objects.toString(r.getString(5),"EMPTY")),r.getString(6),r.getTimestamp(4).toInstant()));
        if(rows.isEmpty())throw new KnowledgeFailure(404,"文件版本不可访问");
        return rows.get(0);
    });}
    public Page<Segment> segments(long owner,UUID entryId,UUID version,int page){
        if(page<1||page>100)throw KnowledgeFailure.invalid("页码无效");
        return tx(owner,()->{
            if(file(owner,entryId,version).state()!=ProcessingState.READY)
                throw new KnowledgeFailure(409,"当前文件尚无完整可查看的提取结果");
            var p=params(owner,entryId).addValue("version",version).addValue("offset",(page-1)*20);
            long total=jdbc.queryForObject("SELECT count(*) FROM knowledge.extracted_segment WHERE owner_id=:owner AND entry_id=:id AND file_version=:version",p,Long.class);
            List<Segment> rows=jdbc.query("SELECT ordinal,page,from_line,to_line,body FROM knowledge.extracted_segment WHERE owner_id=:owner AND entry_id=:id AND file_version=:version ORDER BY ordinal LIMIT 20 OFFSET :offset",p,
                (r,n)->new Segment(r.getInt(1),r.getObject(2,Integer.class),r.getObject(3,Integer.class),r.getObject(4,Integer.class),r.getString(5)));
            return new Page<>(rows,total,page,20);
        });
    }
    String fileKey(long owner,UUID id,UUID version){return tx(owner,()->{
        Entry entry=entry(owner,id,false,false);
        if(!version.equals(entry.fileVersion()))throw new KnowledgeFailure(404,"文件版本不可访问");
        var rows=jdbc.query("SELECT object_key FROM knowledge.file_version WHERE owner_id=:owner AND entry_id=:id AND id=:version AND state='CLAIMED'",params(owner,id).addValue("version",version),(r,n)->r.getString(1));
        if(rows.isEmpty())throw new KnowledgeFailure(404,"文件版本不可访问");
        return rows.get(0);
    });}
    public FileInfo changeFileJob(long owner,UUID entryId,UUID version,boolean retry){tx(owner,()->{
        lockOwner(owner);Entry entry=entry(owner,entryId,false,true);
        if(!version.equals(entry.fileVersion()))throw new KnowledgeFailure(404,"文件版本不可访问");
        var p=params(owner,entryId).addValue("version",version).addValue("event",UUID.randomUUID());
        String state=jdbc.query("SELECT state FROM knowledge.processing_job WHERE owner_id=:owner AND entry_id=:id AND file_version=:version FOR UPDATE",p,(r,n)->r.getString(1)).stream().findFirst().orElseThrow(()->new KnowledgeFailure(404,"解析任务不存在"));
        if(retry){
            if(!Set.of("FAILED","NEEDS_OCR","CANCELED").contains(state))throw KnowledgeFailure.conflict();
            jdbc.update("UPDATE knowledge.processing_job SET state='QUEUED',attempt=0,error_code=NULL,retry_after=NULL,lease_until=NULL,fence=NULL,updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND file_version=:version",p);
            jdbc.update("INSERT INTO knowledge.outbox(owner_id,id,job_id) SELECT :owner,:event,id FROM knowledge.processing_job WHERE owner_id=:owner AND entry_id=:id AND file_version=:version",p);
        }else{
            if(!Set.of("QUEUED","RUNNING","RETRY_WAIT").contains(state))throw KnowledgeFailure.conflict();
            jdbc.update("UPDATE knowledge.processing_job SET state='CANCELED',fence=NULL,lease_until=NULL,retry_after=NULL,updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND file_version=:version",p);
        }
        return null;
    });return file(owner,entryId,version);}
    record JobTicket(UUID jobId,UUID entryId,UUID version,UUID fence,String objectKey,String mime,long size) {}
    record OutboxMessage(UUID id,UUID jobId) {}
    record FileCleanup(UUID version,String objectKey) {}
    record ImageCommit(UUID id,boolean accepted) {}
    record ImageInfo(UUID entryId,String objectKey,String mime,long sizeBytes) {}
    record ImageCleanup(UUID id,String objectKey) {}
    List<ImageCleanup> staleImages(long owner){return tx(owner,()->jdbc.query("""
        SELECT i.id,i.object_key FROM knowledge.image i
          JOIN knowledge.entry e ON e.owner_id=i.owner_id AND e.id=i.entry_id
         WHERE i.owner_id=:owner AND i.state IN('ACTIVE','CLEANUP_PENDING')
           AND i.created_at<now()-interval '1 day'
           AND (e.deleted_at<now()-interval '30 days' OR
                (e.deleted_at IS NULL
                 AND position('knowledge-image:'||i.id::text in e.body)=0
                 AND NOT EXISTS(SELECT FROM knowledge.entry_revision r
                                  WHERE r.owner_id=i.owner_id AND r.entry_id=i.entry_id
                                    AND position('knowledge-image:'||i.id::text in r.content->>'body')>0)))
         ORDER BY i.created_at,i.id LIMIT 10
        """,Map.of("owner",owner),(r,n)->new ImageCleanup(r.getObject(1,UUID.class),r.getString(2))));}
    boolean markImageCleanupPending(long owner,UUID id){return tx(owner,()->jdbc.update("""
        UPDATE knowledge.image i SET state='CLEANUP_PENDING'
         FROM knowledge.entry e
         WHERE i.owner_id=:owner AND i.id=:id AND i.state IN('ACTIVE','CLEANUP_PENDING')
           AND e.owner_id=i.owner_id AND e.id=i.entry_id
           AND i.created_at<now()-interval '1 day'
           AND (e.deleted_at<now()-interval '30 days' OR
                (e.deleted_at IS NULL
                 AND position('knowledge-image:'||i.id::text in e.body)=0
                 AND NOT EXISTS(SELECT FROM knowledge.entry_revision r
                                  WHERE r.owner_id=i.owner_id AND r.entry_id=i.entry_id
                                    AND position('knowledge-image:'||i.id::text in r.content->>'body')>0)))
        """,params(owner,id))==1);}
    void markImageCleaned(long owner,UUID id){tx(owner,()->{jdbc.update("UPDATE knowledge.image SET state='CLEANED' WHERE owner_id=:owner AND id=:id AND state='CLEANUP_PENDING'",params(owner,id));return null;});}
    ImageCommit attachImage(long owner,UUID entryId,UUID mutationId,UUID imageId,String objectKey,
            String mime,long size,int width,int height,String sha256,long quota){
        if(mutationId==null||imageId==null||size<1||size>quota)throw KnowledgeFailure.invalid("图片请求无效");
        String digest=hash(List.of(entryId,mime,size,width,height,sha256));
        return tx(owner,()->{
            lockOwner(owner);
            var old=jdbc.query("SELECT id,request_hash FROM knowledge.image WHERE owner_id=:owner AND mutation_id=:mutation",
                new MapSqlParameterSource("owner",owner).addValue("mutation",mutationId),
                (r,n)->new String[]{r.getString(1),r.getString(2)});
            if(!old.isEmpty()){
                if(!digest.equals(old.get(0)[1]))throw KnowledgeFailure.conflict();
                return new ImageCommit(UUID.fromString(old.get(0)[0]),false);
            }
            entry(owner,entryId,false,true);
            long used=jdbc.queryForObject("SELECT (SELECT coalesce(sum(size_bytes),0) FROM knowledge.file_version WHERE owner_id=:owner AND state<>'CLEANED')+(SELECT coalesce(sum(size_bytes),0) FROM knowledge.image WHERE owner_id=:owner AND state<>'CLEANED')",Map.of("owner",owner),Long.class);
            if(size>quota-used)throw KnowledgeFailure.invalid("知识库文件空间不足");
            var p=params(owner,imageId).addValue("entry",entryId).addValue("mutation",mutationId).addValue("digest",digest)
                .addValue("key",objectKey).addValue("mime",mime).addValue("size",size).addValue("width",width).addValue("height",height);
            jdbc.update("INSERT INTO knowledge.image(owner_id,id,entry_id,mutation_id,request_hash,object_key,mime,size_bytes,width,height,state) VALUES(:owner,:id,:entry,:mutation,:digest,:key,:mime,:size,:width,:height,'ACTIVE')",p);
            return new ImageCommit(imageId,true);
        });
    }
    ImageInfo image(long owner,UUID imageId,boolean allowUnreferenced){return tx(owner,()->{
        var p=params(owner,imageId);
        var rows=jdbc.query("SELECT i.entry_id,i.object_key,i.mime,i.size_bytes,e.body FROM knowledge.image i JOIN knowledge.entry e ON e.owner_id=i.owner_id AND e.id=i.entry_id WHERE i.owner_id=:owner AND i.id=:id AND i.state='ACTIVE' AND e.deleted_at IS NULL",p,
            (r,n)->new Object[]{r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getLong(4),r.getString(5)});
        if(rows.isEmpty())throw new KnowledgeFailure(404,"图片不存在或不可访问");
        Object[] row=rows.get(0);UUID entryId=(UUID)row[0];
        if(!allowUnreferenced&&!((String)row[4]).contains("knowledge-image:"+imageId)){
            boolean historical=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:entry AND position(:reference in content->>'body')>0)",p.addValue("entry",entryId).addValue("reference","knowledge-image:"+imageId),Boolean.class));
            if(!historical)throw new KnowledgeFailure(404,"图片不存在或不可访问");
        }
        return new ImageInfo(entryId,(String)row[1],(String)row[2],(long)row[3]);
    });}
    boolean registeredObjectKey(long owner,String objectKey){return tx(owner,()->Boolean.TRUE.equals(jdbc.queryForObject(
        "SELECT EXISTS(SELECT FROM knowledge.file_version WHERE owner_id=:owner AND object_key=:key AND state<>'CLEANED') OR EXISTS(SELECT FROM knowledge.image WHERE owner_id=:owner AND object_key=:key AND state<>'CLEANED')",
        Map.of("owner",owner,"key",objectKey),Boolean.class)));}
    List<FileCleanup> staleFiles(long owner){return tx(owner,()->jdbc.query("SELECT f.id,f.object_key FROM knowledge.file_version f JOIN knowledge.entry e ON e.owner_id=f.owner_id AND e.id=f.entry_id WHERE f.owner_id=:owner AND f.state IN('CLAIMED','CLEANUP_PENDING') AND f.id IS DISTINCT FROM e.current_file_version AND f.created_at<now()-interval '30 days' OR f.owner_id=:owner AND f.state IN('CLAIMED','CLEANUP_PENDING') AND e.deleted_at<now()-interval '30 days' ORDER BY f.created_at LIMIT 10",Map.of("owner",owner),(r,n)->new FileCleanup(r.getObject(1,UUID.class),r.getString(2))));}
    void markCleanupPending(long owner,UUID version){tx(owner,()->{jdbc.update("UPDATE knowledge.file_version SET state='CLEANUP_PENDING' WHERE owner_id=:owner AND id=:id AND state='CLAIMED'",params(owner,version));return null;});}
    void markCleaned(long owner,UUID version){tx(owner,()->{jdbc.update("UPDATE knowledge.file_version SET state='CLEANED' WHERE owner_id=:owner AND id=:id AND state='CLEANUP_PENDING'",params(owner,version));return null;});}
    List<OutboxMessage> pendingOutbox(long owner){return tx(owner,()->jdbc.query("SELECT o.id,o.job_id FROM knowledge.outbox o JOIN knowledge.processing_job j ON j.owner_id=o.owner_id AND j.id=o.job_id WHERE o.owner_id=:owner AND o.confirmed_at IS NULL AND o.available_at<=now() AND j.state IN('QUEUED','RETRY_WAIT') ORDER BY o.available_at,o.id LIMIT 10",Map.of("owner",owner),(r,n)->new OutboxMessage(r.getObject(1,UUID.class),r.getObject(2,UUID.class))));}
    void confirmOutbox(long owner,UUID id){tx(owner,()->{jdbc.update("UPDATE knowledge.outbox SET confirmed_at=now() WHERE owner_id=:owner AND id=:id AND confirmed_at IS NULL",params(owner,id));return null;});}
    JobTicket claimJob(long owner,UUID jobId){return tx(owner,()->{
        var p=params(owner,jobId);
        var rows=jdbc.query("SELECT j.entry_id,j.file_version,j.state,j.retry_after,f.object_key,f.mime,f.size_bytes,e.current_file_version,e.deleted_at FROM knowledge.processing_job j JOIN knowledge.file_version f ON f.owner_id=j.owner_id AND f.id=j.file_version JOIN knowledge.entry e ON e.owner_id=j.owner_id AND e.id=j.entry_id WHERE j.owner_id=:owner AND j.id=:id FOR UPDATE OF j",p,(r,n)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getTimestamp(4),r.getString(5),r.getString(6),r.getLong(7),r.getObject(8,UUID.class),r.getTimestamp(9)});
        if(rows.isEmpty())return null;
        Object[] row=rows.get(0);UUID entry=(UUID)row[0],version=(UUID)row[1];String state=(String)row[2];Timestamp retry=(Timestamp)row[3];
        if(!version.equals(row[7])||row[8]!=null){jdbc.update("UPDATE knowledge.processing_job SET state='CANCELED',fence=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id AND state<>'READY'",p);return null;}
        if(!Set.of("QUEUED","RETRY_WAIT").contains(state)||retry!=null&&retry.toInstant().isAfter(Instant.now()))return null;
        UUID fence=UUID.randomUUID();p.addValue("fence",fence);
        jdbc.update("UPDATE knowledge.processing_job SET state='RUNNING',attempt=attempt+1,fence=:fence,lease_until=now()+interval '3 minutes',updated_at=now() WHERE owner_id=:owner AND id=:id",p);
        return new JobTicket(jobId,entry,version,fence,(String)row[4],(String)row[5],(long)row[6]);
    });}
    ProcessingState jobState(long owner,UUID jobId){return tx(owner,()->{
        var states=jdbc.query("SELECT state FROM knowledge.processing_job WHERE owner_id=:owner AND id=:id",params(owner,jobId),(r,n)->ProcessingState.valueOf(r.getString(1)));
        return states.isEmpty()?null:states.get(0);
    });}
    void finishJob(long owner,JobTicket ticket,KnowledgeParseRunner.Outcome outcome){tx(owner,()->{
        var p=params(owner,ticket.jobId()).addValue("entry",ticket.entryId()).addValue("version",ticket.version()).addValue("fence",ticket.fence());
        var live=jdbc.query("SELECT j.attempt,e.current_file_version,e.deleted_at FROM knowledge.processing_job j JOIN knowledge.entry e ON e.owner_id=j.owner_id AND e.id=j.entry_id WHERE j.owner_id=:owner AND j.id=:id AND j.fence=:fence AND j.state='RUNNING' FOR UPDATE OF j",p,(r,n)->new Object[]{r.getInt(1),r.getObject(2,UUID.class),r.getTimestamp(3)});
        if(live.isEmpty())return null;
        int attempt=(int)live.get(0)[0];
        if(!ticket.version().equals(live.get(0)[1])||live.get(0)[2]!=null){jdbc.update("UPDATE knowledge.processing_job SET state='CANCELED',fence=NULL,lease_until=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id",p);return null;}
        if(outcome.failure()==null){
            jdbc.update("DELETE FROM knowledge.extracted_segment WHERE owner_id=:owner AND file_version=:version",p);
            for(var segment:outcome.segments()){
                var part=params(owner,ticket.entryId()).addValue("version",ticket.version()).addValue("ordinal",segment.ordinal()).addValue("page",segment.page()).addValue("first",segment.fromLine()).addValue("last",segment.toLine()).addValue("body",segment.text());
                jdbc.update("INSERT INTO knowledge.extracted_segment(owner_id,entry_id,file_version,ordinal,page,from_line,to_line,body) VALUES(:owner,:id,:version,:ordinal,:page,:first,:last,:body)",part);
            }
            jdbc.update("UPDATE knowledge.processing_job SET state='READY',error_code=NULL,fence=NULL,lease_until=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id",p);
        }else{
            boolean retry=outcome.failure().retryable()&&attempt<3;
            p.addValue("state",retry?"RETRY_WAIT":outcome.failure().code().equals("NEEDS_OCR")?"NEEDS_OCR":"FAILED").addValue("error",outcome.failure().code()).addValue("retry",retry?Timestamp.from(Instant.now().plusSeconds(30L*attempt)):null);
            jdbc.update("UPDATE knowledge.processing_job SET state=:state,error_code=:error,retry_after=:retry,fence=NULL,lease_until=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id",p);
            if(retry)jdbc.update("INSERT INTO knowledge.outbox(owner_id,id,job_id,available_at) VALUES(:owner,:event,:id,:retry)",p.addValue("event",UUID.randomUUID()));
        }
        return null;
    });}
    void recoverExpiredJobs(long owner){tx(owner,()->{
        var p=new MapSqlParameterSource("owner",owner);
        var jobs=jdbc.query("SELECT id,attempt FROM knowledge.processing_job WHERE owner_id=:owner AND state='RUNNING' AND lease_until<now() ORDER BY lease_until LIMIT 10 FOR UPDATE SKIP LOCKED",p,(r,n)->new Object[]{r.getObject(1,UUID.class),r.getInt(2)});
        for(Object[] row:jobs){UUID id=(UUID)row[0];int attempt=(int)row[1];var q=params(owner,id).addValue("event",UUID.randomUUID());
            if(attempt>=3)jdbc.update("UPDATE knowledge.processing_job SET state='FAILED',error_code='LEASE_EXPIRED',fence=NULL,lease_until=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id",q);
            else{jdbc.update("UPDATE knowledge.processing_job SET state='RETRY_WAIT',error_code='LEASE_EXPIRED',retry_after=now()+interval '30 seconds',fence=NULL,lease_until=NULL,updated_at=now() WHERE owner_id=:owner AND id=:id",q);jdbc.update("INSERT INTO knowledge.outbox(owner_id,id,job_id,available_at) VALUES(:owner,:event,:id,now()+interval '30 seconds')",q);}
        }
        return null;
    });}
}
