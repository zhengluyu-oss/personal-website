package xyz.luyu.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.service.ExperienceOverviewService;

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
