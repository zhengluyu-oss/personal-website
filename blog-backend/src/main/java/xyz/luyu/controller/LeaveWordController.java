package xyz.luyu.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.annotation.AccessLimit;
import xyz.luyu.annotation.CheckBlacklist;
import xyz.luyu.annotation.LogAnnotation;
import xyz.luyu.constants.LogConst;
import xyz.luyu.domain.dto.LeaveWordIsCheckDTO;
import xyz.luyu.domain.dto.SearchLeaveWordDTO;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.LeaveWordListVO;
import xyz.luyu.domain.vo.LeaveWordVO;
import xyz.luyu.service.LeaveWordService;
import xyz.luyu.utils.ControllerUtils;

import java.util.List;

/**
 * (LeaveWord)表控制层
 *
 * @author luyu
 * @since 2023-11-03 15:01:10
 */
@RestController
@RequestMapping("leaveWord")
@Validated
@Tag(name = "留言板", description = "留言板相关接口")
public class LeaveWordController {

    @Resource
    private LeaveWordService leaveWordService;

    @Operation(summary = "获取留言板列表")
    @Parameters({
            @Parameter(name = "id", description = "留言板id", in = ParameterIn.QUERY)
    })
    @GetMapping("/list")
    @AccessLimit(seconds = 60, maxCount = 10)
    public ResponseResult<List<LeaveWordVO>> list(@RequestParam(value = "id",required = false) String id) {
        return ControllerUtils.messageHandler(() -> leaveWordService.getLeaveWordList(id));
    }

    @CheckBlacklist
    @Operation(summary = "用户留言")
    @PostMapping("/auth/userLeaveWord")
    @AccessLimit(seconds = 60, maxCount = 10)
    public ResponseResult<Void> userLeaveWord(@RequestBody @NotNull String content) {
        return leaveWordService.userLeaveWord(content);
    }

    @PreAuthorize("hasAnyAuthority('blog:leaveword:list')")
    @Operation(summary = "后台留言列表")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="留言管理",operation= LogConst.GET)
    @GetMapping("/back/list")
    public ResponseResult<List<LeaveWordListVO>> backList() {
        return ControllerUtils.messageHandler(() -> leaveWordService.getBackLeaveWordList(null));
    }

    @PreAuthorize("hasAnyAuthority('blog:leaveword:search')")
    @Operation(summary = "搜索后台留言列表")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="留言管理",operation= LogConst.SEARCH)
    @PostMapping("/back/search")
    public ResponseResult<List<LeaveWordListVO>> backList(@RequestBody SearchLeaveWordDTO searchDTO) {
        return ControllerUtils.messageHandler(() -> leaveWordService.getBackLeaveWordList(searchDTO));
    }

    @PreAuthorize("hasAnyAuthority('blog:leaveword:isCheck')")
    @Operation(summary = "修改留言是否通过")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="留言管理",operation= LogConst.UPDATE)
    @PostMapping("/back/isCheck")
    public ResponseResult<Void> isCheck(@RequestBody @Valid LeaveWordIsCheckDTO leaveWordIsCheckDTO) {
        return leaveWordService.isCheckLeaveWord(leaveWordIsCheckDTO);
    }

    @PreAuthorize("hasAnyAuthority('blog:leaveword:delete')")
    @Operation(summary = "删除留言")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="留言管理",operation= LogConst.DELETE)
    @DeleteMapping("/back/delete")
    public ResponseResult<Void> delete(@RequestBody List<Long> ids) {
        return leaveWordService.deleteLeaveWord(ids);
    }
}

