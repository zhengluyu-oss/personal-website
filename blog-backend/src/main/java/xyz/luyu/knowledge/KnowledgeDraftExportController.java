package xyz.luyu.knowledge;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.domain.response.ResponseResult;

import java.util.UUID;

@RestController
@RequestMapping("/knowledge/manage/exports")
public class KnowledgeDraftExportController {
    private final KnowledgeAccess access;
    private final ObjectProvider<KnowledgeDraftExport> exports;

    public KnowledgeDraftExportController(KnowledgeAccess access,ObjectProvider<KnowledgeDraftExport> exports){this.access=access;this.exports=exports;}
    private KnowledgeDraftExport service(){access.store();var value=exports.getIfAvailable();if(value==null)throw KnowledgeFailure.unavailable();return value;}

    @PostMapping("/prepare")
    public ResponseEntity<ResponseResult<KnowledgeDraftExport.Result>> prepare(@RequestBody KnowledgeDraftExport.Request request){
        long owner=access.requireManage();return KnowledgeController.ok(service().prepare(owner,request));
    }
    @PostMapping("/create")
    public ResponseEntity<ResponseResult<KnowledgeDraftExport.Result>> create(@RequestBody KnowledgeDraftExport.Request request){
        long owner=access.requireManage();return KnowledgeController.ok(service().create(owner,request));
    }
    @GetMapping("/{id}")
    public ResponseEntity<ResponseResult<KnowledgeDraftExport.Result>> read(@PathVariable UUID id){
        long owner=access.requireManage();return KnowledgeController.ok(service().read(owner,id));
    }
}
