package xyz.kuailemao.diary.ai;

import xyz.kuailemao.diary.DiaryFailure;
import java.util.*;
import java.util.function.BooleanSupplier;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** 小批增量向量处理。只有整篇当前版本 READY 的片段参与检索。 */
public final class DiaryAiIndex {
    private final DiaryAiStore store;private final DiaryAiCloud cloud;private final DiaryAiProperties p;
    public DiaryAiIndex(DiaryAiStore store,DiaryAiCloud cloud,DiaryAiProperties p){this.store=store;this.cloud=cloud;this.p=p;}
    public Coverage coverage(long owner,String profile,Scope scope) {
        return store.tx(owner,()->{var f=store.filter(owner,scope);f.parameters().put("profile",profile);
            return store.jdbc.queryForObject("SELECT count(*) total,count(*) FILTER(WHERE i.status='READY') indexed,count(*) FILTER(WHERE i.status IN('FAILED','UNCERTAIN','QUOTA')) failed FROM diary.entry e LEFT JOIN diary_ai.entry_index i ON i.owner_id=e.owner_id AND i.entry_id=e.id AND i.profile_id=:profile AND i.source_revision=e.revision WHERE "+f.where(),f.parameters(),
                    (r,n)->new Coverage(r.getInt("total"),r.getInt("indexed"),r.getInt("failed")));
        });
    }
    public IndexStatus status(long owner,String profile) {
        return store.tx(owner,()->{var values=store.jdbc.query("SELECT syncing FROM diary_ai.owner_settings WHERE owner_id=:owner AND profile_id=:profile",Map.of("owner",owner,"profile",profile),(r,n)->r.getBoolean(1));
            int remaining=store.jdbc.queryForObject("SELECT coalesce(sum(i.total_chunks-i.next_chunk),0) FROM diary_ai.entry_index i JOIN diary.entry e ON e.id=i.entry_id AND e.owner_id=i.owner_id AND e.revision=i.source_revision AND e.deleted_at IS NULL WHERE i.owner_id=:owner AND i.profile_id=:profile",Map.of("owner",owner,"profile",profile),Integer.class);
            return new IndexStatus(!values.isEmpty() && values.get(0),profile,coverage(owner,profile,null),remaining,"向量生成会向已配置服务发送当前日记片段；失败版本需明确重试。");
        });
    }
    record Batch(Entry entry,String profile,List<DiaryAiText.Chunk> chunks,int first,int total) {}
    private Batch next(long owner,String profile,UUID token) {
        return store.tx(owner,()->{
            store.lease(owner,token);var values=DiaryAiStore.params(owner);values.put("profile",profile);
            boolean syncing=Boolean.TRUE.equals(store.jdbc.queryForObject("SELECT syncing FROM diary_ai.owner_settings WHERE owner_id=:owner AND profile_id=:profile",values,Boolean.class));
            if(!syncing)return null;
            store.jdbc.update("DELETE FROM diary_ai.chunk c USING diary.entry e WHERE c.owner_id=:owner AND e.owner_id=c.owner_id AND e.id=c.entry_id AND (e.deleted_at IS NOT NULL OR e.revision<>c.source_revision)",values);
            store.jdbc.update("DELETE FROM diary_ai.entry_index i USING diary.entry e WHERE i.owner_id=:owner AND e.owner_id=i.owner_id AND e.id=i.entry_id AND e.deleted_at IS NOT NULL",values);
            var rows=store.jdbc.query("SELECT e.* FROM diary.entry e LEFT JOIN diary_ai.entry_index i ON i.owner_id=e.owner_id AND i.entry_id=e.id AND i.profile_id=:profile WHERE e.owner_id=:owner AND e.deleted_at IS NULL AND (i.entry_id IS NULL OR i.source_revision<>e.revision OR i.status='PENDING' OR i.status='QUOTA' AND (i.updated_at AT TIME ZONE 'Asia/Shanghai')::date<(now() AT TIME ZONE 'Asia/Shanghai')::date) ORDER BY e.updated_at,e.id LIMIT 1 FOR UPDATE OF e",values,DiaryAiStore::mapEntry);
            if(rows.isEmpty())return null;Entry e=rows.get(0);values.put("id",e.id());values.put("revision",e.revision());
            var chunks=DiaryAiText.chunks(e.body());values.put("total",chunks.size());
            var prior=store.jdbc.query("SELECT source_revision,next_chunk FROM diary_ai.entry_index WHERE owner_id=:owner AND entry_id=:id AND profile_id=:profile",values,(r,n)->new long[]{r.getLong(1),r.getInt(2)});
            int first=0;
            if(!prior.isEmpty() && prior.get(0)[0]==e.revision())first=(int)prior.get(0)[1];
            else {
                store.jdbc.update("DELETE FROM diary_ai.chunk WHERE owner_id=:owner AND entry_id=:id AND profile_id=:profile",values);
                store.jdbc.update("INSERT INTO diary_ai.entry_index(owner_id,entry_id,profile_id,source_revision,total_chunks,status) VALUES(:owner,:id,:profile,:revision,:total,'PENDING') ON CONFLICT(owner_id,entry_id,profile_id) DO UPDATE SET source_revision=excluded.source_revision,total_chunks=excluded.total_chunks,next_chunk=0,status='PENDING',error_code=NULL,updated_at=now()",values);
            }
            if(chunks.isEmpty()){store.jdbc.update("UPDATE diary_ai.entry_index SET status='READY',updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND profile_id=:profile",values);return null;}
            if(first>=chunks.size())throw DiaryFailure.unavailable();
            store.jdbc.update("UPDATE diary_ai.entry_index SET status='RUNNING',updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND profile_id=:profile",values);
            return new Batch(e,profile,chunks.subList(first,Math.min(first+8,chunks.size())),first,chunks.size());
        });
    }
    public void processBatch(long owner,String profile,UUID token,BooleanSupplier active) {
        Batch batch=next(owner,profile,token);if(batch==null)return;
        try {
            if(!active.getAsBoolean())throw new DiaryAiCloud.Failure("ACCOUNT_INACTIVE",false);
            store.reserve(owner,0,batch.chunks.size());
            var embedded=cloud.embed(batch.chunks.stream().map(c->batch.entry.title()+"\n"+c.text()).toList());
            store.usage(owner,embedded.inputTokens(),null);
            if(!active.getAsBoolean())throw new DiaryAiCloud.Failure("ACCOUNT_INACTIVE",false);
            store.tx(owner,()->{
                store.lease(owner,token);Entry current=store.entry(owner,batch.entry.id());
                if(current.revision()!=batch.entry.revision())throw new DiaryFailure(409,"索引来源已更新");
                var v=DiaryAiStore.params(owner);v.put("id",current.id());v.put("profile",profile);v.put("revision",current.revision());v.put("first",batch.first);
                Integer expected=store.jdbc.queryForObject("SELECT next_chunk FROM diary_ai.entry_index WHERE owner_id=:owner AND entry_id=:id AND profile_id=:profile AND source_revision=:revision AND status='RUNNING' FOR UPDATE",v,Integer.class);
                if(expected==null || expected!=batch.first)throw new DiaryFailure(409,"索引批次已失效");
                for(int i=0;i<batch.chunks.size();i++) {
                    var c=batch.chunks.get(i);v.put("index",c.index());v.put("start",c.start());v.put("end",c.end());v.put("hash",c.sha256());v.put("dimensions",p.getDimensions());v.put("vector",Arrays.toString(embedded.vectors().get(i)));
                    store.jdbc.update("INSERT INTO diary_ai.chunk(owner_id,entry_id,profile_id,source_revision,chunk_index,start_cp,end_cp,text_hash,dimensions,embedding) VALUES(:owner,:id,:profile,:revision,:index,:start,:end,:hash,:dimensions,CAST(:vector AS diary_vector.vector))",v);
                }
                int next=batch.first+batch.chunks.size();v.put("next",next);v.put("state",next==batch.total?"READY":"PENDING");
                store.jdbc.update("UPDATE diary_ai.entry_index SET next_chunk=:next,status=:state,error_code=NULL,updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND profile_id=:profile AND source_revision=:revision",v);return null;
            });
        } catch(RuntimeException error) {
            String state=error instanceof DiaryFailure d && d.status()==429?"QUOTA":error instanceof DiaryAiCloud.Failure f && f.uncertain()?"UNCERTAIN":"FAILED";
            store.tx(owner,()->{store.jdbc.update("UPDATE diary_ai.entry_index SET status=:state,error_code='BATCH_FAILED',updated_at=now() WHERE owner_id=:owner AND entry_id=:id AND profile_id=:profile AND source_revision=:revision AND status='RUNNING'",Map.of("owner",owner,"id",batch.entry.id(),"profile",profile,"revision",batch.entry.revision(),"state",state));return null;});
        }
    }
    record Candidate(UUID id,long revision,String title,java.time.LocalDate from,java.time.LocalDate to,
                     java.time.Instant created,int start,int end,String text,String hash) {}
    public List<Source> retrieve(long owner,String profile,Request request,float[] vector) {
        return store.snapshot(owner,()->{
            Coverage coverage=coverage(owner,profile,request.scope());
            if(coverage.total()!=coverage.indexed() && !request.allowPartialIndex())throw new DiaryFailure(409,"部分日记尚未索引，请建立索引或明确选择部分资料");
            var f=store.filter(owner,request.scope());var v=f.parameters();v.put("profile",profile);v.put("dimensions",p.getDimensions());v.put("vector",Arrays.toString(vector));v.put("question",request.question());v.put("threshold",p.getMinimumSimilarity());
            String base="WITH eligible AS MATERIALIZED(SELECT c.*,e.title,e.covered_from,e.covered_to,e.created_at,substring(e.body FROM c.start_cp+1 FOR c.end_cp-c.start_cp) fragment FROM diary_ai.chunk c JOIN diary_ai.entry_index i ON i.owner_id=c.owner_id AND i.entry_id=c.entry_id AND i.profile_id=c.profile_id AND i.source_revision=c.source_revision AND i.status='READY' JOIN diary.entry e ON e.owner_id=c.owner_id AND e.id=c.entry_id AND e.revision=c.source_revision WHERE "+f.where()+" AND c.profile_id=:profile AND c.dimensions=:dimensions),scored AS MATERIALIZED(SELECT *,1-(embedding OPERATOR(diary_vector.<=>) CAST(:vector AS diary_vector.vector)) similarity FROM eligible) ";
            org.springframework.jdbc.core.RowMapper<Candidate> mapper=(r,n)->new Candidate(r.getObject("entry_id",UUID.class),r.getLong("source_revision"),r.getString("title"),r.getObject("covered_from",java.time.LocalDate.class),r.getObject("covered_to",java.time.LocalDate.class),r.getTimestamp("created_at").toInstant(),r.getInt("start_cp"),r.getInt("end_cp"),r.getString("fragment"),r.getString("text_hash"));
            var semantic=store.jdbc.query(base+"SELECT * FROM scored WHERE similarity>=:threshold ORDER BY similarity DESC,entry_id,chunk_index LIMIT 64",v,mapper);
            var lexical=store.jdbc.query(base+"SELECT * FROM scored WHERE strpos(lower(title||E'\\n'||fragment),lower(:question))>0 ORDER BY similarity DESC,entry_id,chunk_index LIMIT 64",v,mapper);
            Map<Candidate,Double> ranking=new HashMap<>();
            for(var list:List.of(semantic,lexical))for(int i=0;i<list.size();i++)ranking.merge(list.get(i),1.0/(60+i+1),Double::sum);
            var ordered=ranking.entrySet().stream().sorted(Map.Entry.<Candidate,Double>comparingByValue().reversed().thenComparing(e->e.getKey().id).thenComparingInt(e->e.getKey().start)).toList();
            List<Source> sources=new ArrayList<>();Map<UUID,Integer> counts=new HashMap<>();int characters=0;
            for(var row:ordered){Candidate c=row.getKey();int size=points(c.text)+points(c.title)+200;
                if(counts.getOrDefault(c.id,0)>=2 || sources.size()>=8 || characters+size>p.getMaxQuestionContext() || !DiaryAiText.hash(c.text).equals(c.hash))continue;
                sources.add(new Source("S"+(sources.size()+1),c.id,c.revision,c.title,c.from,c.to,c.created,c.start,c.end,c.text,c.hash));counts.merge(c.id,1,Integer::sum);characters+=size;
            }
            return List.copyOf(sources);
        });
    }
}
