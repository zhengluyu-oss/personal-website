package xyz.kuailemao.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.service.ExperienceOverviewService;

@RestController
@RequestMapping("/experience-overview")
public class ExperienceOverviewController {
    private final ExperienceOverviewService service;

    public ExperienceOverviewController(ExperienceOverviewService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseResult<ExperienceOverviewService.Overview> get() {
        return ResponseResult.success(service.publicCounts());
    }
}
