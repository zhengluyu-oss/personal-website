package xyz.luyu.knowledge;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

public final class KnowledgeModels {
    private KnowledgeModels() {}
    public enum Kind { NOTE,DOCUMENT,CONCEPT }
    public enum ProcessingState { EMPTY,QUEUED,RUNNING,READY,FAILED,NEEDS_OCR,CANCELED,RETRY_WAIT }
    public enum ExportState { PREPARED,CREATED,RETRYABLE,REJECTED,DELETED }
    public enum RelationType { EXPLAINS,DEPENDS_ON,REFERENCES,RELATED_TO }
    public record Content(Kind kind,String title,String body,String sourceUrl,UUID categoryId,List<UUID> tagIds) {
        public void validate(){
            if(kind==null||title==null||title.isBlank()||title.codePointCount(0,title.length())>200||title.indexOf('\0')>=0)throw KnowledgeFailure.invalid("标题需为1至200字，并选择条目类型");
            if(body==null||body.indexOf('\0')>=0||body.getBytes(StandardCharsets.UTF_8).length>2097152)throw KnowledgeFailure.invalid("正文不能超过2MiB或含无效字符");
            if(tagIds==null||tagIds.size()>20||tagIds.stream().anyMatch(Objects::isNull)||new HashSet<>(tagIds).size()!=tagIds.size()||categoryId!=null&&tagIds.contains(categoryId))throw KnowledgeFailure.invalid("最多选择20个不重复标签");
            if(sourceUrl!=null&&!sourceUrl.isBlank())try{URI uri=URI.create(sourceUrl);if(sourceUrl.length()>2000||!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException();}catch(RuntimeException e){throw KnowledgeFailure.invalid("来源链接需为有效HTTP或HTTPS地址；系统不会抓取该链接");}
        }
    }
    public record Write(UUID mutationId,UUID entryId,long expectedRevision,Content content) {
        public void validate(){if(mutationId==null||entryId==null||expectedRevision<0||content==null)throw KnowledgeFailure.invalid("缺少保存标识或版本");content.validate();}
    }
    public record Action(UUID mutationId,long expectedRevision,Long sourceRevision) {}
    public record Ack(UUID id,long revision,Instant savedAt,boolean deleted) {}
    public record Entry(UUID id,long revision,Content content,UUID fileVersion,Instant createdAt,Instant updatedAt,Instant deletedAt) {}
    public record History(long revision,Content content,UUID fileVersion,Instant savedAt,Instant deletedAt,String action) {}
    public record Page<T>(List<T> records,long total,int page,int pageSize) {}
    public record Filter(Kind kind,UUID categoryId,UUID tagId,Instant updatedFrom,Instant updatedTo,String keyword,Integer page,Integer pageSize,boolean deleted) {
        public int number(){return page==null?1:page;}public int size(){return pageSize==null?20:pageSize;}
        public void validate(){if(number()<1||number()>10000||size()<1||size()>50||keyword!=null&&(keyword.isBlank()||keyword.codePointCount(0,keyword.length())>100)||updatedFrom!=null&&updatedTo!=null&&updatedFrom.isAfter(updatedTo))throw KnowledgeFailure.invalid("筛选条件无效，关键词1至100字，每页最多50条");}
    }
    public record Label(UUID id,String kind,String name,long revision) {}
    public record LabelWrite(UUID id,String kind,String name,long expectedRevision) {}
    public record Capabilities(boolean allowed,boolean available,boolean canManage,boolean filesAvailable,int maxTitleBytes,long maxBodyBytes,long maxPdfBytes,long maxTextBytes,long quotaBytes,String message) {}
    public record Segment(int ordinal,Integer page,Integer fromLine,Integer toLine,String text) {}
    public record SearchHit(Entry entry,String excerpt,Integer page,Integer fromLine,Integer toLine) {}
    public record Endpoint(String kind,String id) {
        public void validate(){if(!Set.of("ENTRY","ARTICLE").contains(Objects.toString(kind,"")))throw KnowledgeFailure.invalid("关系端点无效");try{if(kind.equals("ENTRY"))UUID.fromString(id);else if(Long.parseLong(id)<=0)throw new IllegalArgumentException();}catch(RuntimeException e){throw KnowledgeFailure.invalid("关系端点无效");}}
        public String key(){return kind+":"+id;}
    }
    public record RelationWrite(UUID id,long expectedRevision,Endpoint from,Endpoint to,RelationType type,String note) {}
    public record Relation(UUID id,long revision,Endpoint from,Endpoint to,RelationType type,String note) {}
    public record GraphNode(Endpoint endpoint,String title,String kind,String url) {}
    public record GraphView(List<GraphNode> nodes,List<Relation> edges,boolean truncated,boolean hasMore) {}
    public record RelationNeighbor(Relation relation,GraphNode other) {}
    public record PurgeResult(int purged,long pendingFileCleanup) {}
    public record FileInfo(UUID version,String filename,String mime,long sizeBytes,ProcessingState state,String errorCode,Instant createdAt) {}
    public record FileCommit(Ack ack,boolean accepted,UUID version) {}
}
