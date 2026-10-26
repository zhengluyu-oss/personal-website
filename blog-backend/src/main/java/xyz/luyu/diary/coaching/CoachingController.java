package xyz.luyu.diary.coaching;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.diary.DiaryFailure;
import xyz.luyu.domain.response.ResponseResult;
import java.util.UUID;
import static xyz.luyu.diary.coaching.CoachingModels.*;

@RestController
@RequestMapping("/diary/auth/coaching")
public class CoachingController {
    private final CoachingService service;
    public CoachingController(CoachingService service) { this.service=service; }
    @GetMapping("/capabilities") public ResponseResult<Capabilities> capabilities() { return ResponseResult.success(service.capabilities()); }
    @GetMapping("/{type:goals|actions|feedback}")
    public ResponseResult<Page<Row>> list(@PathVariable String type,@RequestParam(required=false) UUID parent,
            @RequestParam(required=false) String state,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size) {
        long owner=service.owner();return ResponseResult.success(service.store(owner).list(owner,Kind.parse(type),parent,state,page,size));
    }
    @GetMapping("/{type:goals|actions|feedback}/{id}") public ResponseResult<Row> read(@PathVariable String type,@PathVariable UUID id) {
        long owner=service.owner();return ResponseResult.success(service.store(owner).read(owner,Kind.parse(type),id));
    }
    @PostMapping("/{type:goals|actions|feedback}") public ResponseResult<Ack> create(@PathVariable String type,@RequestBody Write write) {
        long owner=service.owner();if(write.expectedRevision()!=0) throw DiaryFailure.invalid("新记录版本应为零");
        return ResponseResult.success(service.store(owner).write(owner,Kind.parse(type),write,false,service.origins(owner)));
    }
    @PutMapping("/{type:goals|actions|feedback}/{id}") public ResponseResult<Ack> save(@PathVariable String type,@PathVariable UUID id,@RequestBody Write write) {
        long owner=service.owner();if(!id.equals(write.id()) || write.expectedRevision()<1) throw DiaryFailure.invalid("记录标识或版本无效");
        return ResponseResult.success(service.store(owner).write(owner,Kind.parse(type),write,false,service.origins(owner)));
    }
    @DeleteMapping("/feedback/{id}") public ResponseResult<Ack> delete(@PathVariable UUID id,@RequestBody Write write) {
        long owner=service.owner();if(!id.equals(write.id())) throw DiaryFailure.invalid("记录标识不一致");
        return ResponseResult.success(service.store(owner).write(owner,Kind.FEEDBACK,write,true,service.origins(owner)));
    }
    @GetMapping("/{type:goals|actions|feedback}/{id}/history") public ResponseResult<Page<History>> history(@PathVariable String type,@PathVariable UUID id,@RequestParam(defaultValue="1") int page) {
        long owner=service.owner();return ResponseResult.success(service.store(owner).history(owner,Kind.parse(type),id,page));
    }
    @PostMapping("/periods/preview") public ResponseResult<Preview> preview(@RequestBody Period request) {
        long owner=service.owner();return ResponseResult.success(service.periods(owner).preview(owner,request));
    }
    @PostMapping("/periods/jobs") public ResponseEntity<ResponseResult<CoachingPeriodAi.Job>> enqueue(@RequestBody CoachingPeriodAi.Request request) {
        long owner=service.owner();return ResponseEntity.accepted().body(ResponseResult.success(service.ai(owner).enqueue(owner,request)));
    }
    @GetMapping("/periods/jobs/{id}") public ResponseResult<CoachingPeriodAi.Job> job(@PathVariable UUID id) {
        long owner=service.owner();return ResponseResult.success(service.ai(owner).job(owner,id));
    }
    @PostMapping("/periods/jobs/{id}/cancel") public ResponseResult<CoachingPeriodAi.Job> cancel(@PathVariable UUID id) {
        long owner=service.owner();return ResponseResult.success(service.ai(owner).cancel(owner,id));
    }
    @GetMapping("/periods/reports") public ResponseResult<Page<CoachingPeriodAi.ReportSummary>> reports(@RequestParam(defaultValue="1") int page) {
        long owner=service.owner();return ResponseResult.success(service.ai(owner).list(owner,page));
    }
    @GetMapping("/periods/reports/{id}") public ResponseResult<CoachingPeriodAi.Report> report(@PathVariable UUID id) {
        long owner=service.owner();return ResponseResult.success(service.ai(owner).report(owner,id));
    }
    @DeleteMapping("/periods/reports/{id}") public ResponseResult<Void> removeReport(@PathVariable UUID id) {
        long owner=service.owner();service.ai(owner).delete(owner,id);return ResponseResult.success();
    }
    @PostMapping("/suggestion") public ResponseResult<JSONObject> suggestion(@RequestBody Origin origin) {
        long owner=service.owner();return ResponseResult.success(service.origins(owner).suggestion(owner,origin));
    }
    @GetMapping("/actions/{id}/origin") public ResponseResult<String> origin(@PathVariable UUID id) {
        long owner=service.owner();return ResponseResult.success(service.origins(owner).status(owner,id));
    }
    @PostMapping("/export") public ResponseEntity<byte[]> export() {
        long owner=service.owner();return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition","attachment; filename=\"private-coaching.json\"").header("Content-Type","application/json; charset=UTF-8").body(service.store(owner).export(owner));
    }
}
