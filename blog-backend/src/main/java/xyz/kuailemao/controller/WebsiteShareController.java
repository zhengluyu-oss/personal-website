package xyz.kuailemao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.annotation.AccessLimit;
import xyz.kuailemao.annotation.LogAnnotation;
import xyz.kuailemao.constants.LogConst;
import xyz.kuailemao.domain.dto.WebsiteShareDTO;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WebsiteShareVO;
import xyz.kuailemao.service.WebsiteShareService;
import xyz.kuailemao.utils.ControllerUtils;

import java.util.List;

@RestController
@Validated
@Tag(name = "网站分享接口")
@RequestMapping("/website-share")
public class WebsiteShareController {
    @Resource
    private WebsiteShareService service;

    @Operation(summary = "公开网站分享列表")
    @AccessLimit(seconds = 60, maxCount = 60)
    @GetMapping("/list")
    public ResponseResult<List<WebsiteShareVO>> list() {
        return ControllerUtils.messageHandler(service::listPublic);
    }

    @Operation(summary = "公开网站分享详情")
    @AccessLimit(seconds = 60, maxCount = 60)
    @GetMapping("/{id}")
    public ResponseResult<WebsiteShareVO> detail(@PathVariable Long id) {
        WebsiteShareVO item = service.getPublic(id);
        return item == null ? ResponseResult.failure("网站分享不存在或未发布") : ResponseResult.success(item);
    }

    @Operation(summary = "旧博客文章对应的网站分享")
    @AccessLimit(seconds = 60, maxCount = 60)
    @GetMapping("/from-article/{articleId}")
    public ResponseResult<Long> fromArticle(@PathVariable Long articleId) {
        Long id = service.getLegacyTarget(articleId);
        return id == null ? ResponseResult.failure("没有迁移记录") : ResponseResult.success(id);
    }

    @PreAuthorize("hasAnyAuthority('blog:websiteShare:list')")
    @LogAnnotation(module = "网站分享", operation = LogConst.GET)
    @GetMapping("/back/list")
    public ResponseResult<List<WebsiteShareVO>> backList() {
        return ControllerUtils.messageHandler(service::listBack);
    }

    @PreAuthorize("hasAnyAuthority('blog:websiteShare:list')")
    @GetMapping("/back/get/{id}")
    public ResponseResult<WebsiteShareVO> backDetail(@PathVariable Long id) {
        return ControllerUtils.messageHandler(() -> service.getBack(id));
    }

    @PreAuthorize("hasAnyAuthority('blog:websiteShare:add')")
    @LogAnnotation(module = "网站分享", operation = LogConst.INSERT)
    @PutMapping("/back/add")
    public ResponseResult<Long> add(@RequestBody @Valid WebsiteShareDTO dto) {
        dto.setId(null);
        return service.saveShare(dto);
    }

    @PreAuthorize("hasAnyAuthority('blog:websiteShare:update')")
    @LogAnnotation(module = "网站分享", operation = LogConst.UPDATE)
    @PostMapping("/back/update")
    public ResponseResult<Long> update(@RequestBody @Valid WebsiteShareDTO dto) {
        return dto.getId() == null ? ResponseResult.failure("缺少网站分享 ID") : service.saveShare(dto);
    }

    @PreAuthorize("hasAnyAuthority('blog:websiteShare:delete')")
    @LogAnnotation(module = "网站分享", operation = LogConst.DELETE)
    @DeleteMapping("/back/delete")
    public ResponseResult<Void> delete(@RequestBody List<Long> ids) {
        return service.deleteShares(ids);
    }
}
