package xyz.luyu.knowledge;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import xyz.luyu.diary.ai.DiaryAiCloud;
import xyz.luyu.diary.ai.DiaryAiProperties;
import xyz.luyu.diary.ai.DiaryAiText;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owner-scoped, versioned knowledge vectors. HTTP calls never hold a database transaction. */
@Component
final class KnowledgeAssistantVector {
    private record Chunk(String text,String source,Integer page,Integer fromLine,Integer toLine) {}
    private record Batch(UUID entryId,long revision,UUID fileVersion,boolean fileReady,String profile,String title,int first,List<Chunk> chunks) {}
    record Hit(KnowledgeAssistantStore.Evidence evidence,double similarity) {}
    private final KnowledgeAccess access;
    private final KnowledgeAssistantProperties config;
    private final DiaryAiProperties embeddingConfig;
    private final ObjectProvider<DiaryAiCloud> clouds;
    private final AtomicBoolean working=new AtomicBoolean();

    KnowledgeAssistantVector(KnowledgeAccess access,KnowledgeAssistantProperties config,DiaryAiProperties embeddingConfig,ObjectProvider<DiaryAiCloud> clouds){
        this.access=access;this.config=config;this.embeddingConfig=embeddingConfig;this.clouds=clouds;
    }
    private DiaryAiCloud cloud(){return embeddingConfig.configured()?clouds.getIfAvailable():null;}
    private String profile(){return DiaryAiText.hash(embeddingConfig.getProvider()+"\n"+embeddingConfig.getEmbeddingEndpoint()+"\n"
        +embeddingConfig.getEmbeddingModel()+"\n"+embeddingConfig.getDimensions()+"\nknowledge-v1-paragraph-1000-overlap120");}
    boolean ready(long owner){
        if(!config.isVectorEnabled()||cloud()==null)return false;
        try{return access.store().tx(owner,()->Boolean.TRUE.equals(access.store().jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM knowledge.schema_version WHERE version=5) AND EXISTS(SELECT FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='knowledge_assistant' AND c.relname='vector_chunk' AND c.relrowsecurity AND c.relforcerowsecurity)",Map.of(),Boolean.class)));}
        catch(RuntimeException unavailable){return false;}
    }
    @Scheduled(fixedDelay=3000,initialDelay=10000)
    public void indexNext(){
        if(!config.isVectorEnabled()||!working.compareAndSet(false,true))return;
        try {
            long owner=access.owner();
            if(!access.active(owner)||!ready(owner))return;
            var batch=next(owner);
            if(batch==null)return;
            try {
                var texts=batch.chunks().stream().map(c->prefix(batch.title())+c.text()).toList();
                var result=Objects.requireNonNull(cloud()).embed(texts);
                save(owner,batch,result.vectors());
            } catch(RuntimeException failure){failed(owner,batch);}
        } catch(RuntimeException unavailable){/* Indexing resumes on the next poll. */}
        finally {working.set(false);}
    }
    private Batch next(long owner){
        var store=access.store();String profile=profile();
        return store.tx(owner,()->{
            var p=new MapSqlParameterSource("owner",owner).addValue("profile",profile);
            var ids=store.jdbc.query("""
                SELECT e.id FROM knowledge.entry e LEFT JOIN knowledge_assistant.vector_entry i ON i.owner_id=e.owner_id AND i.entry_id=e.id
                WHERE e.owner_id=:owner AND e.deleted_at IS NULL AND
                 (i.entry_id IS NULL OR i.source_revision<>e.revision OR i.file_version IS DISTINCT FROM e.current_file_version
                  OR i.profile<>:profile OR i.file_ready<>EXISTS(SELECT FROM knowledge.processing_job j
                    WHERE j.owner_id=e.owner_id AND j.entry_id=e.id AND j.file_version=e.current_file_version AND j.state='READY')
                  OR i.status='RUNNING' AND (i.retry_after IS NULL OR i.retry_after<=now())
                  OR i.status='FAILED' AND i.attempts<3 AND i.retry_after<=now())
                ORDER BY e.updated_at,e.id LIMIT 1 FOR UPDATE OF e SKIP LOCKED
                """,p,(r,n)->r.getObject(1,UUID.class));
            if(ids.isEmpty()){
                store.jdbc.update("DELETE FROM knowledge_assistant.vector_chunk c WHERE c.owner_id=:owner AND NOT EXISTS(SELECT FROM knowledge.entry e WHERE e.owner_id=c.owner_id AND e.id=c.entry_id AND e.deleted_at IS NULL)",p);
                store.jdbc.update("DELETE FROM knowledge_assistant.vector_entry i WHERE i.owner_id=:owner AND NOT EXISTS(SELECT FROM knowledge.entry e WHERE e.owner_id=i.owner_id AND e.id=i.entry_id AND e.deleted_at IS NULL)",p);
                return null;
            }
            var entry=store.entry(owner,ids.get(0),false,false);
            p.addValue("id",entry.id()).addValue("revision",entry.revision()).addValue("file",entry.fileVersion());
            boolean fileReady=entry.fileVersion()!=null && Boolean.TRUE.equals(store.jdbc.queryForObject(
                "SELECT EXISTS(SELECT FROM knowledge.processing_job WHERE owner_id=:owner AND entry_id=:id AND file_version=:file AND state='READY')",p,Boolean.class));
            p.addValue("fileReady",fileReady);
            var chunks=chunks(store,owner,entry,fileReady);
            var previous=store.jdbc.query("SELECT source_revision,file_version,profile,file_ready,next_chunk FROM knowledge_assistant.vector_entry WHERE owner_id=:owner AND entry_id=:id",p,
                (r,n)->new Object[]{r.getLong(1),r.getObject(2,UUID.class),r.getString(3),r.getBoolean(4),r.getInt(5)});
            int first=0;
            if(!previous.isEmpty()){
                Object[] old=previous.get(0);
                if((long)old[0]==entry.revision()&&Objects.equals(old[1],entry.fileVersion())&&profile.equals(old[2])&&(boolean)old[3]==fileReady)first=(int)old[4];
            }
            if(first==0)store.jdbc.update("DELETE FROM knowledge_assistant.vector_chunk WHERE owner_id=:owner AND entry_id=:id",p);
            p.addValue("next",first).addValue("total",chunks.size()).addValue("status",first>=chunks.size()?"READY":"RUNNING");
            store.jdbc.update("""
                INSERT INTO knowledge_assistant.vector_entry(owner_id,entry_id,source_revision,file_version,file_ready,profile,next_chunk,total_chunks,status,retry_after,attempts)
                VALUES(:owner,:id,:revision,:file,:fileReady,:profile,:next,:total,:status,CASE WHEN :status='RUNNING' THEN now()+interval '2 minutes' ELSE NULL END,0)
                ON CONFLICT(owner_id,entry_id) DO UPDATE SET source_revision=excluded.source_revision,file_version=excluded.file_version,
                file_ready=excluded.file_ready,profile=excluded.profile,next_chunk=excluded.next_chunk,total_chunks=excluded.total_chunks,
                status=excluded.status,retry_after=excluded.retry_after,
                attempts=CASE WHEN knowledge_assistant.vector_entry.source_revision<>excluded.source_revision
                  OR knowledge_assistant.vector_entry.file_version IS DISTINCT FROM excluded.file_version
                  OR knowledge_assistant.vector_entry.profile<>excluded.profile
                  OR knowledge_assistant.vector_entry.file_ready<>excluded.file_ready THEN 0
                  ELSE knowledge_assistant.vector_entry.attempts END,updated_at=now()
                """,p);
            if(first>=chunks.size())return null;
            List<Chunk> selected=chunks.subList(first,Math.min(first+8,chunks.size()));
            return new Batch(entry.id(),entry.revision(),entry.fileVersion(),fileReady,profile,entry.content().title(),first,List.copyOf(selected));
        });
    }
    private static List<Chunk> chunks(KnowledgeStore store,long owner,KnowledgeModels.Entry entry,boolean fileReady){
        List<Chunk> chunks=new ArrayList<>();
        for(var c:DiaryAiText.chunks(entry.content().body()))chunks.add(new Chunk(c.text(),"BODY",null,null,null));
        if(fileReady){
            var p=KnowledgeStore.params(owner,entry.id()).addValue("file",entry.fileVersion());
            var segments=store.jdbc.query("SELECT body,page,from_line,to_line FROM knowledge.extracted_segment WHERE owner_id=:owner AND entry_id=:id AND file_version=:file ORDER BY ordinal",p,
                (r,n)->new Chunk(r.getString(1),"FILE",r.getObject(2,Integer.class),r.getObject(3,Integer.class),r.getObject(4,Integer.class)));
            for(var segment:segments)for(var c:DiaryAiText.chunks(segment.text()))chunks.add(new Chunk(c.text(),"FILE",segment.page(),segment.fromLine(),segment.toLine()));
        }
        return chunks;
    }
    private static String prefix(String title){int points=title.codePointCount(0,title.length());return title.substring(0,title.offsetByCodePoints(0,Math.min(100,points)))+"\n";}
    private void save(long owner,Batch batch,List<float[]> vectors){
        if(vectors.size()!=batch.chunks().size())throw KnowledgeFailure.unavailable();
        var store=access.store();store.tx(owner,()->{
            var p=KnowledgeStore.params(owner,batch.entryId()).addValue("revision",batch.revision()).addValue("file",batch.fileVersion())
                .addValue("profile",batch.profile()).addValue("first",batch.first());
            var entry=store.entry(owner,batch.entryId(),false,false);
            if(entry.revision()!=batch.revision()||!Objects.equals(entry.fileVersion(),batch.fileVersion()))return null;
            var state=store.jdbc.query("SELECT next_chunk,total_chunks,file_ready FROM knowledge_assistant.vector_entry WHERE owner_id=:owner AND entry_id=:id AND source_revision=:revision AND profile=:profile AND status='RUNNING' FOR UPDATE",p,
                (r,n)->new int[]{r.getInt(1),r.getInt(2),r.getBoolean(3)?1:0});
            if(state.isEmpty()||state.get(0)[0]!=batch.first()||state.get(0)[2]!=(batch.fileReady()?1:0))return null;
            for(int n=0;n<vectors.size();n++){
                Chunk chunk=batch.chunks().get(n);
                p.addValue("index",batch.first()+n).addValue("source",chunk.source()).addValue("page",chunk.page())
                    .addValue("fromLine",chunk.fromLine()).addValue("toLine",chunk.toLine()).addValue("excerpt",chunk.text())
                    .addValue("dimensions",embeddingConfig.getDimensions()).addValue("vector",Arrays.toString(vectors.get(n)));
                store.jdbc.update("""
                    INSERT INTO knowledge_assistant.vector_chunk(owner_id,entry_id,chunk_index,source_revision,file_version,profile,source_kind,page,from_line,to_line,excerpt,dimensions,embedding)
                    VALUES(:owner,:id,:index,:revision,:file,:profile,:source,:page,:fromLine,:toLine,:excerpt,:dimensions,CAST(:vector AS diary_vector.vector))
                    ON CONFLICT(owner_id,entry_id,chunk_index) DO UPDATE SET embedding=excluded.embedding,excerpt=excluded.excerpt
                    """,p);
            }
            int next=batch.first()+vectors.size();p.addValue("next",next).addValue("status",next>=state.get(0)[1]?"READY":"RUNNING");
            store.jdbc.update("UPDATE knowledge_assistant.vector_entry SET next_chunk=:next,status=:status,retry_after=NULL,attempts=0,updated_at=now() WHERE owner_id=:owner AND entry_id=:id",p);
            return null;
        });
    }
    private void failed(long owner,Batch batch){
        try{access.store().tx(owner,()->{
            var p=KnowledgeStore.params(owner,batch.entryId()).addValue("revision",batch.revision()).addValue("profile",batch.profile());
            access.store().jdbc.update("UPDATE knowledge_assistant.vector_entry SET status='FAILED',attempts=least(3,attempts+1),retry_after=now()+interval '15 minutes',updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND source_revision=:revision AND profile=:profile",p);return null;
        });}catch(RuntimeException ignored){}
    }
    List<Hit> find(long owner,String question,int limit){
        if(!ready(owner))return List.of();
        boolean indexed=access.store().tx(owner,()->Boolean.TRUE.equals(access.store().jdbc.queryForObject(
            "SELECT EXISTS(SELECT FROM knowledge_assistant.vector_entry WHERE owner_id=:owner AND profile=:profile AND status='READY' AND total_chunks>0)",
            Map.of("owner",owner,"profile",profile()),Boolean.class)));
        if(!indexed)return List.of();
        var result=Objects.requireNonNull(cloud()).embed(List.of(question));
        String vector=Arrays.toString(result.vectors().get(0));
        var p=new MapSqlParameterSource("owner",owner).addValue("profile",profile())
            .addValue("dimensions",embeddingConfig.getDimensions()).addValue("vector",vector).addValue("limit",limit);
        return access.store().tx(owner,()->access.store().jdbc.query("""
            SELECT c.entry_id,c.source_revision,c.file_version,c.source_kind,e.title,c.excerpt,c.page,c.from_line,c.to_line,
                   1-(c.embedding OPERATOR(diary_vector.<=>) CAST(:vector AS diary_vector.vector)) AS similarity
            FROM knowledge_assistant.vector_chunk c
            JOIN knowledge_assistant.vector_entry i ON i.owner_id=c.owner_id AND i.entry_id=c.entry_id AND i.status='READY'
             AND i.source_revision=c.source_revision AND i.profile=c.profile AND i.file_version IS NOT DISTINCT FROM c.file_version
            JOIN knowledge.entry e ON e.owner_id=c.owner_id AND e.id=c.entry_id AND e.revision=c.source_revision
             AND e.current_file_version IS NOT DISTINCT FROM c.file_version AND e.deleted_at IS NULL
            WHERE c.owner_id=:owner AND c.profile=:profile AND c.dimensions=:dimensions
             AND i.file_ready=EXISTS(SELECT FROM knowledge.processing_job j WHERE j.owner_id=e.owner_id
              AND j.entry_id=e.id AND j.file_version=e.current_file_version AND j.state='READY')
            ORDER BY c.embedding OPERATOR(diary_vector.<=>) CAST(:vector AS diary_vector.vector),c.entry_id,c.chunk_index LIMIT :limit
            """,p,(r,n)->new Hit(new KnowledgeAssistantStore.Evidence(r.getObject(1,UUID.class),r.getLong(2),r.getObject(3,UUID.class),
            "FILE".equals(r.getString(4)),r.getString(5),r.getString(6),r.getObject(7,Integer.class),r.getObject(8,Integer.class),r.getObject(9,Integer.class)),r.getDouble(10))));
    }
}
