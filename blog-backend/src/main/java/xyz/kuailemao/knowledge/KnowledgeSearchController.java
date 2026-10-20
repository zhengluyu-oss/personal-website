package xyz.kuailemao.knowledge;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import xyz.kuailemao.domain.response.ResponseResult;
import static xyz.kuailemao.knowledge.KnowledgeModels.*;

@RestController
@RequestMapping("/knowledge/view")
public class KnowledgeSearchController {
    private final KnowledgeAccess access;
    public KnowledgeSearchController(KnowledgeAccess access){this.access=access;}
    @PostMapping("/search")
    public ResponseEntity<ResponseResult<Page<SearchHit>>> search(@RequestBody Filter filter){
        long owner=filter.deleted()?access.requireManage():access.requireRead();
        return KnowledgeController.ok(access.store().search(owner,filter));
    }
}
