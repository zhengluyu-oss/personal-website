package xyz.kuailemao.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.service.UserService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminLoginChallengeServiceImplTest {
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private HashOperations<String, String, String> hashOperations;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private UserService userService;

    private AdminLoginChallengeServiceImpl service;
    private LoginUser administrator;

    @BeforeEach
    void setUp() {
        service = new AdminLoginChallengeServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "exchange", "email");
        ReflectionTestUtils.setField(service, "routingKey", "admin");
        User account = User.builder().id(7L).username("admin").password("original-hash").email("admin@example.com").build();
        administrator = new LoginUser(account);
        administrator.setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void createMasksDestinationStoresNoPasswordAndKeepsPasswordHashUnchanged() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);

        var result = service.create(administrator, "203.0.113.2");

        assertEquals("a***@example.com", result.getMaskedEmail());
        assertEquals(300, result.getExpiresIn());
        assertEquals("original-hash", administrator.getPassword());
        verify(hashOperations).putAll(anyString(), org.mockito.ArgumentMatchers.argThat(values ->
                !values.containsKey("password") && !values.containsKey("code")));
    }

    @Test
    void expiredChallengeAndActiveResendCooldownAreRejected() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of());
        assertThrows(BadCredentialsException.class, () -> service.verify("expired", "123456", "203.0.113.2"));

        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        assertThrows(BadCredentialsException.class, () -> service.create(administrator, "203.0.113.2"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verificationUsesOneAtomicRedisScriptAndCannotReuseConsumedChallenge() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username", "admin"));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L, -1L);
        when(userService.loadUserByUsername("admin")).thenReturn(administrator);

        assertEquals(administrator, service.verify("one-use", "123456", "203.0.113.2"));
        assertThrows(BadCredentialsException.class, () -> service.verify("one-use", "123456", "203.0.113.2"));
        verify(userService).loadUserByUsername("admin");
    }

    @Test
    @SuppressWarnings("unchecked")
    void incorrectCodeResultNeverLoadsAccountOrIssuesSession() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username", "admin"));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        assertThrows(BadCredentialsException.class, () -> service.verify("limited", "000000", "203.0.113.2"));
        verify(userService, never()).loadUserByUsername(anyString());
        assertTrue(administrator.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verificationPassesPlainNumericLimitAndDigestToRedisScript() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username", "admin"));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(0L);

        assertThrows(BadCredentialsException.class,
                () -> service.verify("plain-strings", "123456", "203.0.113.2"));

        verify(stringRedisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                argThat(value -> value instanceof String && ((String) value).matches("[0-9a-f]{64}")),
                eq("5")
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void exhaustedAndMalformedChallengeStatesFailClosedWithoutLoadingAccount() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username", "admin"));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(-2L, -3L);

        assertThrows(BadCredentialsException.class,
                () -> service.verify("exhausted", "000000", "203.0.113.2"));
        assertThrows(BadCredentialsException.class,
                () -> service.verify("malformed", "000000", "203.0.113.2"));
        verify(userService, never()).loadUserByUsername(anyString());
    }
}
