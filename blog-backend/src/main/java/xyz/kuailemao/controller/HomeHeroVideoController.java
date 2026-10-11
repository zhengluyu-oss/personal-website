package xyz.kuailemao.controller;

import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.annotation.AccessLimit;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.service.HomeHeroVideoService;

@RestController
@RequestMapping("banners/back/hero-video")
public class HomeHeroVideoController {
    @Resource private HomeHeroVideoService service;

    public record ConfirmRequest(String objectKey) {}

    @PreAuthorize("hasAnyAuthority('blog:banner:list')")
    @GetMapping
    public ResponseResult<HomeHeroVideoService.VideoState> current() {
        return ResponseResult.success(service.current());
    }

    @PreAuthorize("hasAnyAuthority('blog:banner:add')")
    @AccessLimit(seconds = 60, maxCount = 10)
    @PostMapping("/ticket")
    public ResponseResult<HomeHeroVideoService.UploadTicket> ticket() {
        return ResponseResult.success(service.issueTicket());
    }

    @PreAuthorize("hasAnyAuthority('blog:banner:add')")
    @AccessLimit(seconds = 60, maxCount = 10)
    @PostMapping("/confirm")
    public ResponseResult<HomeHeroVideoService.VideoState> confirm(@RequestBody ConfirmRequest request) {
        return service.confirm(request.objectKey());
    }

    @PreAuthorize("hasAnyAuthority('blog:banner:delete')")
    @AccessLimit(seconds = 60, maxCount = 10)
    @DeleteMapping
    public ResponseResult<Void> remove() { return service.remove(); }
}
