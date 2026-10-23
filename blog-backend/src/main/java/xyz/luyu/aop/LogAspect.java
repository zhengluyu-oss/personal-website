package xyz.luyu.aop;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import xyz.luyu.annotation.LogAnnotation;
import xyz.luyu.constants.FunctionConst;
import xyz.luyu.domain.entity.Log;
import xyz.luyu.domain.entity.User;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.mapper.UserMapper;
import xyz.luyu.utils.AuditDataProtection;
import xyz.luyu.utils.IpUtils;
import xyz.luyu.utils.SecurityUtils;
import xyz.luyu.utils.StringUtils;

import java.lang.reflect.Method;
import java.util.Date;

/**
 * @author luyu
 * <p>
 * 创建时间：2023/12/11 22:56
 * 操作日志aop
 */
@Component
@Slf4j
@Aspect // 切面
public class LogAspect {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.routingKey.log-system}")
    private String routingKey;

    @Value("${spring.rabbitmq.exchange.log}")
    private String exchange;

    /**
     * 切点，注解加在哪，哪就是切点
     */
    @Pointcut("@annotation(xyz.luyu.annotation.LogAnnotation)")
    public void pt() {
    }

    // 环绕通知，在方法执行前后执行
    @Around("pt()")
    public Object log(ProceedingJoinPoint joinPoint) throws Throwable {
        long beginTime = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            recordSafely(joinPoint, System.currentTimeMillis() - beginTime, null, e);
            throw e;
        }
        recordSafely(joinPoint, System.currentTimeMillis() - beginTime, result, null);
        return result;
    }

    private void recordSafely(ProceedingJoinPoint point, long time, Object result, Throwable failure) {
        try {
            recordLog(point, time, result, failure);
        } catch (Exception auditFailure) {
            // Broker/serializer error messages may themselves contain the original payload.
            log.error("Audit publication failed: {}", auditFailure.getClass().getSimpleName());
        }
    }

    private void recordLog(ProceedingJoinPoint joinPoint, long time, Object result, Throwable failure) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        LogAnnotation logAnnotation = method.getAnnotation(LogAnnotation.class);
        // 操作描述
        Operation operation = method.getAnnotation(Operation.class);

        // 获取 request 设置IP地址
        HttpServletRequest request = SecurityUtils.getCurrentHttpRequest();
        // 请求的方法名
        String className = joinPoint.getTarget().getClass().getName();
        String methodName = signature.getName();
        String ipAddr = request == null ? null : IpUtils.getIpAddr(request);
        User user = userMapper.selectById(SecurityUtils.getUserId());


        Log log = Log.builder()
                .module(logAnnotation.module())
                .operation(logAnnotation.operation())
                .ip(ipAddr)
                .description(operation == null ? methodName : operation.summary())
                .reqMapping(request == null ? null : request.getMethod())
                .userName(StringUtils.isNull(user) ? FunctionConst.UNKNOWN_USER : user.getUsername())
                .method(className + "." + methodName + "()")
                .reqParameter(AuditDataProtection.request(joinPoint.getArgs(), className, methodName))
                .returnParameter(AuditDataProtection.response(result))
                .reqAddress(request == null ? null : request.getRequestURI())
                .exception(failure == null ? null : failure.getClass().getSimpleName())
                .time(time)
                .build();
        log.setState(failure != null ? 2 : result instanceof ResponseResult<?> responseResult
                && !Integer.valueOf(200).equals(responseResult.getCode()) ? 1 : 0);

        rabbitTemplate.convertAndSend(exchange,routingKey,log);
        LogAspect.log.info("耗时：{}毫秒", time);
        LogAspect.log.info("操作时间：{}", new Date());
        LogAspect.log.info("================日志结束=========================");

    }

}
