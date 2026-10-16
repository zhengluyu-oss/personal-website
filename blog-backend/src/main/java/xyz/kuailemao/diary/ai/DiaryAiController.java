package xyz.kuailemao.diary.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.domain.response.ResponseResult;
import java.util.UUID;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

@RestController @RequestMapping("/diary/auth/ai")
public class DiaryAiController {
    private final DiaryAiService service;
    public DiaryAiController(DiaryAiService service){this.service=service;}
    @GetMapping("/capabilities") public ResponseResult<Capabilities> capabilities(){return ResponseResult.success(service.capabilities());}
    @GetMapping("/index") public ResponseResult<IndexStatus> index(){long o=service.owner();var s=service.store(o);return ResponseResult.success(service.index(o).status(o,s.ensureProfile(o)));}
    @PostMapping("/index") public ResponseResult<Job> start(@RequestBody IndexRequest r){long o=service.owner();return ResponseResult.success(service.store(o).indexAction(o,r,true));}
    @PostMapping("/index/pause") public ResponseResult<Job> pause(@RequestBody IndexRequest r){long o=service.owner();return ResponseResult.success(service.store(o).indexAction(o,r,false));}
    @PostMapping("/reviews/preview") public ResponseResult<Preview> preview(@RequestBody Scope scope){long o=service.owner();return ResponseResult.success(service.store(o).preview(o,scope));}
    @PostMapping("/questions") public ResponseEntity<ResponseResult<Job>> question(@RequestBody Request r){return enqueue(Kind.QUESTION,r);}
    @PostMapping("/reviews") public ResponseEntity<ResponseResult<Job>> review(@RequestBody Request r){return enqueue(Kind.REVIEW,r);}
    private ResponseEntity<ResponseResult<Job>> enqueue(Kind k,Request r){long o=service.owner();return ResponseEntity.accepted().body(ResponseResult.success(service.store(o).enqueue(o,k,r)));}
    @GetMapping("/jobs/{id}") public ResponseResult<Job> job(@PathVariable UUID id){long o=service.owner();return ResponseResult.success(service.store(o).job(o,id));}
    @PostMapping("/jobs/{id}/cancel") public ResponseResult<Job> cancel(@PathVariable UUID id){long o=service.owner();return ResponseResult.success(service.store(o).cancel(o,id));}
    @GetMapping("/reports") public ResponseResult<Page<ReportSummary>> reports(@RequestParam(defaultValue="1") int page){long o=service.owner();return ResponseResult.success(service.store(o).reports(o,page));}
    @GetMapping("/reports/{id}") public ResponseResult<Report> report(@PathVariable UUID id){long o=service.owner();return ResponseResult.success(service.store(o).report(o,id));}
    @DeleteMapping("/reports/{id}") public ResponseResult<Void> delete(@PathVariable UUID id){long o=service.owner();service.store(o).deleteReport(o,id);return ResponseResult.success();}
}
