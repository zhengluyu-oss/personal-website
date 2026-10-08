package xyz.kuailemao.service;

import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Independent, non-reusable account epoch. Never initialize it while validating an existing JWT. */
@Service
public class AccountAuthenticationVersion {
    public static final String ABSENT = "-";
    private static final String PREFIX = "auth:account-version:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<String> INITIALIZE = new DefaultRedisScript<>("""
            local value = redis.call('GET', KEYS[1])
            if value then
              if ARGV[2] == 'missing-only' then return nil end
              return value
            end
            redis.call('SET', KEYS[1], ARGV[1])
            return ARGV[1]
            """, String.class);
    private static final DefaultRedisScript<String> ROTATE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return nil end
            redis.call('SET', KEYS[1], ARGV[2])
            return ARGV[2]
            """, String.class);
    @Resource private StringRedisTemplate stringRedisTemplate;

    public String current(Long userId) {
        try {
            String value = stringRedisTemplate.opsForValue().get(key(userId));
            if (value == null) return null;
            return checked(value);
        } catch (RuntimeException failure) { throw unavailable(); }
    }

    public String forCompletedLogin(Long userId, String expected) {
        if (expected != null && !ABSENT.equals(expected)) {
            if (!Objects.equals(expected, current(userId))) throw unavailable();
            return checked(expected);
        }
        try {
            return checked(stringRedisTemplate.execute(INITIALIZE, List.of(key(userId)), random(),
                    ABSENT.equals(expected) ? "missing-only" : "normal"));
        } catch (RuntimeException failure) { throw unavailable(); }
    }

    public String rotate(Long userId, String expected) {
        checked(expected);
        try {
            return checked(stringRedisTemplate.execute(ROTATE, List.of(key(userId)), expected, random()));
        } catch (RuntimeException failure) { throw unavailable(); }
    }

    public String snapshotVersion(Long userId) {
        String version = current(userId);
        return version == null ? ABSENT : version;
    }

    /** Binds pending challenges to the precise account credentials, without retaining their values. */
    public String credentialSnapshot(User user) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object field : new Object[]{user.getId(), user.getPassword(), user.getEmail(), user.getRegisterType()}) {
                String value = field == null ? "<null>" : field.toString();
                digest.update((value.length() + ":" + value).getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private String key(Long userId) {
        if (userId == null || userId <= 0) throw unavailable();
        return PREFIX + userId;
    }
    private String checked(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw unavailable();
        return value;
    }
    private String random() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes); return HexFormat.of().formatHex(bytes);
    }
    private BadCredentialsException unavailable() { return new BadCredentialsException("登录状态已失效，请重新登录"); }
}
