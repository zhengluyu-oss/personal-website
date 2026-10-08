package xyz.kuailemao.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.kuailemao.handler.GlobalExceptionControllerHandler;
import xyz.kuailemao.handler.SensitiveActionExceptionHandler;
import xyz.kuailemao.service.EmailChangeService;
import xyz.kuailemao.service.UserService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class EmailChangeControllerSafetyTest {
    @Test void wrongProofIsControlledWithoutUnauthenticatedLogoutCode() throws Exception {
        UserController controller=new UserController(); UserService users=mock(UserService.class);
        EmailChangeService changes=mock(EmailChangeService.class);
        ReflectionTestUtils.setField(controller,"userService",users); ReflectionTestUtils.setField(controller,"emailChangeService",changes);
        when(users.updateEmailAndVerify(any())).thenThrow(new BadCredentialsException("private-secret"));
        when(users.thirdUpdateEmail(any())).thenThrow(new BadCredentialsException("private-secret"));
        when(changes.start(any())).thenThrow(new BadCredentialsException("private-secret"));
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new SensitiveActionExceptionHandler(),new GlobalExceptionControllerHandler()).build();
        for(String route:new String[]{"/user/auth/update/email","/user/auth/third/update/email","/user/auth/email-change/start"}) {
            String result=mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.invalid\",\"code\":\"123456\",\"challengeId\":\""+"a".repeat(64)+"\"}"))
                    .andReturn().getResponse().getContentAsString();
            assertTrue(result.contains("1005"),result); assertFalse(result.contains("private-secret")); assertFalse(result.contains("1002"));
        }
    }
    @Test void legacyRequestsAndInvalidPasswordsNeverReachServiceOrLogs() throws Exception {
        UserController controller=new UserController(); UserService users=mock(UserService.class); EmailChangeService changes=mock(EmailChangeService.class);
        ReflectionTestUtils.setField(controller,"userService",users); ReflectionTestUtils.setField(controller,"emailChangeService",changes);
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionControllerHandler()).build();
        Logger logger=(Logger)LoggerFactory.getLogger(GlobalExceptionControllerHandler.class); var appender=new ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();appender.start();logger.addAppender(appender);
        try {
            String secret="SYNTHETIC_PASSWORD_MUST_NOT_LOG".repeat(6);
            for(String route:new String[]{"/user/auth/update/email","/user/auth/third/update/email","/user/auth/email-change/start"}) {
                String result=mvc.perform(post(route).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.invalid\",\"code\":\"123456\",\"password\":\""+secret+"\"}"))
                        .andReturn().getResponse().getContentAsString();
                assertTrue(result.contains("1007"),result); assertFalse(result.contains(secret));
            }
            verifyNoInteractions(users,changes);
            assertFalse(appender.list.toString().contains("SYNTHETIC_PASSWORD")); assertFalse(appender.list.toString().contains("123456"));
        } finally {logger.detachAppender(appender);appender.stop();}
    }
}
