package xyz.luyu.knowledge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.domain.response.ResponseResult;

@RestController @RequestMapping("/knowledge/view/assistant")
public final class KnowledgeAssistantController {
    private final KnowledgeAssistantService service;
    public KnowledgeAssistantController(KnowledgeAssistantService service){this.service=service;}
    @GetMapping("/capabilities") public ResponseEntity<ResponseResult<KnowledgeAssistantService.Capability>> capabilities(){return KnowledgeController.ok(service.capabilities());}
    @PostMapping("/ask") public ResponseEntity<ResponseResult<KnowledgeAssistantService.Answer>> ask(@RequestBody KnowledgeAssistantService.Question question){return KnowledgeController.ok(service.ask(question));}
}
