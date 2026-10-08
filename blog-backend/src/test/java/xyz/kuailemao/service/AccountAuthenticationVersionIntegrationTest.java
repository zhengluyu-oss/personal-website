package xyz.kuailemao.service;

import com.auth0.jwt.JWT;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.mapper.UserRoleMapper;
import xyz.kuailemao.utils.JwtUtils;
import xyz.kuailemao.utils.RedisCache;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Opt-in, fixed loopback QA Redis only; never loads application/private configuration. */
@EnabledIfEnvironmentVariable(named = "BLOG_SECURITY_QA_REDIS", matches = "true")
class AccountAuthenticationVersionIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private AccountAuthenticationVersion versions;
    private Long userId;

    @BeforeEach void setup() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", 16379);
        config.setDatabase(15);
        factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        versions = new AccountAuthenticationVersion();
        ReflectionTestUtils.setField(versions, "stringRedisTemplate", redis);
        userId = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
    }
    @AfterEach void cleanup() {
        try { redis.delete("auth:account-version:v1:" + userId); }
        finally { factory.destroy(); }
    }

    @Test void initializeAndRotateAreAtomicAndNeverReuseEpochs() throws Exception {
        assertNull(versions.current(userId));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<String> login = () -> versions.forCompletedLogin(userId, null);
            var logins = executor.invokeAll(List.of(login, login));
            String first = logins.get(0).get();
            assertEquals(first, logins.get(1).get()); assertTrue(first.matches("[0-9a-f]{64}"));
            Callable<Boolean> rotate = () -> { try { versions.rotate(userId, first); return true; } catch (BadCredentialsException failure) { return false; } };
            var rotations = executor.invokeAll(List.of(rotate, rotate));
            assertEquals(1, rotations.stream().filter(result -> { try { return result.get(); } catch (Exception e) { throw new AssertionError(e); } }).count());
            assertNotEquals(first, versions.current(userId));
            assertThrows(BadCredentialsException.class, () -> versions.forCompletedLogin(userId, first));
        } finally { executor.shutdownNow(); }
    }

    @Test void revokedJwtCannotReviveAfterEmailRestorationStateLossOrFailedUpdate() {
        User account = new User().setId(userId).setUsername("qa").setPassword("local-hash")
                .setEmail("original@example.invalid").setRegisterType(0).setIsDeleted(0).setIsDisable(0);
        JwtUtils jwt = new JwtUtils(); UserMapper users = mock(UserMapper.class); UserRoleMapper roles = mock(UserRoleMapper.class);
        ReflectionTestUtils.setField(jwt, "key", "local-test-signing-key");
        ReflectionTestUtils.setField(jwt, "expire", 7);
        ReflectionTestUtils.setField(jwt, "redisCache", mock(RedisCache.class));
        ReflectionTestUtils.setField(jwt, "authenticationVersion", versions);
        ReflectionTestUtils.setField(jwt, "userMapper", users); ReflectionTestUtils.setField(jwt, "userRoleMapper", roles);
        when(users.selectById(userId)).thenReturn(account); when(roles.selectList(any())).thenReturn(List.of());
        LoginUser login = new LoginUser(account).setAuthorities(List.of());
        var old = JWT.decode(jwt.createJwt("qa-old", login, userId, "qa"));
        assertNotNull(jwt.toUser(old));
        versions.rotate(userId, versions.current(userId));
        // A database update failure keeps the original email but MUST NOT restore old sessions.
        assertNull(jwt.toUser(old));
        account.setEmail("new@example.invalid");
        assertNull(jwt.toUser(old));
        versions.rotate(userId, versions.current(userId));
        account.setEmail("original@example.invalid");
        assertNull(jwt.toUser(old));
        var fresh = JWT.decode(jwt.createJwt("qa-new", login, userId, "qa"));
        assertNotNull(jwt.toUser(fresh));
        redis.delete("auth:account-version:v1:" + userId);
        assertNull(jwt.toUser(fresh)); assertNull(versions.current(userId));
        var newest = JWT.decode(jwt.createJwt("qa-newest", login, userId, "qa"));
        assertNotNull(jwt.toUser(newest)); assertNull(jwt.toUser(fresh)); assertNull(jwt.toUser(old));
    }

    @Test void pendingInitialChallengeCannotInitializeOverAnotherEpoch() {
        assertEquals(AccountAuthenticationVersion.ABSENT, versions.snapshotVersion(userId));
        String first = versions.forCompletedLogin(userId, AccountAuthenticationVersion.ABSENT);
        assertThrows(BadCredentialsException.class,
                () -> versions.forCompletedLogin(userId, AccountAuthenticationVersion.ABSENT));
        assertEquals(first, versions.current(userId));
    }

    @Test void malformedStoredStateAndRedisFailureAreControlled() {
        redis.opsForValue().set("auth:account-version:v1:" + userId, "malformed");
        assertThrows(BadCredentialsException.class, () -> versions.current(userId));
        StringRedisTemplate failing = mock(StringRedisTemplate.class);
        when(failing.opsForValue()).thenThrow(new IllegalStateException("private diagnostic"));
        ReflectionTestUtils.setField(versions, "stringRedisTemplate", failing);
        var failure = assertThrows(BadCredentialsException.class, () -> versions.current(userId));
        assertFalse(failure.getMessage().contains("private diagnostic"));
    }
}
