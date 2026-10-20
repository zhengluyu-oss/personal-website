package xyz.kuailemao.knowledge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.domain.response.ResponseResult;
import java.util.*;
import static xyz.kuailemao.knowledge.KnowledgeModels.*;

@RestController @RequestMapping("/knowledge")
public class KnowledgeController {
    private final KnowledgeAccess access;
    public KnowledgeController(KnowledgeAccess access){this.access=access;}
    static <T>ResponseEntity<ResponseResult<T>> ok(T data){return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Robots-Tag","noindex, nofollow, noarchive").header("X-Content-Type-Options","nosniff").body(ResponseResult.success(data));}
    @GetMapping("/view/capabilities") public ResponseEntity<ResponseResult<Capabilities>> capabilities(){return ok(access.capabilities());}
    @PostMapping("/view/entries/search") public ResponseEntity<ResponseResult<Page<Entry>>> list(@RequestBody Filter filter){long owner=filter.deleted()?access.requireManage():access.requireRead();return ok(access.store().list(owner,filter));}
    @GetMapping("/view/entries/{id}") public ResponseEntity<ResponseResult<Entry>> read(@PathVariable UUID id){long owner=access.requireRead();return ok(access.store().read(owner,id,false));}
    @GetMapping("/manage/entries/{id}") public ResponseEntity<ResponseResult<Entry>> managed(@PathVariable UUID id){long owner=access.requireManage();return ok(access.store().read(owner,id,true));}
    @PostMapping("/manage/entries") public ResponseEntity<ResponseResult<Ack>> save(@RequestBody Write write){long owner=access.requireManage();return ok(access.store().save(owner,write));}
    @PostMapping("/manage/entries/{id}/{action}") public ResponseEntity<ResponseResult<Ack>> action(@PathVariable UUID id,@PathVariable String action,@RequestBody Action request){long owner=access.requireManage();String key=switch(action){case "delete"->"DELETE";case "restore"->"RESTORE";case "recover"->"RECOVER";default->throw KnowledgeFailure.invalid("操作无效");};return ok(access.store().action(owner,id,request,key));}
    @GetMapping("/manage/entries/{id}/history") public ResponseEntity<ResponseResult<Page<History>>> history(@PathVariable UUID id,@RequestParam(defaultValue="1") int page){long owner=access.requireManage();return ok(access.store().history(owner,id,page));}
    @GetMapping("/view/labels") public ResponseEntity<ResponseResult<List<Label>>> labels(){long owner=access.requireRead();return ok(access.store().labels(owner));}
    @PostMapping("/manage/labels") public ResponseEntity<ResponseResult<Label>> label(@RequestBody LabelWrite write){long owner=access.requireManage();return ok(access.store().saveLabel(owner,write));}
    @DeleteMapping("/manage/labels/{id}") public ResponseEntity<ResponseResult<Void>> removeLabel(@PathVariable UUID id,@RequestParam long revision){long owner=access.requireManage();access.store().deleteLabel(owner,id,revision);return ok(null);}
    @PostMapping("/manage/trash/purge") public ResponseEntity<ResponseResult<PurgeResult>> purgeExpired(){long owner=access.requireManage();return ok(access.store().purgeExpired(owner));}
}
