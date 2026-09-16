package xyz.kuailemao.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.annotation.AccessLimit;
import xyz.kuailemao.annotation.LogAnnotation;
import xyz.kuailemao.constants.LogConst;
import xyz.kuailemao.domain.dto.ExperienceProjectDTO;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.ExperienceProjectVO;
import xyz.kuailemao.service.ExperienceProjectService;
import java.util.List;

@RestController
@RequestMapping("/experience/{experienceId}/projects")
public class ExperienceProjectController {
    @Resource private ExperienceProjectService service;
    @Operation(summary="公开项目列表") @AccessLimit(seconds=60,maxCount=60) @GetMapping
    public ResponseResult<List<ExperienceProjectVO>> list(@PathVariable Long experienceId){return ResponseResult.success(service.listPublic(experienceId));}
    @Operation(summary="公开项目详情") @AccessLimit(seconds=60,maxCount=60) @GetMapping("/{projectId}")
    public ResponseResult<ExperienceProjectVO> get(@PathVariable Long experienceId,@PathVariable Long projectId){ExperienceProjectVO v=service.getPublic(experienceId,projectId);return v==null?ResponseResult.failure("项目不存在或未发布"):ResponseResult.success(v);}
    @PreAuthorize("hasAnyAuthority('blog:experience:list')") @GetMapping("/back/list") public ResponseResult<List<ExperienceProjectVO>> backList(@PathVariable Long experienceId){return ResponseResult.success(service.listBack(experienceId));}
    @PreAuthorize("hasAnyAuthority('blog:experience:list')") @GetMapping("/back/get/{projectId}") public ResponseResult<ExperienceProjectVO> backGet(@PathVariable Long experienceId,@PathVariable Long projectId){return ResponseResult.success(service.getBack(experienceId,projectId));}
    @PreAuthorize("hasAnyAuthority('blog:experience:add')") @LogAnnotation(module="经历项目",operation=LogConst.INSERT) @AccessLimit(seconds=60,maxCount=30) @PutMapping("/back/add") public ResponseResult<Void> add(@PathVariable Long experienceId,@RequestBody @Valid ExperienceProjectDTO dto){return service.add(experienceId,dto);}
    @PreAuthorize("hasAnyAuthority('blog:experience:update')") @LogAnnotation(module="经历项目",operation=LogConst.UPDATE) @AccessLimit(seconds=60,maxCount=30) @PostMapping("/back/update") public ResponseResult<Void> update(@PathVariable Long experienceId,@RequestBody @Valid ExperienceProjectDTO dto){return service.update(experienceId,dto);}
    @PreAuthorize("hasAnyAuthority('blog:experience:delete')") @LogAnnotation(module="经历项目",operation=LogConst.DELETE) @AccessLimit(seconds=60,maxCount=30) @DeleteMapping("/back/delete") public ResponseResult<Void> delete(@PathVariable Long experienceId,@RequestBody List<Long> ids){return service.delete(experienceId,ids);}
}
