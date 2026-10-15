package xyz.kuailemao.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.kuailemao.config.OssProperties;
import xyz.kuailemao.domain.dto.ArticleDTO;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.storage.FileStorageService;
import xyz.kuailemao.utils.FileUploadUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "BLOG_AUDIT_QA_REDIS", matches = "true")
class ArticleCoverLifecycleIntegrationTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private ArticleCoverLifecycle lifecycle;
    private FileStorageService storage;
    private ArticleMapper articles;
    private FileUploadUtils files;
    private final Set<String> references = ConcurrentHashMap.newKeySet();
    private final Set<String> objects = ConcurrentHashMap.newKeySet();
    private final List<String> ownedRegistryKeys = new ArrayList<>();
    private static final String ORIGIN = "https://media.example.invalid/";
    private String registry(String key) { return "upload:article-cover:v1:" + key; }
    private void authenticate(long id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new LoginUser(User.builder().id(id).build()), null, List.of()));
    }

    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Article.class);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration("127.0.0.1", Integer.getInteger("blog.audit.redis.port", 16380));
        config.setDatabase(15);
        factory = new LettuceConnectionFactory(config); factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        lifecycle = new ArticleCoverLifecycle(); files = new FileUploadUtils();
        storage = mock(FileStorageService.class); articles = mock(ArticleMapper.class);
        OssProperties properties = new OssProperties(); properties.setDomain(ORIGIN);
        ReflectionTestUtils.setField(files, "ossProperties", properties);
        ReflectionTestUtils.setField(files, "fileStorageService", storage);
        ReflectionTestUtils.setField(lifecycle, "redis", redis);
        ReflectionTestUtils.setField(lifecycle, "files", files);
        ReflectionTestUtils.setField(lifecycle, "articles", articles);
        when(storage.exists(anyString())).thenAnswer(call -> objects.contains(call.getArgument(0)));
        doAnswer(call -> { objects.remove(call.getArgument(0)); return null; }).when(storage).delete(anyString());
        when(articles.selectCount(any())).thenAnswer(call -> {
            LambdaQueryWrapper<Article> query = call.getArgument(0);
            query.getSqlSegment();
            return query.getParamNameValuePairs().values().stream()
                    .map(value -> value.toString().replace("%", "")).anyMatch(references::contains) ? 1L : 0L;
        });
        authenticate(42L);
    }
    @AfterEach void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        try { redis.delete(ownedRegistryKeys); } finally { factory.destroy(); }
    }
    private String upload() {
        String key = ArticleCoverLifecycle.PURPOSE + UUID.randomUUID() + ".png";
        ownedRegistryKeys.add(registry(key)); objects.add(key); lifecycle.register(ORIGIN + key);
        return key;
    }
    private ArticleDTO request(String key) {
        ArticleDTO dto = new ArticleDTO(); dto.setArticleCover(ORIGIN + key); dto.setArticleContent("synthetic body"); return dto;
    }
    private String state(String key) {
        return JSON.parseObject(redis.opsForValue().get(registry(key)), ArticleCoverLifecycle.TemporaryCover.class).state();
    }

    @Test void registrationIsOwnedBoundedAndCancellationIsIdempotent() {
        String key = upload();
        var record = JSON.parseObject(redis.opsForValue().get(registry(key)), ArticleCoverLifecycle.TemporaryCover.class);
        assertEquals(42L, record.owner()); assertEquals(key, record.key()); assertEquals(ArticleCoverLifecycle.PURPOSE, record.purpose());
        long ttl = redis.getExpire(registry(key), TimeUnit.SECONDS);
        assertTrue(ttl > 23 * 3600 && ttl <= 24 * 3600);
        lifecycle.cancel(ORIGIN + key); lifecycle.cancel(ORIGIN + key);
        assertFalse(objects.contains(key)); assertEquals("deleted", state(key));
        verify(storage, times(1)).delete(key);
    }
    @Test void anotherOwnerAndAnotherPurposeCannotDelete() {
        String key = upload(); authenticate(43L);
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.cancel(ORIGIN + "websiteInfo/avatar/synthetic.png"));
        verify(storage, never()).delete(anyString()); assertTrue(objects.contains(key));
    }
    @Test void persistedCoverOrBodyReferenceBlocksCancellation() {
        String key = upload(); references.add(key);
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        assertEquals("pending", state(key)); assertTrue(objects.contains(key));
        verify(storage, never()).delete(anyString());
    }
    @Test void deletionFailureRestoresRetryablePendingAuthorization() {
        String key = upload(); doThrow(new IllegalStateException("synthetic failure")).when(storage).delete(key);
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        assertEquals("pending", state(key)); assertTrue(objects.contains(key));
        doAnswer(call -> { objects.remove(key); return null; }).when(storage).delete(key);
        lifecycle.cancel(ORIGIN + key); assertEquals("deleted", state(key));
    }
    @Test void expiredMissingAndUnknownStateFailClosed() throws Exception {
        String key = upload(); redis.expire(registry(key), Duration.ofMillis(1)); Thread.sleep(30);
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        assertThrows(IllegalStateException.class, () -> lifecycle.publication(request(key)));
        redis.opsForValue().set(registry(key), "corrupt", Duration.ofMinutes(1));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        verify(storage, never()).delete(anyString());
    }
    @Test void publicationClaimsBodyReferencesIncludingEncodedUrls() {
        String cover = upload(); String body = upload();
        ArticleDTO dto = request(cover);
        dto.setArticleContent("![body](" + ORIGIN + body.replace("/", "%2f") + "?x-oss-process=image/resize,w_100)");
        try (var claim = lifecycle.publication(dto)) {
            assertEquals("publishing", state(cover)); assertEquals("publishing", state(body));
            assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + body));
            references.add(cover); references.add(body); claim.complete();
        }
        assertEquals("referenced", state(cover)); assertEquals("referenced", state(body));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + cover));
        verify(storage, never()).delete(anyString());
    }
    @Test void transactionRollbackAndCommitDriveTheLifecycle() {
        String key = upload(); TransactionSynchronizationManager.initSynchronization();
        try (var claim = lifecycle.publication(request(key))) { claim.complete(); }
        assertEquals("publishing", state(key));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        assertEquals("pending", state(key));
        TransactionSynchronizationManager.initSynchronization();
        try (var claim = lifecycle.publication(request(key))) { references.add(key); claim.complete(); }
        assertEquals("publishing", state(key));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        TransactionSynchronizationManager.clearSynchronization();
        assertEquals("referenced", state(key));
    }
    @Test void interruptedClaimCannotBeReclaimedByExpiryAsUnused() throws Exception {
        String key = upload(); var abandoned = lifecycle.publication(request(key));
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        redis.expire(registry(key), Duration.ofMillis(1)); Thread.sleep(30);
        abandoned.close();
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        assertTrue(objects.contains(key)); verify(storage, never()).delete(anyString());
    }
    @Test void legacyReferencesRemainEditableWithoutCancellationOwnership() {
        String key = ArticleCoverLifecycle.PURPOSE + "synthetic-history.png";
        references.add(key);
        try (var claim = lifecycle.publication(request(key))) { claim.complete(); }
        assertThrows(IllegalStateException.class, () -> lifecycle.cancel(ORIGIN + key));
        when(articles.selectById(88L)).thenReturn(Article.builder().id(88L).articleCover("https://legacy.invalid/cover.png").build());
        ArticleDTO retained = request(key); retained.setId(88L); retained.setArticleCover("https://legacy.invalid/cover.png");
        try (var claim = lifecycle.publication(retained)) { claim.complete(); }
        verify(storage, never()).delete(anyString());
    }
    @Test void realAtomicClaimRaceNeverCommitsADeletedCover() throws Exception {
        for (int iteration = 0; iteration < 12; iteration++) {
            String key = upload(); CyclicBarrier barrier = new CyclicBarrier(2);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            AtomicBoolean published = new AtomicBoolean(); AtomicBoolean deleted = new AtomicBoolean();
            try {
                Future<?> publishing = executor.submit(() -> {
                    authenticate(42L);
                    try { barrier.await(); try (var claim = lifecycle.publication(request(key))) {
                        references.add(key); claim.complete(); published.set(true);
                    } } catch (IllegalStateException denied) { /* Other operation won its atomic claim. */ }
                    catch (Exception failed) { throw new RuntimeException(failed); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                Future<?> cancelling = executor.submit(() -> {
                    authenticate(42L);
                    try { barrier.await(); lifecycle.cancel(ORIGIN + key); deleted.set(true); }
                    catch (IllegalStateException denied) { /* Publication owns the lifecycle. */ }
                    catch (Exception failed) { throw new RuntimeException(failed); }
                    finally { SecurityContextHolder.clearContext(); }
                });
                publishing.get(10, TimeUnit.SECONDS); cancelling.get(10, TimeUnit.SECONDS);
                assertNotEquals(published.get(), deleted.get(), "exactly one operation must win");
                assertEquals(published.get(), objects.contains(key));
                assertEquals(published.get(), references.contains(key));
            } finally { executor.shutdownNow(); }
        }
    }
}
