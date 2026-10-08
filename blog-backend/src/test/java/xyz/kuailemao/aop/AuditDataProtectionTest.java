package xyz.kuailemao.aop;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xyz.kuailemao.annotation.LogAnnotation;
import xyz.kuailemao.domain.dto.UserResetPasswordDTO;
import xyz.kuailemao.domain.entity.Log;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.interceptor.LogQueueListener;
import xyz.kuailemao.mapper.LogMapper;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.IpService;
import xyz.kuailemao.utils.AuditDataProtection;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditDataProtectionTest {
    private static final String SECRET = "synthetic-secret-never-log";
    static class Endpoint {
        @LogAnnotation(module = "test", operation = "reset")
        public void resetPassword() {}
    }

    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    @Test void nestedPayloadsAndUnknownObjectsAreSafeAndBounded() {
        UserResetPasswordDTO dto = new UserResetPasswordDTO();
        dto.setPassword(SECRET); dto.setCode("123456"); dto.setEmail("test@example.invalid");
        Object dangerous = new Object() {
            @Override public String toString() { throw new AssertionError("must not stringify unknown object"); }
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("password", SECRET); payload.put("code", "123456");
        payload.put("authorization", SECRET); payload.put(SECRET, SECRET);
        payload.put("nested", List.of(Map.of("token", SECRET), dto));
        payload.put("id", 42L); payload.put("self", payload);
        String projected = AuditDataProtection.request(new Object[]{payload, dto, dangerous}, "ArticleController", "publish");
        assertFalse(projected.contains(SECRET)); assertFalse(projected.contains("123456"));
        assertTrue(projected.contains("42")); assertTrue(projected.length() < 2000);
        assertEquals("[REDACTED]", AuditDataProtection.request(new Object[]{payload}, "UserController", "reset"));
        assertFalse(AuditDataProtection.response(ResponseResult.success(payload, SECRET)).contains(SECRET));
    }

    @Test void successFailureAndBrokerFailureNeverLogCredentials() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(LogAspect.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start(); logger.addAppender(appender);
        try {
            exercise(false, false); exercise(true, false); exercise(false, true); exercise(true, true);
            for (ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage().contains(SECRET));
                assertNull(event.getThrowableProxy(), "raw exception stacks may expose input");
            }
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    private void exercise(boolean fails, boolean brokerFails) throws Throwable {
        LogAspect aspect = new LogAspect();
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        ReflectionTestUtils.setField(aspect, "userMapper", mock(UserMapper.class));
        ReflectionTestUtils.setField(aspect, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(aspect, "exchange", "test");
        ReflectionTestUtils.setField(aspect, "routingKey", "test");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("POST", "/user/reset-password")));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(Endpoint.class.getMethod("resetPassword"));
        when(signature.getName()).thenReturn("resetPassword");
        when(point.getTarget()).thenReturn(new Endpoint());
        when(point.getArgs()).thenReturn(new Object[]{Map.of("password", SECRET)});
        RuntimeException original = new IllegalArgumentException(SECRET);
        Object result = ResponseResult.success(Map.of("token", SECRET));
        if (fails) when(point.proceed()).thenThrow(original); else when(point.proceed()).thenReturn(result);
        if (brokerFails) doThrow(new IllegalStateException(SECRET)).when(rabbit).convertAndSend(eq("test"), eq("test"), any(Object.class));
        if (fails) assertSame(original, assertThrows(IllegalArgumentException.class, () -> aspect.log(point)));
        else assertSame(result, aspect.log(point));
        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(rabbit).convertAndSend(eq("test"), eq("test"), captured.capture());
        Log event = (Log) captured.getValue();
        assertFalse(event.toString().contains(SECRET));
        assertEquals(fails ? 2 : 0, event.getState());
        verify(point, times(1)).proceed();
    }

    @Test void legacyQueuedSecretsAreRemovedBeforePersistence() {
        LogQueueListener listener = new LogQueueListener();
        LogMapper mapper = mock(LogMapper.class);
        ReflectionTestUtils.setField(listener, "logMapper", mapper);
        ReflectionTestUtils.setField(listener, "ipService", mock(IpService.class));
        Log event = Log.builder().reqParameter(SECRET).returnParameter(SECRET).exception(SECRET)
                .reqAddress("/link/email/apply?verifyCode=" + SECRET).build();
        listener.handlerSystemLog(event);
        ArgumentCaptor<Log> captured = ArgumentCaptor.forClass(Log.class);
        verify(mapper).insert(captured.capture());
        assertFalse(captured.getValue().toString().contains(SECRET));
    }
}
