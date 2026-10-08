package xyz.kuailemao.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import xyz.kuailemao.domain.dto.EmailChangeStartDTO;
import xyz.kuailemao.domain.dto.UpdateEmailDTO;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.utils.SecurityUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

/** All email-write routes share this single-use, existing-identity proof boundary. */
@Service
public class EmailChangeService {
    private static final String PREFIX = "auth:email-change:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String FAILURE = "邮箱安全验证失败或已过期，请重新发起；原邮箱不可用时请联系站点管理员恢复";
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
            for i=1,#ARGV,2 do redis.call('HSET',KEYS[1],ARGV[i],ARGV[i+1]) end
            redis.call('EXPIRE',KEYS[1],300)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            if redis.call('HGET',KEYS[1],'user') ~= ARGV[1]
              or redis.call('HGET',KEYS[1],'version') ~= ARGV[2]
              or redis.call('HGET',KEYS[1],'credentials') ~= ARGV[3]
              or redis.call('HGET',KEYS[1],'email') ~= ARGV[4]
              or redis.call('HGET',KEYS[1],'purpose') ~= 'email-change' then return 0 end
            if redis.call('HGET',KEYS[1],'providerVerified') ~= '1'
              or redis.call('HGET',KEYS[1],'newCode') ~= ARGV[5]
              or redis.call('HGET',KEYS[1],'oldCode') ~= ARGV[6] then
              if redis.call('HINCRBY',KEYS[1],'attempts',1) >= 5 then redis.call('DEL',KEYS[1]) end
              return 0
            end
            redis.call('DEL',KEYS[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> PROVIDER = new DefaultRedisScript<>("""
            if redis.call('HGET',KEYS[1],'user') ~= ARGV[1]
              or redis.call('HGET',KEYS[1],'provider') ~= ARGV[2]
              or redis.call('HGET',KEYS[1],'version') ~= ARGV[3]
              or redis.call('HGET',KEYS[1],'credentials') ~= ARGV[4]
              or redis.call('HGET',KEYS[1],'purpose') ~= 'email-change' then return 0 end
            redis.call('HSET',KEYS[1],'providerVerified','1')
            return 1
            """, Long.class);
    @Resource private UserMapper userMapper;
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private AccountAuthenticationVersion authenticationVersion;
    @Resource private PasswordEncoder passwordEncoder;
    @Resource private RabbitTemplate rabbitTemplate;
    @Value("${spring.rabbitmq.exchange.email}") private String exchange;
    @Value("${spring.rabbitmq.routingKey.email}") private String routingKey;

    public record Challenge(String challengeId, int expiresIn, boolean oldEmailRequired, String maskedOldEmail, int provider) {}

    public Challenge start(EmailChangeStartDTO dto) {
        User user = authenticatedAccount();
        String email = dto.getEmail();
        if (email == null || email.length() > 254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                || email.equalsIgnoreCase(Objects.toString(user.getEmail(), ""))) throw invalid();
        int provider = user.getRegisterType();
        if (provider == 0 && (dto.getPassword() == null || dto.getPassword().length() > 128
                || !passwordEncoder.matches(dto.getPassword(), user.getPassword()))) throw invalid();
        boolean oldRequired = oldRequired(user);
        if (oldRequired && (user.getEmail() == null || user.getEmail().isBlank())) throw invalid();
        String version = ((LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getAuthenticationVersion();
        String id = random();
        String code = code(), oldCode = oldRequired ? code() : "";
        String key = key(id);
        try {
            if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                    PREFIX + "cooldown:" + user.getId(), "1", Duration.ofSeconds(60)))) throw invalid();
            Long created = stringRedisTemplate.execute(CREATE, List.of(key),
                    "user", user.getId().toString(), "email", email, "purpose", "email-change",
                    "version", version, "credentials", authenticationVersion.credentialSnapshot(user),
                    "provider", Integer.toString(provider), "providerVerified", provider == 0 ? "1" : "0",
                    "oldRequired", oldRequired ? "1" : "0", "attempts", "0",
                    "newCode", codeHash(id, code), "oldCode", oldRequired ? codeHash(id, oldCode) : "-");
            if (!Long.valueOf(1).equals(created)) throw invalid();
            sendCode(email, code);
            if (oldRequired) sendCode(user.getEmail(), oldCode);
        } catch (RuntimeException failure) {
            try { stringRedisTemplate.delete(key); } catch (RuntimeException ignored) { /* Fail closed. */ }
            throw invalid();
        }
        return new Challenge(id, 300, oldRequired, oldRequired ? mask(user.getEmail()) : "", provider);
    }

    public int requiredProvider(String id) {
        User user = authenticatedAccount();
        validateSnapshot(id, user);
        if (user.getRegisterType() == 0) throw invalid();
        return user.getRegisterType();
    }

    /** Called only after browser-bound provider callback validation, never by a public DTO. */
    public void verifyProvider(String id, int provider, long providerIdentity) {
        User user = activeAccount(providerIdentity);
        if (!Objects.equals(user.getRegisterType(), provider) || provider == 0) throw invalid();
        try {
            Long ok = stringRedisTemplate.execute(PROVIDER, List.of(key(id)), Long.toString(providerIdentity),
                    Integer.toString(provider), authenticationVersion.current(user.getId()),
                    authenticationVersion.credentialSnapshot(user));
            if (!Long.valueOf(1).equals(ok)) throw invalid();
        } catch (RuntimeException failure) { throw invalid(); }
    }

    public ResponseResult<Void> complete(UpdateEmailDTO dto) {
        User user = authenticatedAccount();
        String id = dto.getChallengeId();
        Map<Object, Object> state = validateSnapshot(id, user);
        String version = authenticationVersion.current(user.getId());
        if (!Objects.equals(state.get("email"), dto.getEmail())) throw invalid();
        try {
            Long consumed = stringRedisTemplate.execute(CONSUME, List.of(key(id)), user.getId().toString(),
                    version, authenticationVersion.credentialSnapshot(user), dto.getEmail(),
                    codeHash(id, dto.getCode()), oldRequired(user) ? codeHash(id, dto.getOldCode()) : "-");
            if (!Long.valueOf(1).equals(consumed)) throw invalid();
        } catch (RuntimeException failure) { throw invalid(); }
        // Revoke before DB write: a failed update must never resurrect old tokens/challenges.
        authenticationVersion.rotate(user.getId(), version);
        LambdaUpdateWrapper<User> update = new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId()).eq(User::getRegisterType, user.getRegisterType())
                .eq(User::getIsDeleted, 0).eq(User::getIsDisable, 0)
                .set(User::getEmail, dto.getEmail());
        if (user.getEmail() == null) update.isNull(User::getEmail); else update.eq(User::getEmail, user.getEmail());
        if (user.getPassword() == null) update.isNull(User::getPassword); else update.eq(User::getPassword, user.getPassword());
        try {
            if (userMapper.update(null, update) != 1) return ResponseResult.failure("账号状态已改变，请重新登录后验证");
            return ResponseResult.success();
        } catch (DataAccessException conflict) {
            return ResponseResult.failure("邮箱更新未完成，地址可能已被占用；请重新登录后验证");
        }
    }

    private Map<Object, Object> validateSnapshot(String id, User user) {
        try {
            Map<Object, Object> state = stringRedisTemplate.opsForHash().entries(key(id));
            if (!Objects.equals(state.get("user"), user.getId().toString())
                    || !Objects.equals(state.get("purpose"), "email-change")
                    || !Objects.equals(state.get("version"), authenticationVersion.current(user.getId()))
                    || !Objects.equals(state.get("credentials"), authenticationVersion.credentialSnapshot(user))
                    || !Objects.equals(state.get("oldRequired"), oldRequired(user) ? "1" : "0")) throw invalid();
            return state;
        } catch (RuntimeException failure) { throw invalid(); }
    }

    private User authenticatedAccount() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof LoginUser login)) throw invalid();
        User user = activeAccount(login.getUser().getId());
        String version = authenticationVersion.current(user.getId());
        if (version == null || !Objects.equals(login.getAuthenticationVersion(), version)) throw invalid();
        return user;
    }
    private User activeAccount(long id) {
        User user = userMapper.selectById(id);
        if (user == null || !Objects.equals(user.getIsDisable(), 0) || !Objects.equals(user.getIsDeleted(), 0)
                || user.getRegisterType() == null || user.getRegisterType() < 0 || user.getRegisterType() > 2) throw invalid();
        return user;
    }
    private boolean oldRequired(User user) {
        return SecurityUtils.getUserRoles().contains("ROLE_ADMIN")
                || (user.getRegisterType() != 0 && user.getEmail() != null && !user.getEmail().isBlank());
    }
    private void sendCode(String email, String code) {
        rabbitTemplate.convertAndSend(exchange, routingKey, Map.of("email", email, "code", code, "type", "resetEmail"));
    }
    private static String mask(String email) { int at = email.indexOf('@'); return at < 1 ? "***" : email.substring(0, 1) + "***" + email.substring(at); }
    private static String code() { return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000)); }
    private static String random() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return HexFormat.of().formatHex(b); }
    private static String key(String id) { if (id == null || !id.matches("[0-9a-f]{64}")) throw invalid(); return PREFIX + OauthBrowserBinding.digest(id); }
    private static String codeHash(String id, String code) { return OauthBrowserBinding.digest(id + ":" + Objects.toString(code, "")); }
    private static BadCredentialsException invalid() { return new BadCredentialsException(FAILURE); }
}
