package xyz.kuailemao.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.stereotype.Component;
import jakarta.annotation.Resource;
import xyz.kuailemao.service.EmailDeliveryService;

/**
 * @author kuailemao
 * <p>
 * 创建时间：2024/7/31 20:54
 * 邮件队列监听器
 */
@Slf4j
@Component
public class RabbitListenerErrorHandler implements MessageRecoverer, org.springframework.amqp.rabbit.listener.api.RabbitListenerErrorHandler {

    @Resource private EmailDeliveryService emailDeliveryService;

    @Override
    public Object handleError(Message message, com.rabbitmq.client.Channel channel,
                              org.springframework.messaging.Message<?> message1, ListenerExecutionFailedException e) throws Exception {
        // 处理重试失败的情况，例如记录日志、发送告警等
        log.error("Queue listener failed; payload and original exception omitted");
        throw e;
    }
    @Override
    public void recover(Message message, Throwable cause) {
        Object taskId = message.getMessageProperties().getHeaders().get(EmailDeliveryService.TASK_HEADER);
        if (taskId instanceof String id) emailDeliveryService.fail(id);
        // 恢复消息，例如将消息发送到死信队列
        log.error("Queue retries exhausted; payload and original exception omitted");
    }


}
