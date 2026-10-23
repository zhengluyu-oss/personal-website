package xyz.luyu.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import xyz.luyu.service.AdminLoginDeliveryException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import xyz.luyu.annotation.AccessLimit;
import xyz.luyu.annotation.LogAnnotation;
import xyz.luyu.constants.LogConst;
import xyz.luyu.domain.dto.*;
import xyz.luyu.domain.entity.LoginUser;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.UserAccountVO;
import xyz.luyu.domain.vo.UserDetailsVO;
import xyz.luyu.domain.vo.UserListVO;
import xyz.luyu.service.UserService;
import xyz.luyu.service.AdminLoginChallengeService;
import xyz.luyu.handler.SecurityHandler;
import xyz.luyu.utils.IpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import xyz.luyu.utils.ControllerUtils;
import xyz.luyu.utils.SecurityUtils;
import xyz.luyu.utils.WebUtil;
import xyz.luyu.enums.RespEnum;

import java.util.List;

/**
 * @author luyu
 * /auth/ 都是需要验证的
 * <p>
 * 创建时间：2023/10/10 14:29
 */

@Slf4j
@RestController
@Tag(name = "用户相关接口")
@RequestMapping("/user")
public class UserController {

    private static final String ADMIN_LOGIN_VERIFY_ERROR = "登录验证失败，请重新登录";

    @Resource
    private AdminLoginChallengeService adminLoginChallengeService;

    @Resource
    private SecurityHandler securityHandler;

    @PostMapping("/admin-login/verify")
    @AccessLimit(seconds = 60, maxCount = 10)
    public void verifyAdminLogin(@Valid @RequestBody AdminLoginVerifyDTO dto,
                                 HttpServletRequest request,
                                 HttpServletResponse response) {
        try {
            LoginUser user = adminLoginChallengeService.verify(dto.getChallengeId(), dto.getCode(), IpUtils.getIpAddr(request));
            securityHandler.issueToken(request, response, user);
        } catch (BadCredentialsException exception) {
            WebUtil.renderString(response, ResponseResult.failure(
                    RespEnum.VERIFY_CODE_ERROR.getCode(), ADMIN_LOGIN_VERIFY_ERROR).asJsonString());
        }
    }

    @PostMapping("/admin-login/resend")
    @AccessLimit(seconds = 60, maxCount = 5)
    public ResponseResult<?> resendAdminLogin(@Valid @RequestBody AdminLoginResendDTO dto,
                                              HttpServletRequest request) {
        try {
            return ResponseResult.success(adminLoginChallengeService.resend(
                    dto.getChallengeId(), IpUtils.getIpAddr(request)), "验证码已重新发送");
        } catch (AdminLoginDeliveryException exception) {
            return exception.response();
        } catch (BadCredentialsException exception) {
            return ResponseResult.failure(RespEnum.VERIFY_CODE_ERROR.getCode(), ADMIN_LOGIN_VERIFY_ERROR);
        } catch (RuntimeException exception) {
            log.warn("Administrator resend unavailable: type={}", exception.getClass().getSimpleName());
            return AdminLoginDeliveryException.serviceUnavailable();
        }
    }

    @Resource
    private UserService userService;

    @Resource
    private xyz.luyu.service.EmailChangeService emailChangeService;

    @PreAuthorize("isAuthenticated()")
    @AccessLimit(seconds = 60, maxCount = 3)
    @PostMapping("/auth/email-change/start")
    public ResponseResult<?> startEmailChange(@Valid @RequestBody EmailChangeStartDTO dto) {
        return ResponseResult.success(emailChangeService.start(dto));
    }

    @Operation(summary = "获取当前登录用户信息")
    @AccessLimit(seconds = 60, maxCount = 30)
    @GetMapping("/auth/info")
    public ResponseResult<UserAccountVO> getInfo() {
        return ControllerUtils.messageHandler(() -> userService.findAccountById(SecurityUtils.getUserId()));
    }

    /**
     * 前台修改用户信息
     *
     * @param userUpdateDTO 用户信息
     * @return 是否成功
     */
    @Operation(summary = "修改用户信息")
    @Parameter(name = "userUpdateDTO", description = "修改用户信息")
    @AccessLimit(seconds = 60, maxCount = 30)
    @PostMapping("/auth/update")
    public ResponseResult<Void> updateUser(@RequestBody @Valid UserUpdateDTO userUpdateDTO) {
        return userService.updateUser(userUpdateDTO);
    }

    /**
     *  上传用户头像
     * @param avatarFile  头像
     * @return 是否成功, 头像地址
     */
    @Operation(summary = "用户头像上传")
    @AccessLimit(seconds = 60, maxCount = 3)
    @PostMapping("/auth/upload/avatar")
    public ResponseResult<String> uploadAvatar(@RequestParam("avatarFile") MultipartFile avatarFile) throws Exception {
        return userService.uploadAvatar(avatarFile);
    }

    /**
     * 修改用户绑定邮箱
     * @param updateEmailDTO 所需参数
     * @return 是否成功
     */
    @Operation(summary = "修改用户绑定邮箱")
    @AccessLimit(seconds = 60, maxCount = 30)
    @PostMapping("/auth/update/email")
    public ResponseResult<Void> updateEmail(@RequestBody @Valid UpdateEmailDTO updateEmailDTO) {
        return userService.updateEmailAndVerify(updateEmailDTO);
    }

    // 第三方登录用户绑定邮箱
    @Operation(summary = "第三方登录用户绑定邮箱")
    @AccessLimit(seconds = 60, maxCount = 30)
    @PostMapping("/auth/third/update/email")
    public ResponseResult<Void> thirdUpdateEmail(@RequestBody @Valid UpdateEmailDTO updateEmailDTO) {
        return userService.thirdUpdateEmail(updateEmailDTO);
    }

    @Operation(summary = "用户注册")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "前台注册", operation = LogConst.INSERT)
    @PostMapping("/register")
    public ResponseResult<Void> register(@RequestBody @Valid UserRegisterDTO userRegisterDTO) {
        return userService.userRegister(userRegisterDTO);
    }

    @Operation(summary = "重置密码-确认邮件")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "邮件确认", operation = LogConst.RESET_CONFIRM)
    @PostMapping("/reset-confirm")
    public ResponseResult<Void> resetConfirm(@RequestBody @Valid UserResetConfirmDTO userResetDTO) {
        return userService.userResetConfirm(userResetDTO);
    }

    @Operation(summary = "重置密码")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "重置密码", operation = LogConst.RESET_PASSWORD)
    @PostMapping("/reset-password")
    public ResponseResult<Void> resetPassword(@RequestBody @Valid UserResetPasswordDTO userResetDTO) {
        return userService.userResetPassword(userResetDTO);
    }

    @PreAuthorize("hasAnyAuthority('system:user:list')")
    @Operation(summary = "获取用户列表")
    @AccessLimit(seconds = 60, maxCount = 30)
    @GetMapping("/list")
    public ResponseResult<List<UserListVO>> getUserList() {
        return ControllerUtils.messageHandler(() -> userService.getUserOrSearch(null));
    }

    @PreAuthorize("hasAnyAuthority('system:user:search')")
    @Operation(summary = "搜索用户列表")
    @AccessLimit(seconds = 60, maxCount = 30)
    @PostMapping("/search")
    public ResponseResult<List<UserListVO>> searchUserList(@RequestBody UserSearchDTO userSearchDTO) {
        return ControllerUtils.messageHandler(() -> userService.getUserOrSearch(userSearchDTO));
    }

    @PreAuthorize("hasAnyAuthority('system:user:status:update')")
    @Operation(summary = "更新用户状态")
    @Parameter(name = "roleDeleteDTO", description = "修改用户状态")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "用户管理", operation = LogConst.UPDATE)
    @PostMapping("/update/status")
    public ResponseResult<Void> updateStatus(@RequestBody @Valid UpdateRoleStatusDTO updateRoleStatusDTO) {
        return userService.updateStatus(updateRoleStatusDTO.getId(), updateRoleStatusDTO.getStatus());
    }

    @PreAuthorize("hasAnyAuthority('system:user:details')")
    @Operation(summary = "获取用户详细信息")
    @Parameter(name = "id", description = "用户id")
    @AccessLimit(seconds = 60, maxCount = 30)
    @GetMapping("/details/{id}")
    public ResponseResult<UserDetailsVO> getUserDetails(@PathVariable Long id) {
        return ControllerUtils.messageHandler(() -> userService.findUserDetailsById(id));
    }

    @PreAuthorize("hasAnyAuthority('system:user:delete')")
    @Operation(summary = "删除用户")
    @Parameter(name = "id", description = "用户id")
    @AccessLimit(seconds = 60, maxCount = 30)
    @LogAnnotation(module = "用户管理", operation = LogConst.DELETE)
    @DeleteMapping("/delete")
    public ResponseResult<Void> deleteUser(@RequestBody UserDeleteDTO userDeleteDTO) {
        return userService.deleteUser(userDeleteDTO.getIds());
    }

}
