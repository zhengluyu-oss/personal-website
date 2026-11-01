package xyz.luyu.knowledge;

import com.alibaba.fastjson2.JSON;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Tag-local display order; never changes entry content/revision or another tag's sequence. */
public final class KnowledgeTagOrder {
    public record Order(List<UUID> entryIds,String digest) {}
    public record Move(UUID mutationId,UUID entryId,int position,String expectedDigest) {}
    private final KnowledgeStore store;
    public KnowledgeTagOrder(KnowledgeStore store){this.store=store;}
    static String rank(String tag) {
        return "coalesce((SELECT (o.positions->>e.id::text)::integer FROM knowledge_order.tag_order o WHERE o.owner_id=e.owner_id AND o.tag_id="+tag+"),2147483647),e.created_at,e.id";
    }
    private Order current(long owner,UUID tag) {
        var p=KnowledgeStore.params(owner,tag).addValue("tag",tag);
        if(store.jdbc.query("SELECT id FROM knowledge.label WHERE owner_id=:owner AND id=:id AND kind='TAG'",p,(r,n)->r.getObject(1,UUID.class)).isEmpty())throw new KnowledgeFailure(404,"标签不存在或不可访问");
        var ids=store.jdbc.query("SELECT e.id FROM knowledge.entry e JOIN knowledge.entry_tag t ON t.owner_id=e.owner_id AND t.entry_id=e.id WHERE e.owner_id=:owner AND e.deleted_at IS NULL AND t.tag_id=:tag ORDER BY "+rank(":tag")+" LIMIT 10001",p,(r,n)->r.getObject(1,UUID.class));
        if(ids.size()>10000)throw KnowledgeFailure.invalid("标签内超过一万条，请先拆分标签");
        Long revision=store.jdbc.query("SELECT revision FROM knowledge_order.tag_order WHERE owner_id=:owner AND tag_id=:tag",p,(r,n)->r.getLong(1)).stream().findFirst().orElse(0L);
        return new Order(List.copyOf(ids),KnowledgeStore.hash(List.of(tag,revision,ids)));
    }
    public Order read(long owner,UUID tag){return store.tx(owner,()->{store.lockOwner(owner);return current(owner,tag);});}
    public Order move(long owner,UUID tag,Move input){
        if(input==null||input.mutationId()==null||input.entryId()==null||input.position()<1||input.expectedDigest()==null)throw KnowledgeFailure.invalid("请提供有效的位置与当前排序版本");
        String hash=KnowledgeStore.hash(List.of(tag,input));
        return store.tx(owner,()->{
            store.lockOwner(owner);
            var p=KnowledgeStore.params(owner,input.mutationId()).addValue("tag",tag).addValue("hash",hash);
            var prior=store.jdbc.query("SELECT request_hash,result FROM knowledge_order.mutation WHERE owner_id=:owner AND id=:id",p,(r,n)->{
                if(!hash.equals(r.getString(1)))throw KnowledgeFailure.conflict();return JSON.parseObject(r.getString(2),Order.class);
            });
            if(!prior.isEmpty())return prior.get(0);
            Order before=current(owner,tag);
            if(!before.digest().equals(input.expectedDigest()))throw new KnowledgeFailure(409,"标签内条目或排序已变化，请刷新后重试");
            var ids=new ArrayList<>(before.entryIds());
            if(input.position()>ids.size()||!ids.remove(input.entryId()))throw KnowledgeFailure.invalid("条目不在此标签中或目标位置超出范围");
            ids.add(input.position()-1,input.entryId());
            if(!ids.equals(before.entryIds())) {
                var positions=new LinkedHashMap<UUID,Integer>();for(int i=0;i<ids.size();i++)positions.put(ids.get(i),i+1);
                p.addValue("entries",JSON.toJSONString(positions));
                store.jdbc.update("INSERT INTO knowledge_order.tag_order(owner_id,tag_id,positions,revision) VALUES(:owner,:tag,CAST(:entries AS jsonb),1) ON CONFLICT(owner_id,tag_id) DO UPDATE SET positions=excluded.positions,revision=knowledge_order.tag_order.revision+1",p);
            }
            Order after=current(owner,tag);p.addValue("result",JSON.toJSONString(after));
            store.jdbc.update("INSERT INTO knowledge_order.mutation(owner_id,id,request_hash,result) VALUES(:owner,:id,:hash,CAST(:result AS jsonb))",p);
            return after;
        });
    }
    public Map<UUID,Map<UUID,Integer>> numbers(long owner,List<UUID> entries) {
        if(entries==null||entries.size()>50||entries.stream().anyMatch(Objects::isNull))throw KnowledgeFailure.invalid("每次最多读取50条序号");
        if(entries.isEmpty())return Map.of();
        return store.tx(owner,()->{
            var result=new LinkedHashMap<UUID,Map<UUID,Integer>>();
            store.jdbc.query("SELECT * FROM (SELECT e.id,t.tag_id,row_number() OVER(PARTITION BY t.tag_id ORDER BY "+rank("t.tag_id")+") number FROM knowledge.entry e JOIN knowledge.entry_tag t ON t.owner_id=e.owner_id AND t.entry_id=e.id WHERE e.owner_id=:owner AND e.deleted_at IS NULL) numbered WHERE id IN(:entries)",Map.of("owner",owner,"entries",entries),r->{
                UUID id=r.getObject("id",UUID.class),tag=r.getObject("tag_id",UUID.class);
                result.computeIfAbsent(id,k->new LinkedHashMap<>()).put(tag,r.getInt("number"));
            });return result;
        });
    }

    @RestController @RequestMapping("/knowledge")
    public static class Controller {
        private final KnowledgeAccess access;
        public Controller(KnowledgeAccess access){this.access=access;}
        @GetMapping("/view/tags/{id}/order") public ResponseEntity<?> read(@PathVariable UUID id){long owner=access.requireRead();return KnowledgeController.ok(new KnowledgeTagOrder(access.store()).read(owner,id));}
        @PostMapping("/view/tag-numbers") public ResponseEntity<?> numbers(@RequestBody List<UUID> ids){long owner=access.requireRead();return KnowledgeController.ok(new KnowledgeTagOrder(access.store()).numbers(owner,ids));}
        @PostMapping("/manage/tags/{id}/order") public ResponseEntity<?> move(@PathVariable UUID id,@RequestBody Move input){long owner=access.requireManage();return KnowledgeController.ok(new KnowledgeTagOrder(access.store()).move(owner,id,input));}
    }
}
