package xyz.kuailemao.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.utils.JwtUtils;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

class JwtAuthorizeFilterTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void previouslyValidJwtDoesNotAuthenticateDisabledOrDeletedUser() throws Exception {
        JwtAuthorizeFilter filter = new JwtAuthorizeFilter();
        JwtUtils jwtUtils = mock(JwtUtils.class);
        ReflectionTestUtils.setField(filter, "jwtUtils", jwtUtils);
        DecodedJWT decoded = mock(DecodedJWT.class);
        when(jwtUtils.resolveJwt("Bearer signed")).thenReturn(decoded);
        when(jwtUtils.toUser(decoded)).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer signed");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(any(), any());
    }
}
