package xyz.luyu.controller;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.luyu.handler.SecurityHandler;
import xyz.luyu.service.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminLoginDeliveryResponseTest {
    MockMvc mvc; AdminLoginChallengeService service; SecurityHandler handler;
    @BeforeEach void setup() {
        service=mock(AdminLoginChallengeService.class);handler=mock(SecurityHandler.class);
        var controller=new UserController();ReflectionTestUtils.setField(controller,"adminLoginChallengeService",service);
        ReflectionTestUtils.setField(controller,"securityHandler",handler);
        mvc=MockMvcBuilders.standaloneSetup(controller).build();
    }
    @Test void resendCooldownUsesSameCodeAndDetailsAsLogin() throws Exception {
        when(service.resend(eq("fixture"),any())).thenThrow(AdminLoginDeliveryException.cooldown(1001L));
        mvc.perform(post("/user/admin-login/resend").contentType("application/json").content("{\"challengeId\":\"fixture\"}"))
            .andExpect(jsonPath("$.code").value(1004)).andExpect(jsonPath("$.data.reason").value("CODE_SEND_COOLDOWN"))
            .andExpect(jsonPath("$.data.retryAfterSeconds").value(2));
        verifyNoInteractions(handler);
    }
    @Test void resendUnavailableAndUnexpectedFailureAreSafe() throws Exception {
        when(service.resend(eq("fixture"),any())).thenThrow(AdminLoginDeliveryException.unavailable())
            .thenThrow(new IllegalStateException("private connection secret"));
        mvc.perform(post("/user/admin-login/resend").contentType("application/json").content("{\"challengeId\":\"fixture\"}"))
            .andExpect(jsonPath("$.code").value(1008)).andExpect(jsonPath("$.msg").value("验证码发送服务暂不可用，请稍后重试"));
        mvc.perform(post("/user/admin-login/resend").contentType("application/json").content("{\"challengeId\":\"fixture\"}"))
            .andExpect(jsonPath("$.code").value(1008)).andExpect(jsonPath("$.msg").value("登录服务暂不可用，请稍后重试"));
    }
    @Test void invalidChallengeAndCodeKeepUniformVerificationFailure() throws Exception {
        when(service.resend(eq("fixture"),any())).thenThrow(new BadCredentialsException("private state"));
        when(service.verify(eq("fixture"),eq("123456"),any())).thenThrow(new BadCredentialsException("private state"));
        mvc.perform(post("/user/admin-login/resend").contentType("application/json").content("{\"challengeId\":\"fixture\"}"))
            .andExpect(jsonPath("$.code").value(1005)).andExpect(jsonPath("$.msg").value("登录验证失败，请重新登录"));
        mvc.perform(post("/user/admin-login/verify").contentType("application/json").content("{\"challengeId\":\"fixture\",\"code\":\"123456\"}"))
            .andExpect(jsonPath("$.code").value(1005)).andExpect(jsonPath("$.msg").value("登录验证失败，请重新登录"));
        verifyNoInteractions(handler);
    }
}
