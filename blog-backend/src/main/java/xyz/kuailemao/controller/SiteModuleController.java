package xyz.kuailemao.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.service.SiteModuleAccessService;
import java.util.List;

@RestController
@RequestMapping("/site-modules")
public class SiteModuleController {
    private final SiteModuleAccessService service;
    public SiteModuleController(SiteModuleAccessService service) { this.service = service; }

    public record Update(@NotNull Boolean publicAccess, @NotNull @Size(max=100) List<@NotNull @Positive Long> roleIds,
                         @NotNull @Min(0) @Max(2147483646) Integer revision) {}

    @GetMapping("/access")
    public ResponseResult<List<SiteModuleAccessService.Access>> access() { return ResponseResult.success(service.access()); }

    @PreAuthorize("hasAuthority('system:role:list')")
    @GetMapping("/back/policies")
    public ResponseResult<List<SiteModuleAccessService.Policy>> policies() { return ResponseResult.success(service.policies()); }

    @PreAuthorize("hasAuthority('system:role:update')")
    @PutMapping("/back/policies/{key}")
    public ResponseResult<Void> update(@PathVariable String key, @RequestBody @Valid Update update) {
        service.update(key, update.publicAccess(), update.roleIds(), update.revision());
        return ResponseResult.success();
    }
}
