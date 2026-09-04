package xyz.kuailemao.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.dto.AdminLoginResendDTO;
import xyz.kuailemao.domain.dto.AdminLoginVerifyDTO;
import xyz.kuailemao.handler.SecurityHandler;
import xyz.kuailemao.service.AdminLoginChallengeService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerAdminLoginVerificationTest {
    @Mock private AdminLoginChallengeService challengeService;
    @Mock private SecurityHandler securityHandler;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController();
        ReflectionTestUtils.setField(controller, "adminLoginChallengeService", challengeService);
        ReflectionTestUtils.setField(controller, "securityHandler", securityHandler);
    }

    @Test
    void expectedVerificationFailureReturnsControlledBusinessResponse() throws Exception {
        AdminLoginVerifyDTO dto = new AdminLoginVerifyDTO();
        dto.setChallengeId("expired-or-invalid");
        dto.setCode("123456");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/admin-login/verify");
        request.setRemoteAddr("203.0.113.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(challengeService.verify("expired-or-invalid", "123456", "203.0.113.8"))
                .thenThrow(new BadCredentialsException("internal detail"));

        controller.verifyAdminLogin(dto, request, response);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":1005"));
        assertTrue(response.getContentAsString().contains("登录验证失败，请重新登录"));
        assertFalse(response.getContentAsString().contains("internal detail"));
        assertFalse(response.getContentAsString().contains("Redis"));
        verify(securityHandler, never()).issueToken(any(), any(), any());
    }

    @Test
    void expectedResendFailureUsesSameNonEnumeratingResponse() {
        AdminLoginResendDTO dto = new AdminLoginResendDTO();
        dto.setChallengeId("missing");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/admin-login/resend");
        request.setRemoteAddr("203.0.113.8");
        when(challengeService.resend("missing", "203.0.113.8"))
                .thenThrow(new BadCredentialsException("challenge does not exist"));

        var result = controller.resendAdminLogin(dto, request);

        assertEquals(1005, result.getCode());
        assertEquals("登录验证失败，请重新登录", result.getMsg());
        assertFalse(result.getMsg().contains("challenge"));
    }
}
