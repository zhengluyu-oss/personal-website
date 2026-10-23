package xyz.luyu.controller;



import io.lettuce.core.dynamic.annotation.Value;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import xyz.luyu.annotation.AccessLimit;
import xyz.luyu.annotation.LogAnnotation;
import xyz.luyu.constants.LogConst;
import xyz.luyu.domain.dto.LoginLogDeleteDTO;
import xyz.luyu.domain.dto.LoginLogDTO;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.LoginLogVO;
import xyz.luyu.service.LoginLogService;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.utils.ControllerUtils;

import java.util.List;

/**
 * (LoginLog)表控制层
 *
 * @author luyu
 * @since 2023-12-08 14:38:43
 */
@Tag(name = "登录日志相关接口")
@RestController
@RequestMapping("loginLog")
public class LoginLogController {
    /**
     * 服务对象
     */
    @Resource
    private LoginLogService loginLogService;

    @PreAuthorize("hasAnyAuthority('system:log:login:list')")
    @Operation(summary = "显示所有登录日志")
    @AccessLimit(seconds = 60, maxCount = 30)
    @GetMapping("/list")
    public ResponseResult<List<LoginLogVO>> getLoginLogList() {
        return ControllerUtils.messageHandler(() -> loginLogService.searchLoginLog(null));
    }

    @PreAuthorize("hasAnyAuthority('system:log:login:search')")
    @Operation(summary = "搜索登录日志")
    @Parameter(name = "loginLogDTO", description = "搜索条件")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="登录日志",operation= LogConst.SEARCH)
    @PostMapping("/search")
    public ResponseResult<List<LoginLogVO>> getLoginLogSearch(@RequestBody LoginLogDTO loginLogDTO) {
        return ControllerUtils.messageHandler(() -> loginLogService.searchLoginLog(loginLogDTO));
    }

    @PreAuthorize("hasAnyAuthority('system:log:login:delete')")
    @Operation(summary = "删除/清空登录日志")
    @Parameter(name = "deleteLoginLogDTO", description = "删除的id数组")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module="登录日志",operation= LogConst.DELETE)
    @DeleteMapping("/delete")
    public ResponseResult<Void> deleteLoginLog(@RequestBody @Valid LoginLogDeleteDTO deleteLoginLogDTO) {
        return loginLogService.deleteLoginLog(deleteLoginLogDTO);
    }

}

