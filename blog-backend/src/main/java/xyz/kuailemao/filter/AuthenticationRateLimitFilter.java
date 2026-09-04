package xyz.kuailemao.filter;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.enums.RespEnum;
import xyz.kuailemao.utils.IpUtils;
import xyz.kuailemao.utils.RedisCache;
import xyz.kuailemao.utils.WebUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {
    @Resource
    private RedisCache redisCache;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equalsIgnoreCase(request.getMethod()) || !"/user/login".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String client = IpUtils.getIpAddr(request);
        String identity = request.getParameter("username");
        String identityKey = digest(identity == null ? "missing" : identity.trim().toLowerCase(Locale.ROOT));
        if (exceeded("auth:login:client:" + client, 20) || exceeded("auth:login:identity:" + identityKey, 8)) {
            log.warn("Authentication request throttled: client={}", client);
            WebUtil.renderString(response, ResponseResult.failure(RespEnum.REQUEST_FREQUENTLY.getCode(), "请求过于频繁，请稍后重试").asJsonString());
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean exceeded(String key, int maximum) {
        Long count = redisCache.increment(key, 1L);
        if (count != null && count == 1) redisCache.expire(key, 60, TimeUnit.SECONDS);
        return count != null && count > maximum;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
