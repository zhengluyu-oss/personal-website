package xyz.luyu.diary;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.domain.response.ResponseResult;

import java.time.YearMonth;
import java.util.UUID;
import static xyz.luyu.diary.DiaryModels.*;

/** 私密专用接口：不添加公共内容审计、上传或站点地图注解。 */
@RestController
@RequestMapping("/diary/auth")
public class DiaryController {
    private final DiaryService service;
    public DiaryController(DiaryService service) { this.service=service; }
    @GetMapping("/capabilities")
    public ResponseResult<Capabilities> capabilities() { return ResponseResult.success(service.capabilities()); }
    @PostMapping("/entries")
    public ResponseResult<Ack> create(@RequestBody Write write) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).create(owner,write));
    }
    @GetMapping("/entries/{id}")
    public ResponseResult<Entry> read(@PathVariable UUID id) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).read(owner,id));
    }
    @PutMapping("/entries/{id}")
    public ResponseResult<Ack> save(@PathVariable UUID id,@RequestBody Write write) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).save(owner,id,write));
    }
    @DeleteMapping("/entries/{id}")
    public ResponseResult<Ack> delete(@PathVariable UUID id,@RequestBody Change change) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).delete(owner,id,change));
    }
    @PostMapping("/entries/{id}/restore")
    public ResponseResult<Ack> restore(@PathVariable UUID id,@RequestBody Change change) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).restore(owner,id,change));
    }
    @PostMapping("/entries/search")
    public ResponseResult<Page<Entry>> search(@RequestBody Search search) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).search(owner,search));
    }
    @GetMapping("/calendar")
    public ResponseResult<java.util.List<CalendarDay>> calendar(@RequestParam String month) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).calendar(owner,YearMonth.parse(month)));
    }
    @GetMapping("/entries/{id}/history")
    public ResponseResult<Page<History>> history(@PathVariable UUID id,@RequestParam(defaultValue="1") int page) {
        long owner=service.requireOwner();return ResponseResult.success(service.store(owner).history(owner,id,page));
    }
    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody Export request) {
        long owner=service.requireOwner();Download result=service.store(owner).export(owner,request);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\""+result.filename()+"\"")
                .header(HttpHeaders.CONTENT_TYPE,result.contentType()).body(result.bytes());
    }
}
