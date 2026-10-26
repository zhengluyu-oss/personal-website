package xyz.luyu.knowledge;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.domain.response.ResponseResult;

import java.util.UUID;
import static xyz.luyu.knowledge.KnowledgeModels.*;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeRelationController {
    private final KnowledgeAccess access;
    private final ObjectProvider<KnowledgeRelations> relations;

    public KnowledgeRelationController(KnowledgeAccess access, ObjectProvider<KnowledgeRelations> relations) {
        this.access = access;
        this.relations = relations;
    }

    private KnowledgeRelations ready() {
        access.store();
        KnowledgeRelations service = relations.getIfAvailable();
        if (service == null) throw KnowledgeFailure.unavailable();
        return service;
    }

    @PostMapping("/view/relations/neighbors")
    public ResponseEntity<ResponseResult<Page<RelationNeighbor>>> neighbors(@RequestBody Endpoint center,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        long owner = access.requireRead();
        return KnowledgeController.ok(ready().neighbors(owner, center, page, size));
    }

    @PostMapping("/view/relations/graph")
    public ResponseEntity<ResponseResult<GraphView>> graph(@RequestBody Endpoint center,
            @RequestParam(defaultValue = "1") int depth,
            @RequestParam(defaultValue = "50") int nodes,
            @RequestParam(defaultValue = "100") int edges) {
        long owner = access.requireRead();
        return KnowledgeController.ok(ready().graph(owner, center, depth, nodes, edges));
    }

    @PostMapping("/manage/relations")
    public ResponseEntity<ResponseResult<Relation>> save(@RequestBody RelationWrite write) {
        long owner = access.requireManage();
        return KnowledgeController.ok(ready().save(owner, write));
    }

    @DeleteMapping("/manage/relations/{id}")
    public ResponseEntity<ResponseResult<Void>> delete(@PathVariable UUID id, @RequestParam long revision) {
        long owner = access.requireManage();
        ready().delete(owner, id, revision);
        return KnowledgeController.ok(null);
    }
}
