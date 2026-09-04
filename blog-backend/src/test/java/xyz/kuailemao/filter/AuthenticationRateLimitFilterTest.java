package xyz.kuailemao.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.utils.RedisCache;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationRateLimitFilterTest {
    @Mock private RedisCache redisCache;
    @Mock private FilterChain chain;
    private AuthenticationRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new AuthenticationRateLimitFilter();
        ReflectionTestUtils.setField(filter, "redisCache", redisCache);
        when(redisCache.increment(org.mockito.ArgumentMatchers.anyString(), anyLong())).thenReturn(2L);
    }

    @Test
    void changingUntrustedForwardingHeadersDoesNotChangeClientLimitKey() throws Exception {
        filter.doFilter(request("198.51.100.1"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("192.0.2.200"), new MockHttpServletResponse(), chain);

        verify(redisCache, times(2)).increment(eq("auth:login:client:203.0.113.20"), eq(1L));
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest request(String forged) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/login");
        request.setRemoteAddr("203.0.113.20");
        request.addHeader("X-Forwarded-For", forged);
        request.addHeader("X-Real-IP", forged);
        request.addParameter("username", "Admin");
        return request;
    }
}
