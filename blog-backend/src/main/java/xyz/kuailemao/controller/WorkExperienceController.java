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
import xyz.kuailemao.domain.dto.WorkExperienceDTO;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WorkExperienceVO;
import xyz.kuailemao.service.WorkExperienceService;
import xyz.kuailemao.utils.ControllerUtils;

import java.util.List;

@RestController
@Tag(name = "工作经历相关接口")
@RequestMapping("/experience")
@Validated
public class WorkExperienceController {

    @Resource
    private WorkExperienceService workExperienceService;

    @Operation(summary = "前台工作经历列表")
    @AccessLimit(seconds = 60, maxCount = 60)
    @GetMapping("/list")
    public ResponseResult<List<WorkExperienceVO>> listPublic() {
        return ControllerUtils.messageHandler(workExperienceService::listPublic);
    }

    @Operation(summary = "前台工作经历详情")
    @AccessLimit(seconds = 60, maxCount = 60)
    @GetMapping("/{id}")
    public ResponseResult<WorkExperienceVO> getPublic(@PathVariable("id") Long id) {
        WorkExperienceVO vo = workExperienceService.getPublicById(id);
        if (vo == null) {
            return ResponseResult.failure("工作经历不存在或已停用");
        }
        return ResponseResult.success(vo);
    }

    @Operation(summary = "后台工作经历列表")
    @PreAuthorize("hasAnyAuthority('blog:experience:list')")
    @LogAnnotation(module = "工作经历", operation = LogConst.GET)
    @AccessLimit(seconds = 60, maxCount = 60)
    @GetMapping("/back/list")
    public ResponseResult<List<WorkExperienceVO>> listBack() {
        return ControllerUtils.messageHandler(workExperienceService::listBack);
    }

    @Operation(summary = "根据id查询工作经历")
    @PreAuthorize("hasAnyAuthority('blog:experience:list')")
    @AccessLimit(seconds = 60, maxCount = 60)
    @GetMapping("/back/get/{id}")
    public ResponseResult<WorkExperienceVO> getById(@PathVariable Long id) {
        return ControllerUtils.messageHandler(() -> workExperienceService.getByIdVO(id));
    }

    @Operation(summary = "新增工作经历")
    @PreAuthorize("hasAnyAuthority('blog:experience:add')")
    @LogAnnotation(module = "工作经历", operation = LogConst.INSERT)
    @AccessLimit(seconds = 60, maxCount = 30)
    @PutMapping("/back/add")
    public ResponseResult<Void> add(@RequestBody @Valid WorkExperienceDTO dto) {
        return workExperienceService.addOrUpdate(dto.setId(null));
    }

    @Operation(summary = "修改工作经历")
    @PreAuthorize("hasAnyAuthority('blog:experience:update')")
    @LogAnnotation(module = "工作经历", operation = LogConst.UPDATE)
    @AccessLimit(seconds = 60, maxCount = 30)
    @PostMapping("/back/update")
    public ResponseResult<Void> update(@RequestBody @Valid WorkExperienceDTO dto) {
        return workExperienceService.addOrUpdate(dto);
    }

    @Operation(summary = "删除工作经历")
    @PreAuthorize("hasAnyAuthority('blog:experience:delete')")
    @LogAnnotation(module = "工作经历", operation = LogConst.DELETE)
    @AccessLimit(seconds = 60, maxCount = 30)
    @DeleteMapping("/back/delete")
    public ResponseResult<Void> delete(@RequestBody List<Long> ids) {
        return workExperienceService.deleteByIds(ids);
    }
}
