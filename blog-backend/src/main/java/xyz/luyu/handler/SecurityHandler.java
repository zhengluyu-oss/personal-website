package xyz.luyu.handler;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.luyu.service.AdminLoginDeliveryException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import xyz.luyu.constants.RespConst;
import xyz.luyu.constants.SecurityConst;
import xyz.luyu.domain.entity.LoginUser;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.AuthorizeVO;
import xyz.luyu.service.AdminLoginChallengeService;
import xyz.luyu.utils.IpUtils;
import xyz.luyu.enums.RespEnum;
import xyz.luyu.service.LoginLogService;
import xyz.luyu.service.UserService;
import xyz.luyu.utils.JwtUtils;
import xyz.luyu.utils.RedisCache;
import xyz.luyu.utils.WebUtil;

import java.io.IOException;
import java.util.UUID;

/**
 * @author luyu
 * <p>
 * 创建时间：2023/10/11 16:03
 */
@Slf4j
@Component
public class SecurityHandler {

    @Resource
    private JwtUtils jwtUtils;

    @Resource
    private RedisCache redisCache;

    @Resource
    private LoginLogService loginLogService;

    @Resource
    private UserService userService;

    @Resource
    private AdminLoginChallengeService adminLoginChallengeService;

    public static final String USER_NAME = "username";


    /**
     * 登录成功处理
     *
     * @param request        请求
     * @param response       响应
     * @param authentication 认证信息
     */
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        handlerOnAuthenticationSuccess(request,response,(LoginUser)authentication.getPrincipal());
    }

    public void handlerOnAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            LoginUser user
    ) {
        boolean administrator = user.getAuthorities().stream()
                .anyMatch(authority -> (SecurityConst.ROLE_PREFIX + SecurityConst.ROLE_ADMIN).equals(authority.getAuthority()));
        if (administrator) {
            try {
                var challenge = adminLoginChallengeService.create(user, IpUtils.getIpAddr(request));
                WebUtil.renderString(response, ResponseResult.success(challenge, "请完成邮箱验证").asJsonString());
            } catch (AdminLoginDeliveryException failure) {
                renderLoginFailure(request, response, failure);
            } catch (RuntimeException failure) {
                // This boundary runs only after password/OAuth authentication succeeded.
                log.warn("Administrator challenge unavailable: type={}", failure.getClass().getSimpleName());
                renderLoginFailure(request, response, new AuthenticationServiceException("challenge unavailable"));
            }
            return;
        }
        issueToken(request, response, user);
    }

    public void issueToken(HttpServletRequest request, HttpServletResponse response, LoginUser user) {
        Long id = user.getUser().getId();
        String name = user.getUser().getUsername();
        // UUID做jwt的id
        String uuid = UUID.randomUUID().toString();
        // 生成jwt
        String token = jwtUtils.createJwt(uuid, user, id, name);

        // 转换VO
        AuthorizeVO authorizeVO = user.getUser().asViewObject(AuthorizeVO.class, v -> {
            v.setToken(token);
            v.setExpire(jwtUtils.expireTime(user));
        });
        userService.userLoginStatus(user.getUser().getId(), user.getUser().getRegisterType());
        loginLogService.loginLog(request, request.getParameter(USER_NAME), 0, RespConst.SUCCESS_LOGIN_MSG);
        WebUtil.renderString(response, ResponseResult.success(authorizeVO, RespConst.SUCCESS_LOGIN_MSG).asJsonString());
    }


    /**
     * 登录失败处理
     */
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        renderLoginFailure(request, response, exception);
    }

    private void renderLoginFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException failure) {
        SecurityContextHolder.clearContext();
        ResponseResult<?> result = failure instanceof AdminLoginDeliveryException delivery ? delivery.response()
                : failure instanceof AuthenticationServiceException ? AdminLoginDeliveryException.serviceUnavailable()
                : ResponseResult.failure(RespEnum.USERNAME_OR_PASSWORD_ERROR.getCode(), RespConst.USERNAME_OR_PASSWORD_ERROR_MSG);
        try {
            loginLogService.loginLog(request, request.getParameter(USER_NAME), 1, "登录验证失败");
        } catch (RuntimeException loggingFailure) {
            log.warn("Login failure audit unavailable: type={}", loggingFailure.getClass().getSimpleName());
        }
        WebUtil.renderString(response, result.asJsonString());
    }

    /**
     * 退出登录处理
     */
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        boolean invalidateJwt = jwtUtils.invalidateJwt(request.getHeader("Authorization"));
        if (invalidateJwt) {
            WebUtil.renderString(response, ResponseResult.success().asJsonString());
            return;
        }
        WebUtil.renderString(response, ResponseResult.failure(RespEnum.NOT_LOGIN.getCode(), RespEnum.NOT_LOGIN.getMsg()).asJsonString());
    }

    /**
     * 没有登录处理
     */
    public void onUnAuthenticated(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        WebUtil.renderString(response, ResponseResult.failure(RespEnum.NOT_LOGIN.getCode(), RespEnum.NOT_LOGIN.getMsg()).asJsonString());
    }

    /**
     * 没有权限处理
     */
    public void onAccessDeny(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) {
        WebUtil.renderString(response, ResponseResult.failure(RespEnum.NO_PERMISSION.getCode(), RespEnum.NO_PERMISSION.getMsg()).asJsonString());
    }
}
