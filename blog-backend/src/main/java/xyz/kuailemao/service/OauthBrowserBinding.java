package xyz.kuailemao.service;

import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

/** Bind each OAuth attempt to an HttpOnly browser secret before contacting a provider. */
@Service
public class OauthBrowserBinding {
    static final String STATE_PREFIX = "auth:oauth:v2:state:";
    static final String EXCHANGE_PREFIX = "auth:oauth:v2:exchange:";
    static final String COOKIE_PREFIX = "blog_oauth_";
    private static final String VALIDATED = OauthBrowserBinding.class.getName() + ".validated";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String FAILURE = "第三方登录已失效，请重新尝试";
    private static final DefaultRedisScript<String> CONSUME = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if not value or string.sub(value, 1, string.len(ARGV[1])) ~= ARGV[1] then return nil end
            redis.call('DEL', KEYS[1])
            return value
            """, String.class);
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Value("${oauth.allow-local-insecure-cookie:false}") private boolean allowLocalInsecureCookie;

    public String begin(HttpServletRequest request, HttpServletResponse response, int provider, String callbackUrl) {
        return beginPurpose(request, response, provider, callbackUrl, "login", "");
    }

    public String beginReauthentication(HttpServletRequest request, HttpServletResponse response, int provider,
                                        String callbackUrl, String challengeId) {
        if (challengeId == null || !challengeId.matches("[0-9a-f]{64}")) throw invalid();
        return beginPurpose(request, response, provider, callbackUrl, "email-change", challengeId);
    }

    private String beginPurpose(HttpServletRequest request, HttpServletResponse response, int provider,
                                String callbackUrl, String purpose, String context) {
        int active = 0;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) if (cookie.getName().startsWith(COOKIE_PREFIX)) active++;
        }
        if (active >= 5 || (provider != 1 && provider != 2)) throw invalid();
        final URI callback;
        try { callback = URI.create(callbackUrl); }
        catch (RuntimeException failure) { throw invalid(); }
        boolean local = allowLocalInsecureCookie && ("localhost".equals(request.getServerName())
                || "127.0.0.1".equals(request.getServerName()))
                && ("localhost".equals(callback.getHost()) || "127.0.0.1".equals(callback.getHost()))
                && "http".equals(callback.getScheme());
        if (!"https".equals(callback.getScheme()) && !(local && "http".equals(callback.getScheme()))) throw invalid();
        String suffix = provider == 1 ? "/gitee/callback" : "/github/callback";
        String path = callback.getPath();
        if (callback.getHost() == null || path == null || !path.endsWith(suffix) || callback.getUserInfo() != null
                || callback.getQuery() != null || callback.getFragment() != null
                || !path.equals(callback.getRawPath()) || !path.equals(callback.normalize().getPath())) throw invalid();
        String state = randomHex(16);
        String secret = randomHex(32);
        String binding = provider + ":" + purpose + ":" + digest(secret) + ":" + context;
        store(STATE_PREFIX + state, binding, Duration.ofMinutes(10));
        response.addHeader("Set-Cookie", ResponseCookie.from(COOKIE_PREFIX + state, secret)
                .httpOnly(true).secure(!local).sameSite("Lax")
                .path(path.substring(0, path.length() - suffix.length()) + "/")
                .maxAge(Duration.ofMinutes(10)).build().toString());
        response.setHeader("Cache-Control", "no-store");
        return state;
    }

    public void validateCallback(HttpServletRequest request, String state, int provider) {
        if (state == null || !state.matches("[0-9a-f]{32}")) throw invalid();
        String browser = digest(cookie(request, state));
        for (String purpose : new String[]{"login", "email-change"}) {
            String expected = provider + ":" + purpose + ":" + browser + ":";
            String value = consume(STATE_PREFIX + state, expected);
            if (value != null) {
                request.setAttribute(VALIDATED, state + ":" + value);
                return;
            }
        }
        throw invalid();
    }

    public String reauthenticationChallenge(HttpServletRequest request, int provider) {
        Object raw = request == null ? null : request.getAttribute(VALIDATED);
        if (!(raw instanceof String value)) throw invalid();
        String prefix = value.substring(33);
        if (prefix.startsWith(provider + ":login:")) return null;
        String[] fields = prefix.split(":", -1);
        if (fields.length != 4 || !fields[0].equals(Integer.toString(provider))
                || !fields[1].equals("email-change") || !fields[3].matches("[0-9a-f]{64}")) throw invalid();
        request.removeAttribute(VALIDATED);
        return fields[3];
    }

    public String issue(HttpServletRequest request, int provider, long userId) {
        Object raw = request == null ? null : request.getAttribute(VALIDATED);
        if (!(raw instanceof String binding)) throw invalid();
        String state = binding.substring(0, 32);
        String prefix = binding.substring(33);
        if (!prefix.startsWith(provider + ":login:")) throw invalid();
        request.removeAttribute(VALIDATED);
        String code = state + randomHex(16);
        store(EXCHANGE_PREFIX + digest(code), prefix + userId, Duration.ofMinutes(2));
        return code;
    }

    public String exchange(HttpServletRequest request, String code) {
        if (code == null || !code.matches("[0-9a-f]{64}")) throw invalid();
        String browser = digest(cookie(request, code.substring(0, 32)));
        // Provider is not selected by the caller. Only a matching stored binding is consumable.
        for (int provider : new int[]{1, 2}) {
            String prefix = provider + ":login:" + browser + ":";
            String value = consume(EXCHANGE_PREFIX + digest(code), prefix);
            if (value != null) return provider + ":" + value.substring(prefix.length());
        }
        throw invalid();
    }

    private String consume(String key, String prefix) {
        try { return stringRedisTemplate.execute(CONSUME, List.of(key), prefix); }
        catch (RuntimeException failure) { throw invalid(); }
    }

    private void store(String key, String value, Duration ttl) {
        try { stringRedisTemplate.opsForValue().set(key, value, ttl); }
        catch (RuntimeException failure) { throw invalid(); }
    }

    private static String cookie(HttpServletRequest request, String state) {
        if (request != null && request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ((COOKIE_PREFIX + state).equals(cookie.getName()) && cookie.getValue().matches("[0-9a-f]{64}")) return cookie.getValue();
            }
        }
        throw invalid();
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes]; RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static BadCredentialsException invalid() { return new BadCredentialsException(FAILURE); }
}
