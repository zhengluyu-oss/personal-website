package xyz.kuailemao.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="BLOG_AUDIT_QA_REDIS", matches="true")
class AccessWindowIntegrationTest {
    LettuceConnectionFactory factory;
    StringRedisTemplate redis;
    AccessWindowService windows;
    String key;
    List<String> owned;
    @BeforeEach void setUp() {
        var config = new RedisStandaloneConfiguration("127.0.0.1", Integer.getInteger("blog.audit.redis.port",16380));
        config.setDatabase(15);
        factory=new LettuceConnectionFactory(config); factory.afterPropertiesSet();
        redis=new StringRedisTemplate(factory); windows=new AccessWindowService(redis);
        key="limit:qa:"+UUID.randomUUID(); owned=new ArrayList<>(List.of(key,"expire_time_"+key));
    }
    @AfterEach void cleanup() { try { redis.delete(owned); } finally { factory.destroy(); } }
    @Test void concurrentFirstRequestsShareOneWindowAndStart() throws Exception {
        var executor=Executors.newFixedThreadPool(12);
        var gate=new CountDownLatch(1); List<Future<AccessWindowService.Window>> jobs=new ArrayList<>();
        try {
            for(int i=0;i<48;i++) jobs.add(executor.submit(()->{gate.await(); return windows.increment(key,10);}));
            gate.countDown(); Set<Long> counts=new HashSet<>(), starts=new HashSet<>();
            for(var job:jobs) {var w=job.get(8,TimeUnit.SECONDS); counts.add(w.count()); starts.add(w.startedAt());}
            assertEquals(48,counts.size()); assertTrue(counts.containsAll(List.of(1L,48L))); assertEquals(1,starts.size());
            assertBounded(10000);
        } finally { executor.shutdownNow(); }
    }
    void assertBounded(long maximum) {
        long counter=redis.getExpire(key,TimeUnit.MILLISECONDS), marker=redis.getExpire("expire_time_"+key,TimeUnit.MILLISECONDS);
        assertTrue(counter>0&&counter<=maximum); assertTrue(marker>0&&marker<=maximum);
        assertTrue(Math.abs(counter-marker)<100);
    }
    @Test void windowExpiresButLongIndependentBanSurvives() throws Exception {
        String ban="qa:ban:"+UUID.randomUUID(); owned.add(ban);
        redis.opsForValue().set(ban,"blocked",Duration.ofSeconds(30));
        windows.increment(key,1); Thread.sleep(1150);
        assertFalse(Boolean.TRUE.equals(redis.hasKey(key))); assertFalse(Boolean.TRUE.equals(redis.hasKey("expire_time_"+key)));
        assertEquals("blocked",redis.opsForValue().get(ban)); assertTrue(redis.getExpire(ban)>20);
        assertEquals(1,windows.increment(key,1).count());
    }
    @Test void legacyPersistentMarkerIsRepairedWithoutExtendingLiveWindow() {
        redis.opsForValue().set(key,"5",Duration.ofMillis(1800));
        redis.opsForValue().set("expire_time_"+key,String.valueOf(System.currentTimeMillis()-200));
        assertEquals(-1L,redis.getExpire("expire_time_"+key));
        assertEquals(6,windows.increment(key,3).count()); assertBounded(1800);
    }
    @Test void staleMarkerAndPersistentCounterDoNotCreatePermanentWindow() {
        redis.opsForValue().set(key,"7"); redis.opsForValue().set("expire_time_"+key,"1");
        var w=windows.increment(key,2); assertEquals(8,w.count());
        assertTrue(w.startedAt()>System.currentTimeMillis()-3000); assertBounded(2000);
    }
    @Test void redisFailurePropagatesInsteadOfInventingAnAllowedCount() {
        var broken=mock(StringRedisTemplate.class);
        when(broken.execute(any(),anyList(),any(Object[].class))).thenThrow(new RedisConnectionFailureException("isolated"));
        assertThrows(RedisConnectionFailureException.class,()->new AccessWindowService(broken).increment(key,1));
    }
}
