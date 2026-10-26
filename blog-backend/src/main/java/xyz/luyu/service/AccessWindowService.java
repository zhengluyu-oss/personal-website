package xyz.luyu.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

/** Counter and start marker have exactly one bounded lifetime. Ban deadlines are separate. */
@Service
public class AccessWindowService {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<List> WINDOW = new DefaultRedisScript<>("""
            local window = tonumber(ARGV[1])
            local clock = redis.call('TIME')
            local now = tonumber(clock[1])*1000 + math.floor(tonumber(clock[2])/1000)
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if count == 1 or ttl < 1 then
              ttl = window
              redis.call('PEXPIRE', KEYS[1], ttl)
            end
            local start = tonumber(redis.call('GET', KEYS[2]))
            if count == 1 or not start or start > now or start < now-window then
              start = now - math.max(0, window-ttl)
            end
            redis.call('SET', KEYS[2], tostring(start), 'PX', ttl)
            return {count, start}
            """, List.class);

    public AccessWindowService(StringRedisTemplate redis) { this.redis = redis; }
    public record Window(long count, long startedAt) {}
    public Window increment(String key, int seconds) {
        if (seconds <= 0) throw new IllegalArgumentException("限流窗口必须为正数");
        List<?> values = redis.execute(WINDOW, List.of(key, "expire_time_" + key), String.valueOf(seconds * 1000L));
        if (values == null || values.size() != 2) throw new IllegalStateException("限流窗口结果不可用");
        return new Window(((Number) values.get(0)).longValue(), ((Number) values.get(1)).longValue());
    }
}
