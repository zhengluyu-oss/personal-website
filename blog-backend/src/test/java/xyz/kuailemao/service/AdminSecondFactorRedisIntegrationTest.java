package xyz.kuailemao.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.constants.RedisConst;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.service.impl.AdminLoginChallengeServiceImpl;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Fixed disposable loopback Redis; outgoing mail and database lookups are doubles. */
@EnabledIfEnvironmentVariable(named="BLOG_SECURITY_QA_REDIS", matches="true")
class AdminSecondFactorRedisIntegrationTest {
    LettuceConnectionFactory factory;
    StringRedisTemplate redis;
    AccountAuthenticationVersion versions;
    AdminLoginChallengeServiceImpl service;
    LoginUser user;
    Map<String, Object> mail;
    List<String> keys;

    @BeforeEach void setup() {
        var configuration = new RedisStandaloneConfiguration("127.0.0.1", 16379); configuration.setDatabase(15);
        factory = new LettuceConnectionFactory(configuration); factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory); keys = new ArrayList<>();
        versions = new AccountAuthenticationVersion(); ReflectionTestUtils.setField(versions, "stringRedisTemplate", redis);
        long id = ThreadLocalRandom.current().nextLong(1_000_000_000L, Long.MAX_VALUE);
        user = new LoginUser(new User().setId(id).setUsername("qa-admin-" + id).setEmail("admin@example.invalid")
                .setPassword("synthetic-hash").setRegisterType(0).setIsDeleted(0).setIsDisable(0))
                .setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        service = new AdminLoginChallengeServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
        ReflectionTestUtils.setField(service, "authenticationVersion", versions);
        var users = mock(UserService.class); when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        ReflectionTestUtils.setField(service, "userService", users);
        var rabbit = mock(RabbitTemplate.class);
        doAnswer(call -> { mail = call.getArgument(2); return null; }).when(rabbit).convertAndSend(eq("qa"), eq("qa"), any(Object.class));
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(service, "exchange", "qa"); ReflectionTestUtils.setField(service, "routingKey", "qa");
        keys.add(RedisConst.ADMIN_LOGIN_RESEND + id); keys.add("auth:account-version:v1:" + id);
    }
    @AfterEach void cleanup() { try { redis.delete(keys); } finally { factory.destroy(); } }
    String create() {
        String id = service.create(user, "192.0.2.12").getChallengeId();
        keys.add(RedisConst.ADMIN_LOGIN_CHALLENGE + id); return id;
    }
    @Test void realLuaAcceptsPlainStringHashAndConsumesOnlyOnce() {
        String id = create(); String key = RedisConst.ADMIN_LOGIN_CHALLENGE + id;
        assertEquals("0", redis.opsForHash().get(key, "attempts"));
        assertTrue(redis.getExpire(key) > 0);
        String code = (String) mail.get("code");
        assertEquals(user, service.verify(id, code, "192.0.2.12"));
        assertFalse(redis.hasKey(key));
        assertThrows(BadCredentialsException.class, () -> service.verify(id, code, "192.0.2.12"));
    }
    @Test void legacyJsonEncodedAttemptCounterFailsClosed() {
        String id = create(); String key = RedisConst.ADMIN_LOGIN_CHALLENGE + id;
        redis.opsForHash().put(key, "attempts", "\"0\"");
        assertThrows(BadCredentialsException.class, () -> service.verify(id, (String) mail.get("code"), "192.0.2.12"));
        assertFalse(redis.hasKey(key));
    }
    @Test void epochRotationRejectsPendingSecondFactorAndResend() {
        String current = versions.forCompletedLogin(user.getUser().getId(), null);
        String id = create(); versions.rotate(user.getUser().getId(), current);
        assertThrows(BadCredentialsException.class, () -> service.resend(id, "192.0.2.12"));
        assertThrows(BadCredentialsException.class, () -> service.verify(id, (String) mail.get("code"), "192.0.2.12"));
    }
}
