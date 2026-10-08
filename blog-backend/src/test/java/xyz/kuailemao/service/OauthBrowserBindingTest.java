package xyz.kuailemao.service;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OauthBrowserBindingTest {
    private OauthBrowserBinding service;
    private StringRedisTemplate redis;
    private final Map<String, String> state = new ConcurrentHashMap<>();
    private final Map<String, Duration> ttl = new ConcurrentHashMap<>();

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        service = new OauthBrowserBinding();
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(call -> {
            state.put(call.getArgument(0), call.getArgument(1));
            ttl.put(call.getArgument(0), call.getArgument(2)); return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(call -> {
            // Atomic store double; integration deployment must also verify actual Redis Lua execution.
            synchronized (state) {
                List<String> keys = call.getArgument(1);
                String prefix = (String) call.getArguments()[2];
                String value = state.get(keys.get(0));
                return value != null && value.startsWith(prefix) ? state.remove(keys.get(0)) : null;
            }
        });
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redis);
    }

    private record Attempt(String state, Cookie cookie) {}
    private Attempt begin(int provider) {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setServerName("example.invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String state = service.begin(request, response, provider, "https://example.invalid/api/oauth/" + (provider == 1 ? "gitee" : "github") + "/callback");
        String header = response.getHeader("Set-Cookie");
        assertNotNull(header); assertTrue(header.contains("HttpOnly")); assertTrue(header.contains("Secure"));
        assertTrue(header.contains("SameSite=Lax")); assertTrue(header.contains("Path=/api/oauth/")); assertFalse(header.contains("Domain="));
        String[] pair = header.split(";", 2)[0].split("=", 2);
        return new Attempt(state, new Cookie(pair[0], pair[1]));
    }
    private MockHttpServletRequest browser(Attempt attempt) {
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setCookies(attempt.cookie()); return request;
    }

    @Test void otherBrowserCannotConsumeStateOrExchangeAndReplayFails() {
        Attempt attempt = begin(2);
        MockHttpServletRequest attacker = browser(attempt);
        attacker.setCookies(new Cookie(attempt.cookie().getName(), "f".repeat(64)));
        assertThrows(BadCredentialsException.class, () -> service.validateCallback(attacker, attempt.state(), 2));
        MockHttpServletRequest valid = browser(attempt);
        service.validateCallback(valid, attempt.state(), 2);
        String code = service.issue(valid, 2, 42);
        assertEquals(Duration.ofMinutes(2), ttl.get(OauthBrowserBinding.EXCHANGE_PREFIX + OauthBrowserBinding.digest(code)));
        assertThrows(BadCredentialsException.class, () -> service.exchange(attacker, code));
        assertEquals("2:42", service.exchange(valid, code));
        assertThrows(BadCredentialsException.class, () -> service.exchange(valid, code));
    }

    @Test void wrongProviderExpiredStateAndUnvalidatedIssueFailClosed() {
        Attempt attempt = begin(1); MockHttpServletRequest request = browser(attempt);
        assertThrows(BadCredentialsException.class, () -> service.validateCallback(request, attempt.state(), 2));
        assertThrows(BadCredentialsException.class, () -> service.issue(request, 1, 42));
        state.clear();
        assertThrows(BadCredentialsException.class, () -> service.validateCallback(request, attempt.state(), 1));
        assertThrows(BadCredentialsException.class, () -> service.exchange(request, "bad"));
    }

    @Test void separateTabsKeepIndependentAttemptsAndConcurrentExchangeSucceedsOnce() throws Exception {
        Attempt first = begin(1), second = begin(2);
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setCookies(first.cookie(), second.cookie());
        service.validateCallback(request, first.state(), 1);
        String firstCode = service.issue(request, 1, 42);
        service.validateCallback(request, second.state(), 2);
        String secondCode = service.issue(request, 2, 43);
        assertEquals("2:43", service.exchange(request, secondCode));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> exchange = () -> { try { service.exchange(request, firstCode); return true; } catch (BadCredentialsException ignored) { return false; } };
            var results = executor.invokeAll(List.of(exchange, exchange));
            assertEquals(1, results.stream().filter(result -> { try { return result.get(); } catch (Exception e) { throw new AssertionError(e); } }).count());
        } finally { executor.shutdownNow(); }
    }

    @Test void redisOutageAndUnsafeCallbackAreRejected() {
        Attempt attempt = begin(1); MockHttpServletRequest request = browser(attempt);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenThrow(new IllegalStateException("private diagnostic"));
        var error = assertThrows(BadCredentialsException.class, () -> service.validateCallback(request, attempt.state(), 1));
        assertFalse(error.getMessage().contains("private diagnostic"));
        assertThrows(BadCredentialsException.class, () -> service.begin(request, new MockHttpServletResponse(), 1, "http://example.invalid/api/oauth/gitee/callback"));
    }
}
