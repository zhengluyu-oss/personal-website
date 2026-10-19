package xyz.kuailemao.handler;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import xyz.kuailemao.config.SecurityConfiguration;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.filter.*;
import xyz.kuailemao.service.*;
import xyz.kuailemao.utils.*;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LoginFailureReasonSecurityTest {
    @Configuration(proxyBeanMethods=false) @EnableWebSecurity @EnableWebMvc
    @Import({SecurityConfiguration.class, SecurityHandler.class, JwtAuthorizeFilter.class, AuthenticationRateLimitFilter.class})
    static class Config {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }

    }
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    AdminLoginChallengeService challenges;
    JwtUtils jwt;
    LoginLogService logs;
    @BeforeEach void setup() {
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        challenges=mock(AdminLoginChallengeService.class);jwt=mock(JwtUtils.class);logs=mock(LoginLogService.class);
        context.addBeanFactoryPostProcessor(f -> {
            f.registerSingleton("adminLoginChallengeService",challenges);f.registerSingleton("jwtUtils",jwt);
            f.registerSingleton("redisCache",mock(RedisCache.class));f.registerSingleton("loginLogService",logs);
            UserService users=mock(UserService.class);
            String hash=new BCryptPasswordEncoder(4).encode("synthetic-login-password");
            when(users.loadUserByUsername(anyString())).thenAnswer(call -> {
                String name=call.getArgument(0);
                if (!"fixture-admin".equals(name)) throw new UsernameNotFoundException("private account detail");
                return new LoginUser(User.builder().id(7L).username(name).password(hash).email("fixture@example.invalid").build())
                    .setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            });
            f.registerSingleton("userService",users);
        });
        context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
    }
    @AfterEach void cleanup() { context.close(); }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(String name,String password) {
        return post("/user/login").param("username",name).param("password",password);
    }
    @Test void returnFromCodeStepWithinCooldownReportsWaitThenRecovers() throws Exception {
        var challenge=new xyz.kuailemao.domain.vo.AdminLoginChallengeVO("fixture-challenge","f***@example.invalid",300,60,true);
        when(challenges.create(any(),any())).thenReturn(challenge)
            .thenThrow(AdminLoginDeliveryException.cooldown(41501L)).thenReturn(challenge);
        mvc.perform(login("fixture-admin","synthetic-login-password")).andExpect(jsonPath("$.data.secondFactorRequired").value(true));
        mvc.perform(login("fixture-admin","synthetic-login-password"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1004))
            .andExpect(jsonPath("$.data.reason").value("CODE_SEND_COOLDOWN"))
            .andExpect(jsonPath("$.data.retryAfterSeconds").value(42))
            .andExpect(jsonPath("$.msg").value("验证码发送过于频繁，请等待 42 秒后重试"))
            .andExpect(jsonPath("$.data.token").doesNotExist());
        mvc.perform(login("fixture-admin","synthetic-login-password")).andExpect(jsonPath("$.data.challengeId").value("fixture-challenge"));
        verify(challenges,times(3)).create(any(),any());verify(jwt,never()).createJwt(any(),any(),any(),any());
    }
    @Test void deliveryUnavailableDoesNotBecomeCredentialError() throws Exception {
        when(challenges.create(any(),any())).thenThrow(AdminLoginDeliveryException.unavailable());
        mvc.perform(login("fixture-admin","synthetic-login-password"))
            .andExpect(jsonPath("$.code").value(1008)).andExpect(jsonPath("$.data.reason").value("CODE_SEND_UNAVAILABLE"))
            .andExpect(jsonPath("$.msg").value("验证码发送服务暂不可用，请稍后重试"))
            .andExpect(jsonPath("$.data.token").doesNotExist());
        verify(jwt,never()).createJwt(any(),any(),any(),any());
    }
    @Test void infrastructureFailureAfterPasswordAcceptanceIsSanitized() throws Exception {
        when(challenges.create(any(),any())).thenThrow(new IllegalStateException("private Redis endpoint and secret"));
        mvc.perform(login("fixture-admin","synthetic-login-password"))
            .andExpect(jsonPath("$.code").value(1008)).andExpect(jsonPath("$.msg").value("登录服务暂不可用，请稍后重试"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private"))))
            .andExpect(jsonPath("$.data.token").doesNotExist());
        verify(jwt,never()).createJwt(any(),any(),any(),any());
    }
    @Test void wrongPasswordAndUnknownAccountHaveSameResponseWithoutReadingCooldown() throws Exception {
        for(String name:List.of("fixture-admin","missing-account")) {
            mvc.perform(login(name,"wrong-synthetic-password"))
                .andExpect(jsonPath("$.code").value(1001)).andExpect(jsonPath("$.msg").value("用户名或密码错误"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
        }
        verifyNoInteractions(challenges);verify(jwt,never()).createJwt(any(),any(),any(),any());
    }
    @Test void auditOutageCannotOverwriteCooldownResponse() throws Exception {
        when(challenges.create(any(),any())).thenThrow(AdminLoginDeliveryException.cooldown(1000L));
        doThrow(new IllegalStateException("private logging failure")).when(logs).loginLog(any(),any(),anyInt(),anyString());
        mvc.perform(login("fixture-admin","synthetic-login-password")).andExpect(jsonPath("$.code").value(1004));
    }
    @Test void ordinaryUserStillReceivesLoginResultWithoutChallenge() throws Exception {
        var reader=new LoginUser(User.builder().id(8L).username("fixture-reader").password(new BCryptPasswordEncoder(4).encode("synthetic-login-password")).registerType(0).build())
            .setAuthorities(List.of(new SimpleGrantedAuthority("ROLE_USER")));
        doReturn(reader).when(context.getBean(UserService.class)).loadUserByUsername("fixture-reader");
        when(jwt.createJwt(any(),any(),any(),any())).thenReturn("synthetic-token");
        when(jwt.expireTime(any(org.springframework.security.core.userdetails.UserDetails.class))).thenReturn(new java.util.Date(System.currentTimeMillis()+60000));
        mvc.perform(login("fixture-reader","synthetic-login-password"))
            .andExpect(jsonPath("$.code").value(200)).andExpect(jsonPath("$.data.token").value("synthetic-token"));
        verifyNoInteractions(challenges);
    }
}
