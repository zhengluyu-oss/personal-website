package xyz.kuailemao.service.impl;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.IpService;
import xyz.kuailemao.service.OauthService;
import xyz.kuailemao.service.OauthBrowserBinding;
import xyz.kuailemao.service.UserService;
import xyz.kuailemao.utils.IpUtils;
import xyz.kuailemao.utils.SecurityUtils;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * @author kuailemao
 * <p>
 * 创建时间：2023/12/21 17:09
 */
@Slf4j
@Service
public class OauthServiceImpl implements OauthService {

    private static final String LOGIN_FAILED = "第三方登录已失效，请重新尝试";

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserService userService;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private IpService ipService;

    @Resource
    private OauthBrowserBinding browserBinding;

    @Resource
    private xyz.kuailemao.service.EmailChangeService emailChangeService;


    @Override
    public String handleLogin(AuthResponse authResponse, HttpServletRequest request, Integer type) {
        if (authResponse == null || !Objects.equals(authResponse.getCode(), 2000)
                || !(authResponse.getData() instanceof AuthUser authUser)
                || (!Objects.equals(type, 1) && !Objects.equals(type, 2))) {
            return "?oauth_error=provider_failed";
        }

        final Long userId;
        try {
            userId = Long.valueOf(authUser.getUuid());
        } catch (RuntimeException exception) {
            return "?oauth_error=provider_failed";
        }
        if (userId <= 0) return "?oauth_error=provider_failed";

        String challenge = browserBinding.reauthenticationChallenge(request, type);
        if (challenge != null) {
            try {
                emailChangeService.verifyProvider(challenge, type, userId);
                return "?email_reauth=complete";
            } catch (BadCredentialsException failure) {
                return "?email_reauth=failed";
            }
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            String ipAddr = IpUtils.getIpAddr(request);
            user = User.builder()
                    .id(userId)
                    .username(authUser.getUsername())
                    .avatar(authUser.getAvatar())
                    .nickname(authUser.getNickname())
                    // OAuth accounts authenticate through the provider, never with its access token as a password.
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .email(authUser.getEmail())
                    .registerType(type)
                    .registerIp(ipAddr)
                    .loginType(type)
                    .loginIp(ipAddr)
                    .loginTime(new Date())
                    .isDisable(0)
                    .isDeleted(0)
                    .build();
            try {
                if (!userService.save(user)) return "?oauth_error=provider_failed";
            } catch (DuplicateKeyException conflict) {
                // Email equality is not proof that two provider identities belong to one account.
                return "?oauth_error=account_conflict";
            }
            ipService.refreshIpDetailAsyncByUidAndRegister(userId);
        } else if (!Objects.equals(user.getRegisterType(), type)) {
            // Provider IDs are not globally unique. Never attach one provider to another local account.
            return "?oauth_error=account_conflict";
        }

        if (Objects.equals(user.getIsDisable(), 1) || Objects.equals(user.getIsDeleted(), 1)) {
            return "?oauth_error=account_unavailable";
        }
        String code = browserBinding.issue(request, type, userId);
        return "?oauth_code=" + code;
    }

    @Override
    public LoginUser exchangeCode(String code) {
        if (code == null || !code.matches("[0-9a-f]{64}")) {
            throw new BadCredentialsException(LOGIN_FAILED);
        }
        String account = browserBinding.exchange(SecurityUtils.getCurrentHttpRequest(), code);
        if (account == null) throw new BadCredentialsException(LOGIN_FAILED);
        try {
            String[] parts = account.split(":", 2);
            if (parts.length != 2) throw new IllegalArgumentException("Malformed OAuth handoff");
            return userService.loadLoginUserById(Long.valueOf(parts[1]), Integer.valueOf(parts[0]));
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException(LOGIN_FAILED);
        }
    }
}
