package xyz.kuailemao.service.impl;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import xyz.kuailemao.constants.RedisConst;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.vo.AdminLoginChallengeVO;
import xyz.kuailemao.service.AdminLoginChallengeService;
import xyz.kuailemao.service.UserService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class AdminLoginChallengeServiceImpl implements AdminLoginChallengeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String GENERIC_ERROR = "登录验证失败，请重新登录";
    private static final DefaultRedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts'))
            local maxAttempts = tonumber(ARGV[2])
            if not attempts or not maxAttempts or maxAttempts <= 0 then
              redis.call('DEL', KEYS[1]); return -3
            end
            if attempts >= maxAttempts then redis.call('DEL', KEYS[1]); return -2 end
            if redis.call('HGET', KEYS[1], 'codeHash') ~= ARGV[1] then
              attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
              if attempts >= maxAttempts then redis.call('DEL', KEYS[1]) end
              return 0
            end
            redis.call('DEL', KEYS[1]); return 1
            """, Long.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private UserService userService;

    @Value("${spring.rabbitmq.routingKey.email}")
    private String routingKey;
    @Value("${spring.rabbitmq.exchange.email}")
    private String exchange;

    @Override
    public AdminLoginChallengeVO create(LoginUser loginUser, String clientAddress) {
        String email = loginUser.getUser().getEmail();
        if (email == null || email.isBlank()) {
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        String resendKey = RedisConst.ADMIN_LOGIN_RESEND + loginUser.getUser().getId();
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(resendKey))) {
            throw new BadCredentialsException("验证码发送过于频繁，请稍后重试");
        }
        String challengeId = UUID.randomUUID().toString();
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = RedisConst.ADMIN_LOGIN_CHALLENGE + challengeId;
        challengeHash().putAll(key, Map.of(
                "userId", loginUser.getUser().getId().toString(),
                "username", loginUser.getUsername(),
                "codeHash", sha256(code),
                "attempts", "0",
                "client", clientAddress == null ? "unknown" : clientAddress
        ));
        stringRedisTemplate.expire(key, RedisConst.ADMIN_LOGIN_CHALLENGE_MINUTES, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(resendKey, "1", RedisConst.ADMIN_LOGIN_RESEND_SECONDS, TimeUnit.SECONDS);
        rabbitTemplate.convertAndSend(exchange, routingKey, Map.of("email", email, "code", code, "type", "adminLogin"));
        log.info("Administrator second-factor challenge created: userId={}, client={}", loginUser.getUser().getId(), clientAddress);
        return new AdminLoginChallengeVO(challengeId, maskEmail(email),
                RedisConst.ADMIN_LOGIN_CHALLENGE_MINUTES * 60,
                RedisConst.ADMIN_LOGIN_RESEND_SECONDS, true);
    }

    @Override
    public LoginUser verify(String challengeId, String code, String clientAddress) {
        String key = RedisConst.ADMIN_LOGIN_CHALLENGE + challengeId;
        Map<String, String> challenge = challengeHash().entries(key);
        if (challenge.isEmpty()) throw new BadCredentialsException(GENERIC_ERROR);
        Long result = stringRedisTemplate.execute(
                VERIFY_SCRIPT,
                List.of(key),
                sha256(code),
                String.valueOf(RedisConst.ADMIN_LOGIN_MAX_ATTEMPTS)
        );
        if (!Long.valueOf(1).equals(result)) {
            if (Long.valueOf(-2).equals(result)) {
                log.warn("Administrator second-factor exhausted: challengeId={}, client={}", challengeId, clientAddress);
            } else if (Long.valueOf(-3).equals(result)) {
                log.warn("Administrator second-factor invalid challenge state: challengeId={}, client={}", challengeId, clientAddress);
            } else {
                log.warn("Administrator second-factor rejected: challengeId={}, client={}", challengeId, clientAddress);
            }
            throw new BadCredentialsException(GENERIC_ERROR);
        }
        LoginUser user = (LoginUser) userService.loadUserByUsername(String.valueOf(challenge.get("username")));
        boolean admin = user.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) throw new BadCredentialsException(GENERIC_ERROR);
        log.info("Administrator second-factor accepted: userId={}, client={}", challenge.get("userId"), clientAddress);
        return user;
    }

    @Override
    public AdminLoginChallengeVO resend(String challengeId, String clientAddress) {
        String oldKey = RedisConst.ADMIN_LOGIN_CHALLENGE + challengeId;
        Map<String, String> challenge = challengeHash().entries(oldKey);
        if (challenge.isEmpty()) throw new BadCredentialsException(GENERIC_ERROR);
        LoginUser user = (LoginUser) userService.loadUserByUsername(String.valueOf(challenge.get("username")));
        boolean admin = user.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) throw new BadCredentialsException(GENERIC_ERROR);
        AdminLoginChallengeVO replacement = create(user, clientAddress);
        stringRedisTemplate.delete(oldKey);
        log.info("Administrator second-factor challenge resent: userId={}, client={}", user.getUser().getId(), clientAddress);
        return replacement;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private HashOperations<String, String, String> challengeHash() {
        return stringRedisTemplate.opsForHash();
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(Math.max(at, 0));
        return email.substring(0, 1) + "***" + email.substring(at);
    }
}
