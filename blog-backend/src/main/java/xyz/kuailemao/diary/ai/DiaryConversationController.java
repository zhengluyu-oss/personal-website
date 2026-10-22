package xyz.kuailemao.diary.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.diary.DiaryFailure;
import xyz.kuailemao.domain.response.ResponseResult;
import java.util.UUID;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;
import static xyz.kuailemao.diary.ai.DiaryConversationStore.*;

@RestController
@RequestMapping("/diary/auth/ai/conversations")
public final class DiaryConversationController {
    private final DiaryAiService service;
    public DiaryConversationController(DiaryAiService service) { this.service=service; }

    private DiaryConversationStore store(long owner) {
        DiaryConversationStore conversations=new DiaryConversationStore(service.store(owner));
        if(!conversations.available(owner)) throw new DiaryFailure(503,"日记对话尚未准备好");
        return conversations;
    }
    @GetMapping
    public ResponseResult<Page<Summary>> list(@RequestParam Kind kind,@RequestParam(defaultValue="1") int page) {
        long owner=service.owner();return ResponseResult.success(store(owner).list(owner,kind,page));
    }
    @GetMapping("/{id}")
    public ResponseResult<Detail> detail(@PathVariable UUID id,@RequestParam(defaultValue="1") int page) {
        long owner=service.owner();return ResponseResult.success(store(owner).detail(owner,id,page));
    }
    @PostMapping("/questions")
    public ResponseEntity<ResponseResult<Submitted>> question(@RequestBody Start request) {
        return create(Kind.QUESTION,request);
    }
    @PostMapping("/reviews")
    public ResponseEntity<ResponseResult<Submitted>> review(@RequestBody Start request) {
        return create(Kind.REVIEW,request);
    }
    private ResponseEntity<ResponseResult<Submitted>> create(Kind kind,Start request) {
        long owner=service.owner();return ResponseEntity.accepted().body(ResponseResult.success(store(owner).start(owner,kind,request.request())));
    }
    @PostMapping("/{id}/turns")
    public ResponseEntity<ResponseResult<Submitted>> followup(@PathVariable UUID id,@RequestBody Followup request) {
        long owner=service.owner();return ResponseEntity.accepted().body(ResponseResult.success(store(owner).followup(owner,id,request)));
    }
    @DeleteMapping("/{id}")
    public ResponseResult<Void> delete(@PathVariable UUID id) {
        long owner=service.owner();store(owner).delete(owner,id);return ResponseResult.success();
    }
}
