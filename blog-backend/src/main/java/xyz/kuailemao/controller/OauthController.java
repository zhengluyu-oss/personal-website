package xyz.kuailemao.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.request.AuthGiteeRequest;
import me.zhyd.oauth.request.AuthGithubRequest;
import me.zhyd.oauth.request.AuthRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.annotation.AccessLimit;
import xyz.kuailemao.domain.dto.OauthExchangeDTO;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.request.oauth.GiteeBody;
import xyz.kuailemao.domain.request.oauth.GithubBody;
import xyz.kuailemao.enums.RespEnum;
import xyz.kuailemao.enums.RegisterOrLoginTypeEnum;
import xyz.kuailemao.handler.SecurityHandler;
import xyz.kuailemao.service.OauthService;
import xyz.kuailemao.service.OauthBrowserBinding;
import xyz.kuailemao.utils.WebUtil;

import java.io.IOException;

/**
 * @author kuailemao
 * <p>
 * 创建时间：2023/12/21 14:52
 */
@Slf4j
@Tag(name = "第三方登录")
@RestController
@RequestMapping("/oauth")
public class OauthController {

    @Resource
    private GiteeBody giteeBody;

    @Resource
    private GithubBody githubBody;

    @Resource
    private OauthService oauthService;

    @Resource
    private OauthBrowserBinding browserBinding;

    @Resource
    private xyz.kuailemao.service.EmailChangeService emailChangeService;

    public record ReauthenticationRequest(@jakarta.validation.constraints.Pattern(regexp = "[0-9a-f]{64}")
                                         @jakarta.validation.constraints.NotNull String challengeId) {}

    @org.springframework.security.access.prepost.PreAuthorize("isAuthenticated()")
    @AccessLimit(seconds = 60, maxCount = 3)
    @PostMapping("/reauth/start")
    public ResponseResult<String> startReauthentication(@Valid @RequestBody ReauthenticationRequest dto,
                                                        HttpServletRequest request, HttpServletResponse response) {
        int provider = emailChangeService.requiredProvider(dto.challengeId());
        String callback = provider == 1 ? giteeBody.getRedirectUri() : githubBody.getRedirectUri();
        String state = browserBinding.beginReauthentication(request, response, provider, callback, dto.challengeId());
        AuthRequest auth = provider == 1 ? getGiteeAuthRequest() : getGithubAuthRequest();
        return ResponseResult.success(auth.authorize(state));
    }

    @Resource
    private SecurityHandler securityHandler;

    @Value("${web.index.path}")
    private String path;

    // gitee登录
    @Operation(summary = "Gitee登录")
    @AccessLimit(seconds = 60, maxCount = 5)
    @GetMapping("/gitee/render")
    public void giteeRenderAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthRequest authRequest = getGiteeAuthRequest();
        response.sendRedirect(authRequest.authorize(browserBinding.begin(request, response, 1, giteeBody.getRedirectUri())));
    }

    @Operation(summary = "Gitee登录回调")
    @AccessLimit(seconds = 60, maxCount = 5)
    @GetMapping("/gitee/callback")
    public void giteeLogin(AuthCallback callback, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!validateBrowser(callback, request, response, 1)) return;
        AuthRequest authRequest = getGiteeAuthRequest();
        String parameter = oauthService.handleLogin(authRequest.login(callback), request, RegisterOrLoginTypeEnum.GITEE.getRegisterType());
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(path+parameter);
    }
    // github登录
    @Operation(summary = "Github登录")
    @AccessLimit(seconds = 60, maxCount = 5)
    @GetMapping("/github/render")
    public void githubRenderAuth(HttpServletRequest request, HttpServletResponse response) throws IOException {
        AuthRequest authRequest = getGithubAuthRequest();
        response.sendRedirect(authRequest.authorize(browserBinding.begin(request, response, 2, githubBody.getRedirectUri())));
    }

    @Operation(summary = "Github登录回调")
    @AccessLimit(seconds = 60, maxCount = 5)
    @GetMapping("/github/callback")
    public void githubLogin(AuthCallback callback, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!validateBrowser(callback, request, response, 2)) return;
        AuthRequest authRequest = getGithubAuthRequest();
        String parameter = oauthService.handleLogin(authRequest.login(callback), request,RegisterOrLoginTypeEnum.GITHUB.getRegisterType());
        response.setHeader("Cache-Control", "no-store");
        response.sendRedirect(path+parameter);
    }

    private boolean validateBrowser(AuthCallback callback, HttpServletRequest request,
                                    HttpServletResponse response, int provider) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        try {
            browserBinding.validateCallback(request, callback.getState(), provider);
            return true;
        } catch (AuthenticationException failure) {
            response.sendRedirect(path + "?oauth_error=invalid_attempt");
            return false;
        }
    }

    @Operation(summary = "兑换第三方登录一次性凭据")
    @AccessLimit(seconds = 60, maxCount = 10)
    @PostMapping("/exchange")
    public void exchange(@Valid @RequestBody OauthExchangeDTO dto,
                         HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        try {
            securityHandler.handlerOnAuthenticationSuccess(request, response, oauthService.exchangeCode(dto.getCode()));
        } catch (AuthenticationException exception) {
            WebUtil.renderString(response, ResponseResult.failure(
                    RespEnum.USERNAME_OR_PASSWORD_ERROR.getCode(),
                    "第三方登录已失效，请重新尝试").asJsonString());
        }
    }

    /**
     * 获取gitee授权请求
     * @return gitee授权请求
     */
    private AuthRequest getGiteeAuthRequest() {
        return new AuthGiteeRequest(AuthConfig.builder()
                .clientId(giteeBody.getClientId())
                .clientSecret(giteeBody.getClientSecret())
                .redirectUri(giteeBody.getRedirectUri())
                .build());
    }

    /**
     * 获取github授权请求
     * @return github授权请求
     */
    private AuthRequest getGithubAuthRequest() {
        return new AuthGithubRequest(AuthConfig.builder()
                .clientId(githubBody.getClientId())
                .clientSecret(githubBody.getClientSecret())
                .redirectUri(githubBody.getRedirectUri())
                .build());
    }


}
