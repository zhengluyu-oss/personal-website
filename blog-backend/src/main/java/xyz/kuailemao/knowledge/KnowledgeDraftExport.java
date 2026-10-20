package xyz.kuailemao.knowledge;

import com.alibaba.fastjson2.JSON;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.dto.ArticleDTO;
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.utils.SecurityUtils;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** PostgreSQL source snapshot and MySQL draft are reconciled by a stable export ID. */
@Service
@ConditionalOnProperty(name="knowledge.enabled",havingValue="true")
public final class KnowledgeDraftExport {
    public record Source(UUID entryId,long revision,UUID fileVersion,Integer segmentOrdinal) {}
    public record Request(UUID exportId,String title,String body,long categoryId,List<Long> tagIds,String coverUrl,List<Source> sources) {
        void validate() {
            if (exportId==null || title==null || title.isBlank() || title.codePointCount(0,title.length())>200
                    || body==null || body.isBlank() || body.getBytes(StandardCharsets.UTF_8).length>2097152
                    || categoryId<=0 || tagIds==null || tagIds.isEmpty() || tagIds.size()>20
                    || tagIds.stream().anyMatch(id->id==null||id<=0) || new HashSet<>(tagIds).size()!=tagIds.size()
                    || coverUrl==null || coverUrl.isBlank() || coverUrl.length()>2048
                    || sources==null || sources.isEmpty() || sources.size()>10 || new HashSet<>(sources).size()!=sources.size())
                throw KnowledgeFailure.invalid("草稿标题、正文、分类、标签、封面或来源无效");
            for(Source source:sources) if(source==null || source.entryId()==null || source.revision()<1
                    || source.segmentOrdinal()!=null && (source.fileVersion()==null || source.segmentOrdinal()<0))
                throw KnowledgeFailure.invalid("来源版本或片段无效");
            // A generated article must never accidentally contain a private object pointer or bearer token.
            String lower=body.toLowerCase(Locale.ROOT);
            if(lower.contains("/knowledge/view/files/") || lower.contains("oss://")
                    || lower.contains("authorization:") || lower.contains("bearer "))
                throw KnowledgeFailure.invalid("草稿正文包含私有地址或凭据，请先移除");
        }
    }
    public record SnapshotSource(Source source,String title,String kind,String text) {}
    public record Snapshot(List<SnapshotSource> sources) {}
    public record Result(UUID exportId,String state,Long articleId,Snapshot snapshot) {}

    private final KnowledgeStore store;
    private final KnowledgeArticleDraftWriter writer;
    private final SiteModuleAccessService modules;

    public KnowledgeDraftExport(KnowledgeStore store,KnowledgeArticleDraftWriter writer,SiteModuleAccessService modules) {
        this.store=store;this.writer=writer;this.modules=modules;
    }

    private void articlePermission() {
        if (!modules.canView("blog") || !SecurityUtils.getUserRoles().contains("blog:publish:article"))
            throw new KnowledgeFailure(403,"没有创建博客草稿的权限");
    }

    public Result read(long owner,UUID id) {
        if(id==null)throw KnowledgeFailure.invalid("导出标识无效");
        return store.tx(owner,()->store.jdbc.query("SELECT * FROM knowledge.article_export WHERE owner_id=:owner AND id=:id",
                KnowledgeStore.params(owner,id),(r,n)->new Result(id,r.getString("state"),r.getObject("article_id",Long.class),
                        JSON.parseObject(r.getString("snapshot"),Snapshot.class)))
                .stream().findFirst().orElseThrow(()->new KnowledgeFailure(404,"导出记录不存在")));
    }

    private SnapshotSource source(long owner,Source ref) {
        var p=KnowledgeStore.params(owner,ref.entryId()).addValue("revision",ref.revision());
        var entries=store.jdbc.query("SELECT kind,title,current_file_version FROM knowledge.entry WHERE owner_id=:owner AND id=:id AND deleted_at IS NULL",p,
                (r,n)->new Object[]{r.getString("kind"),r.getString("title"),r.getObject("current_file_version",UUID.class)});
        if(entries.isEmpty())throw KnowledgeFailure.invalid("来源条目已删除或不可访问");
        Object[] entry=entries.get(0);
        String text;
        if(ref.segmentOrdinal()==null) {
            if(ref.fileVersion()!=null)throw KnowledgeFailure.invalid("片段来源需指定段落位置");
            var versions=store.jdbc.query("SELECT content FROM knowledge.entry_revision WHERE owner_id=:owner AND entry_id=:id AND revision=:revision",p,
                    (r,n)->JSON.parseObject(r.getString(1),KnowledgeModels.Content.class));
            if(versions.isEmpty())throw KnowledgeFailure.invalid("来源历史版本不存在");
            text=versions.get(0).body();
        } else {
            if(!Objects.equals(entry[2],ref.fileVersion()))throw KnowledgeFailure.invalid("资料版本已经变化，请重新选择片段");
            p.addValue("file",ref.fileVersion()).addValue("ordinal",ref.segmentOrdinal());
            text=store.jdbc.query("SELECT s.body FROM knowledge.extracted_segment s JOIN knowledge.processing_job j ON j.owner_id=s.owner_id AND j.file_version=s.file_version AND j.state='READY' WHERE s.owner_id=:owner AND s.entry_id=:id AND s.file_version=:file AND s.ordinal=:ordinal",
                    p,(r,n)->r.getString(1)).stream().findFirst().orElseThrow(()->KnowledgeFailure.invalid("来源片段尚未可检索"));
        }
        return new SnapshotSource(ref,(String)entry[1],(String)entry[0],text);
    }

    public Result prepare(long owner,Request request) {
        request.validate();articlePermission();
        String digest=KnowledgeStore.hash(request);
        return store.tx(owner,()->{
            store.lockOwner(owner);
            var p=KnowledgeStore.params(owner,request.exportId());
            var existing=store.jdbc.query("SELECT request_hash,state,article_id,snapshot FROM knowledge.article_export WHERE owner_id=:owner AND id=:id",p,
                    (r,n)->new Object[]{r.getString(1),r.getString(2),r.getObject(3,Long.class),JSON.parseObject(r.getString(4),Snapshot.class)});
            if(!existing.isEmpty()){
                Object[] old=existing.get(0);
                if(!digest.equals(old[0]))throw KnowledgeFailure.conflict();
                return new Result(request.exportId(),(String)old[1],(Long)old[2],(Snapshot)old[3]);
            }
            Snapshot snapshot=new Snapshot(request.sources().stream().map(ref->source(owner,ref)).toList());
            if(JSON.toJSONBytes(snapshot).length>4*1024*1024)
                throw KnowledgeFailure.invalid("来源快照超过4MiB，请减少选择或缩短资料");
            p.addValue("digest",digest).addValue("snapshot",JSON.toJSONString(snapshot));
            store.jdbc.update("INSERT INTO knowledge.article_export(owner_id,id,request_hash,snapshot,state) VALUES(:owner,:id,:digest,CAST(:snapshot AS jsonb),'PREPARED')",p);
            return new Result(request.exportId(),"PREPARED",null,snapshot);
        });
    }

    private void mark(long owner,UUID id,String state,String code) {
        try {
            store.tx(owner,()->{
                store.jdbc.update("UPDATE knowledge.article_export SET state=:state,error_code=:code,updated_at=now() WHERE owner_id=:owner AND id=:id AND state<>'CREATED'",
                        KnowledgeStore.params(owner,id).addValue("state",state).addValue("code",code));
                return null;
            });
        } catch(RuntimeException ignored) { /* The original failure remains authoritative; retry can reconcile later. */ }
    }

    public Result create(long owner,Request request) {
        prepare(owner,request);
        articlePermission();
        ArticleDTO draft=new ArticleDTO();
        draft.setCategoryId(request.categoryId());draft.setTagId(request.tagIds());draft.setArticleCover(request.coverUrl());
        draft.setArticleTitle(request.title().strip());draft.setArticleContent(request.body());
        draft.setArticleType(1);draft.setIsTop(0);draft.setStatus(3);
        long articleId;
        try { articleId=writer.create(owner,request.exportId(),KnowledgeStore.hash(request),draft); }
        catch(KnowledgeFailure failure){
            mark(owner,request.exportId(),failure.status()==410?"DELETED":failure.status()==503?"RETRYABLE":"REJECTED","ARTICLE_"+failure.status());
            throw failure;
        }
        catch(RuntimeException failure){mark(owner,request.exportId(),"RETRYABLE","ARTICLE_UNAVAILABLE");throw KnowledgeFailure.unavailable();}
        try {
            return store.tx(owner,()->{
                var p=KnowledgeStore.params(owner,request.exportId()).addValue("article",articleId).addValue("digest",KnowledgeStore.hash(request));
                if(store.jdbc.update("UPDATE knowledge.article_export SET state='CREATED',article_id=:article,updated_at=now(),error_code=NULL WHERE owner_id=:owner AND id=:id AND request_hash=:digest",p)!=1)
                    throw KnowledgeFailure.conflict();
                return read(owner,request.exportId());
            });
        } catch(RuntimeException reconciliationPending) {
            throw new KnowledgeFailure(503,"草稿可能已创建但结果未确认；保留同一导出标识重试，系统会查找原草稿");
        }
    }
}
