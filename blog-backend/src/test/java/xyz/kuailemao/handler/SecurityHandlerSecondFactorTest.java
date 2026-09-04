package xyz.kuailemao.handler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.domain.vo.AdminLoginChallengeVO;
import xyz.kuailemao.service.AdminLoginChallengeService;
import xyz.kuailemao.utils.JwtUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityHandlerSecondFactorTest {
    @Mock private JwtUtils jwtUtils;
    @Mock private AdminLoginChallengeService challengeService;

    private SecurityHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SecurityHandler();
        ReflectionTestUtils.setField(handler, "jwtUtils", jwtUtils);
        ReflectionTestUtils.setField(handler, "adminLoginChallengeService", challengeService);
    }

    @Test
    void validAdminPasswordCreatesChallengeWithoutIssuingJwtEvenWithForgedClientHeader() throws Exception {
        User account = User.builder().id(1L).username("admin").password("unchanged-hash").email("a@example.com").build();
        LoginUser principal = new LoginUser(account);
        principal.setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(challengeService.create(eq(principal), any())).thenReturn(
                new AdminLoginChallengeVO("opaque", "a***@example.com", 300, 60, true));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/login");
        request.setRemoteAddr("203.0.113.9");
        request.addHeader("X-Client-Type", "Backend");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response,
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        verify(challengeService).create(principal, "203.0.113.9");
        verify(jwtUtils, never()).createJwt(any(), any(), any(), any());
        assertFalse(response.getContentAsString().contains("\"token\""));
        org.junit.jupiter.api.Assertions.assertEquals("unchanged-hash", account.getPassword());
    }
}
