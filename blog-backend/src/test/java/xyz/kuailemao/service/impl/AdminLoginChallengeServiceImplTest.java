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
import xyz.kuailemao.service.EmailDeliveryService;
import xyz.kuailemao.service.AdminLoginDeliveryException;
import xyz.kuailemao.service.UserService;
import xyz.kuailemao.service.AccountAuthenticationVersion;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.anyLong;

@ExtendWith(MockitoExtension.class)
class AdminLoginChallengeServiceImplTest {
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private HashOperations<String, String, String> hashOperations;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private EmailDeliveryService emailDeliveryService;
    @Mock private UserService userService;
    @Mock private AccountAuthenticationVersion authenticationVersion;

    private AdminLoginChallengeServiceImpl service;
    private LoginUser administrator;

    @BeforeEach
    void setUp() {
        service = new AdminLoginChallengeServiceImpl();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "rabbitTemplate", rabbitTemplate);
        ReflectionTestUtils.setField(service, "emailDeliveryService", emailDeliveryService);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "authenticationVersion", authenticationVersion);
        org.mockito.Mockito.lenient().when(authenticationVersion.snapshotVersion(7L)).thenReturn("a".repeat(64));
        org.mockito.Mockito.lenient().when(authenticationVersion.credentialSnapshot(any())).thenReturn("snapshot");
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
        when(emailDeliveryService.create(eq(1), anyList())).thenReturn("b".repeat(64));

        var result = service.create(administrator, "203.0.113.2");

        assertEquals("a***@example.com", result.getMaskedEmail());
        assertEquals("b".repeat(64), result.getTaskId());
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
        assertThrows(AdminLoginDeliveryException.class, () -> service.create(administrator, "203.0.113.2"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verificationUsesOneAtomicRedisScriptAndCannotReuseConsumedChallenge() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username", "admin", "userId", "7", "version", "a".repeat(64), "credentials", "snapshot"));
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

    @Test
    @SuppressWarnings("unchecked")
    void credentialChangeRejectsPendingVerificationAndResend() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username", "admin", "userId", "7", "version", "a".repeat(64), "credentials", "old-snapshot"));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(userService.loadUserByUsername("admin")).thenReturn(administrator);
        assertThrows(BadCredentialsException.class, () -> service.verify("old", "123456", "203.0.113.2"));
        assertThrows(BadCredentialsException.class, () -> service.resend("old", "203.0.113.2"));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotatedVersionRejectsChallengeEvenWhenCredentialsReturnToOriginalValues() {
        when(stringRedisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username", "admin", "userId", "7", "version", "b".repeat(64), "credentials", "snapshot"));
        when(stringRedisTemplate.execute(any(RedisScript.class), anyList(), any(), any())).thenReturn(1L);
        when(userService.loadUserByUsername("admin")).thenReturn(administrator);
        assertThrows(BadCredentialsException.class, () -> service.verify("old", "123456", "203.0.113.2"));
    }
    @Test void cooldownReadsRemainingMillisecondsWithoutSendingOrExtending() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        when(stringRedisTemplate.getExpire(anyString(),eq(java.util.concurrent.TimeUnit.MILLISECONDS))).thenReturn(12001L);
        var failure=assertThrows(AdminLoginDeliveryException.class,()->service.create(administrator,"fixture"));
        assertEquals(13L,failure.response().getData().retryAfterSeconds());
        verify(stringRedisTemplate,never()).expire(anyString(),anyLong(),any());
        verifyNoInteractions(emailDeliveryService);
    }
    @Test void unknownTtlNeverProducesNegativeOrInventedRetryDuration() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        for(Long ttl:java.util.Arrays.asList(null,-2L,-1L,0L)) {
            when(stringRedisTemplate.getExpire(anyString(),eq(java.util.concurrent.TimeUnit.MILLISECONDS))).thenReturn(ttl);
            var failure=assertThrows(AdminLoginDeliveryException.class,()->service.create(administrator,"fixture"));
            org.junit.jupiter.api.Assertions.assertNull(failure.response().getData().retryAfterSeconds());
            assertEquals("验证码发送过于频繁，请稍后重试",failure.response().getMsg());
        }
        verifyNoInteractions(emailDeliveryService);
    }
    @Test void publishingFailureCleansNewChallengeAndCooldownButNeverDeletesOldChallenge() {
        when(stringRedisTemplate.<String,String>opsForHash()).thenReturn(hashOperations);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(emailDeliveryService.create(eq(1),anyList())).thenReturn("fixture-task");
        org.mockito.Mockito.doThrow(new IllegalStateException("private upstream error")).when(emailDeliveryService).publish(anyString(),any());
        var failure=assertThrows(AdminLoginDeliveryException.class,()->service.create(administrator,"fixture"));
        assertEquals("CODE_SEND_UNAVAILABLE",failure.response().getData().reason().name());
        verify(stringRedisTemplate).delete(org.mockito.ArgumentMatchers.<java.util.Collection<String>>argThat(keys -> keys.size()==2
            && keys.contains("auth:admin:resend:7") && keys.stream().noneMatch(k->k.equals("auth:admin:challenge:old"))));
    }
    @Test @SuppressWarnings("unchecked") void resendDuringCooldownPreservesOldChallengeAndItCanStillCompleteOnce() {
        when(stringRedisTemplate.<String,String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries(anyString())).thenReturn(Map.of("username","admin","userId","7","version","a".repeat(64),"credentials","snapshot"));
        when(userService.loadUserByUsername("admin")).thenReturn(administrator);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);
        assertThrows(AdminLoginDeliveryException.class,()->service.resend("old","fixture"));
        verify(stringRedisTemplate,never()).delete(anyString());
        verifyNoInteractions(emailDeliveryService);
        when(stringRedisTemplate.execute(any(RedisScript.class),anyList(),any(),any())).thenReturn(1L,-1L);
        assertEquals(administrator,service.verify("old","123456","fixture"));
        assertThrows(BadCredentialsException.class,()->service.verify("old","123456","fixture"));
    }
    @Test void resendPublishFailurePreservesOldChallengeAndDoesNotRefreshItsExpiry() {
        when(stringRedisTemplate.<String,String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("auth:admin:challenge:old")).thenReturn(Map.of("username","admin","userId","7","version","a".repeat(64),"credentials","snapshot"));
        when(userService.loadUserByUsername("admin")).thenReturn(administrator);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(false);
        when(emailDeliveryService.create(eq(1),anyList())).thenReturn("fixture-task");
        org.mockito.Mockito.doThrow(new IllegalStateException("private failure")).when(emailDeliveryService).publish(anyString(),any());
        assertThrows(AdminLoginDeliveryException.class,()->service.resend("old","fixture"));
        verify(stringRedisTemplate,never()).delete("auth:admin:challenge:old");
        verify(stringRedisTemplate,never()).expire(eq("auth:admin:challenge:old"),anyLong(),any());
        verify(stringRedisTemplate).delete(org.mockito.ArgumentMatchers.<java.util.Collection<String>>argThat(keys->
            keys.size()==2 && !keys.contains("auth:admin:challenge:old")));
    }
}
