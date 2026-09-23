package xyz.kuailemao.aop;

import io.swagger.v3.oas.annotations.Operation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xyz.kuailemao.annotation.LogAnnotation;
import xyz.kuailemao.controller.ExperienceProjectController;
import xyz.kuailemao.domain.entity.Log;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.mapper.UserMapper;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LogAspectTest {
    static class Endpoints {
        @LogAnnotation(module = "test", operation = "insert")
        public void missingOperation() {}

        @LogAnnotation(module = "test", operation = "insert")
        @Operation(summary = "Existing description")
        public void documented() {}
    }

    @AfterEach
    void cleanup() {
        RequestContextHolder.resetRequestAttributes();
    }

    private Log exercise(String methodName, ResponseResult<Void> response) throws Throwable {
        LogAspect aspect = new LogAspect();
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        ReflectionTestUtils.setField(aspect, "userMapper", mock(UserMapper.class));
        ReflectionTestUtils.setField(aspect, "rabbitTemplate", rabbit);
        ReflectionTestUtils.setField(aspect, "exchange", "logs");
        ReflectionTestUtils.setField(aspect, "routingKey", "operations");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(
                new MockHttpServletRequest("PUT", "/experience/2/projects/back/add")));
        ProceedingJoinPoint point = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(point.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(Endpoints.class.getMethod(methodName));
        when(signature.getName()).thenReturn(methodName);
        when(point.getTarget()).thenReturn(new Endpoints());
        when(point.getArgs()).thenReturn(new Object[0]);
        when(point.proceed()).thenReturn(response);

        assertSame(response, aspect.log(point));
        verify(point, times(1)).proceed();
        ArgumentCaptor<Object> captured = ArgumentCaptor.forClass(Object.class);
        verify(rabbit, times(1)).convertAndSend(eq("logs"), eq("operations"), captured.capture());
        return (Log) captured.getValue();
    }

    @Test
    void missingOperationDoesNotTurnSuccessfulWriteIntoError() throws Throwable {
        Log log = exercise("missingOperation", ResponseResult.success());
        assertEquals("missingOperation", log.getDescription());
        assertEquals(0, log.getState());
    }

    @Test
    void existingDescriptionIsPreserved() throws Throwable {
        assertEquals("Existing description", exercise("documented", ResponseResult.success()).getDescription());
    }

    @Test
    void missingOperationPreservesBusinessFailure() throws Throwable {
        assertEquals(1, exercise("missingOperation", ResponseResult.failure("工作经历不存在")).getState());
    }

    @Test
    void projectWriteEndpointsHaveOperationDescriptions() {
        int count = 0;
        for (Method method : ExperienceProjectController.class.getDeclaredMethods()) {
            if (method.isAnnotationPresent(LogAnnotation.class)) {
                Operation operation = method.getAnnotation(Operation.class);
                assertNotNull(operation, method.getName());
                assertFalse(operation.summary().isBlank(), method.getName());
                count++;
            }
        }
        assertEquals(3, count);
    }
}
