package xyz.kuailemao.service;

import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;

/** A capability exposes delivery state only, never recipients or verification codes. */
@Service
public class EmailDeliveryService {
    public static final String TASK_HEADER = "emailTaskId";
    public static final String DELIVERY_HEADER = "emailDeliveryId";
    private static final String PREFIX = "email:delivery:v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    @Resource private StringRedisTemplate stringRedisTemplate;
    @Resource private RabbitTemplate rabbitTemplate;
    @Value("${spring.rabbitmq.exchange.email}") private String exchange;
    @Value("${spring.rabbitmq.routingKey.email}") private String routingKey;

    public record Status(String status, String message) {}
    public record Cleanup(String key, String expectedValue) {}

    // All terminal transitions and conditional code invalidation are atomic.
    private static final DefaultRedisScript<String> TRANSITION = new DefaultRedisScript<>("""
        local state=redis.call('HGET',KEYS[1],'status')
        if not state then return 'EXPIRED' end
        if state=='SENT' or state=='FAILED' or state=='EXPIRED' then return state end
        local action=ARGV[1]
        local deadline=tonumber(redis.call('HGET',KEYS[1],'deadline'))
        if tonumber(ARGV[2])>=deadline then state='EXPIRED'
        elseif action=='fail' then state='FAILED'
        elseif action=='claim' then
          local field='delivery:'..ARGV[3]
          if redis.call('HEXISTS',KEYS[1],field)==1 then return 'SKIP' end
          local attempts=redis.call('HINCRBY',KEYS[1],'attempts:'..ARGV[3],1)
          if attempts>3 then state='FAILED'
          else redis.call('HSET',KEYS[1],field,'processing','status','PROCESSING'); return 'CLAIMED' end
        elseif action=='retry' then
          redis.call('HDEL',KEYS[1],'delivery:'..ARGV[3]); state='PENDING'
        elseif action=='sent' then
          local field='delivery:'..ARGV[3]
          if redis.call('HGET',KEYS[1],field)=='processing' then
            redis.call('HSET',KEYS[1],field,'done')
            local completed=redis.call('HINCRBY',KEYS[1],'completed',1)
            if completed>=tonumber(redis.call('HGET',KEYS[1],'expected')) then state='SENT' end
          end
        end
        redis.call('HSET',KEYS[1],'status',state)
        if state=='FAILED' or state=='EXPIRED' then
          local count=tonumber(redis.call('HGET',KEYS[1],'cleanupCount'))
          for i=0,count-1 do
            local key=redis.call('HGET',KEYS[1],'cleanupKey:'..i)
            local expected=redis.call('HGET',KEYS[1],'cleanupValue:'..i)
            if expected=='*' or redis.call('GET',key)==expected then redis.call('DEL',key) end
          end
        end
        return state
        """, String.class);

    public String create(int expected, List<Cleanup> cleanup) {
        if (expected < 1 || expected > 2) throw new IllegalArgumentException("Invalid delivery count");
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String id = HexFormat.of().formatHex(bytes);
        Map<String, String> state = new HashMap<>();
        state.put("status", "PENDING"); state.put("expected", Integer.toString(expected));
        state.put("completed", "0"); state.put("deadline", Long.toString(System.currentTimeMillis() + 300_000));
        state.put("cleanupCount", Integer.toString(cleanup.size()));
        for (int i = 0; i < cleanup.size(); i++) {
            state.put("cleanupKey:" + i, cleanup.get(i).key());
            state.put("cleanupValue:" + i, Objects.requireNonNull(cleanup.get(i).expectedValue()));
        }
        // Single script prevents leaving an immortal task if the process stops between write and expiry.
        var script = new DefaultRedisScript<Long>("for i=1,#ARGV,2 do redis.call('HSET',KEYS[1],ARGV[i],ARGV[i+1]) end; redis.call('EXPIRE',KEYS[1],600); return 1", Long.class);
        List<String> arguments = new ArrayList<>(); state.forEach((k,v) -> { arguments.add(k); arguments.add(v); });
        stringRedisTemplate.execute(script, List.of(key(id)), arguments.toArray());
        return id;
    }

    public Cleanup valueCleanup(String key) {
        return new Cleanup(key, Objects.requireNonNull(stringRedisTemplate.opsForValue().get(key)));
    }

    public void publish(String id, Map<String, Object> payload) {
        String deliveryId = UUID.randomUUID().toString();
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, message -> {
                message.getMessageProperties().setHeader(TASK_HEADER, id);
                message.getMessageProperties().setHeader(DELIVERY_HEADER, deliveryId);
                message.getMessageProperties().setMessageId(deliveryId);
                return message;
            });
        } catch (RuntimeException failure) {
            fail(id);
            throw new IllegalStateException("验证码发送请求未提交，请稍后重试");
        }
    }

    public boolean claim(String id, String deliveryId) { return "CLAIMED".equals(transition(id, "claim", deliveryId)); }
    public void sent(String id, String deliveryId) { transition(id, "sent", deliveryId); }
    public void retry(String id, String deliveryId) { transition(id, "retry", deliveryId); }
    public void fail(String id) { transition(id, "fail", ""); }
    public Status status(String id) {
        String state = transition(id, "status", "");
        return new Status(state, switch (state) {
            case "SENT" -> "验证码已发送，请查收邮件";
            case "FAILED" -> "验证码发送失败，请稍后重试；持续失败请联系管理员";
            case "EXPIRED" -> "验证码发送任务已过期，请重新获取";
            default -> "验证码正在发送，请稍候";
        });
    }
    private String transition(String id, String action, String deliveryId) {
        return Objects.requireNonNull(stringRedisTemplate.execute(TRANSITION, List.of(key(id)),
                action, Long.toString(System.currentTimeMillis()), deliveryId));
    }
    private String key(String id) {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("发送任务编号无效");
        return PREFIX + id;
    }
}
