package xyz.luyu.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.luyu.domain.dto.ArticleDTO;
import xyz.luyu.domain.entity.Article;
import xyz.luyu.mapper.ArticleMapper;
import xyz.luyu.utils.FileUploadUtils;
import xyz.luyu.utils.SecurityUtils;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coordinates temporary cancellation with all standard article-reference writes. */
@Service
@Slf4j
public class ArticleCoverLifecycle {
    public static final String PURPOSE = "article/articleCover/";
    public static final Duration LIFETIME = Duration.ofHours(24);
    private static final String REGISTRY_PREFIX = "upload:article-cover:v1:";
    private static final Pattern BODY_URL = Pattern.compile("https?://[^\\s\"'<>\\)]+", Pattern.CASE_INSENSITIVE);
    private static final DefaultRedisScript<Long> CAS = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            local ttl = redis.call('PTTL', KEYS[1])
            if ttl <= 0 then return 0 end
            redis.call('PSETEX', KEYS[1], ttl, ARGV[2])
            return 1
            """, Long.class);

    @Resource private StringRedisTemplate redis;
    @Resource private FileUploadUtils files;
    @Resource private ArticleMapper articles;

    public record TemporaryCover(Long owner, String key, String purpose, String state, String operation) {}
    private record Claim(String key, String previous, String claimed) {}

    private String registryKey(String key) { return REGISTRY_PREFIX + key; }
    private Long owner() {
        Long owner = SecurityUtils.getUserId();
        if (owner == null || owner <= 0) throw new IllegalStateException("请先登录");
        return owner;
    }

    public void register(String url) {
        String key = files.requireObjectKey(url, PURPOSE);
        TemporaryCover record = new TemporaryCover(owner(), key, PURPOSE, "pending", UUID.randomUUID().toString());
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(registryKey(key), JSON.toJSONString(record), LIFETIME))) {
            throw new IllegalStateException("上传授权登记失败，请重新上传");
        }
    }

    private TemporaryCover read(String raw, String key) {
        try {
            TemporaryCover record = JSON.parseObject(raw, TemporaryCover.class);
            if (record == null || record.owner() == null || record.owner() <= 0
                    || !key.equals(record.key()) || !PURPOSE.equals(record.purpose())
                    || record.operation() == null || record.state() == null) throw new IllegalArgumentException();
            return record;
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("上传授权状态不可用，请重新上传");
        }
    }

    private boolean swap(String key, String expected, String next) {
        return Long.valueOf(1).equals(redis.execute(CAS, List.of(registryKey(key)), expected, next));
    }

    private Claim claim(String key, String raw, String nextState, Long owner) {
        TemporaryCover previous = read(raw, key);
        if (!owner.equals(previous.owner()) || !"pending".equals(previous.state())) {
            throw new IllegalStateException("只能操作本人未引用的临时封面，请刷新或重新上传");
        }
        String next = JSON.toJSONString(new TemporaryCover(owner, key, PURPOSE, nextState, UUID.randomUUID().toString()));
        if (!swap(key, raw, next)) throw new IllegalStateException("封面正在使用或授权已过期，请重试");
        return new Claim(key, raw, next);
    }

    private boolean referenced(String key) {
        // Matching the canonical key also covers configured host aliases and body references.
        return articles.selectCount(new LambdaQueryWrapper<Article>()
                .and(query -> query.like(Article::getArticleCover, key).or().like(Article::getArticleContent, key))) > 0;
    }

    public void cancel(String url) {
        String key = files.requireObjectKey(url, PURPOSE);
        Long owner = owner();
        String raw = redis.opsForValue().get(registryKey(key));
        if (raw == null) throw new IllegalStateException("临时上传授权不存在或已过期，已有文章封面不能在此删除");
        TemporaryCover record = read(raw, key);
        if (owner.equals(record.owner()) && "deleted".equals(record.state()) && !referenced(key)) return;
        Claim claim = claim(key, raw, "deleting", owner);
        boolean deleted = false;
        try {
            if (referenced(key)) throw new IllegalStateException("该封面已被文章使用，不能取消删除");
            if (!files.deleteTrustedFile(url, PURPOSE)) throw new IllegalStateException("对象存储清理失败，请稍后重试");
            deleted = true;
            String done = JSON.toJSONString(new TemporaryCover(owner, key, PURPOSE, "deleted", record.operation()));
            if (!swap(key, claim.claimed(), done)) throw new IllegalStateException("对象已清理，授权状态待核实，请刷新");
        } finally {
            // Uncertain completion never resets to publishable after an object was deleted.
            if (!deleted) restorePending(claim);
        }
    }

    private void restorePending(Claim claim) {
        try {
            if (!swap(claim.key(), claim.claimed(), claim.previous())) {
                log.warn("临时封面认领恢复待核实: {}", claim.key());
            }
        } catch (RuntimeException unavailable) {
            log.warn("临时封面认领恢复失败，保持拒绝状态: {}", claim.key());
        }
    }

    public Publication publication(ArticleDTO request) {
        Long owner = owner();
        Set<String> keys = new TreeSet<>();
        Article before = request.getId() == null ? null : articles.selectById(request.getId());
        boolean retained = before != null && Objects.equals(before.getArticleCover(), request.getArticleCover());
        if (!retained) keys.add(files.requireObjectKey(request.getArticleCover(), PURPOSE));
        Matcher urls = BODY_URL.matcher(Objects.toString(request.getArticleContent(), ""));
        while (urls.find()) {
            String url = urls.group();
            try { keys.add(files.referenceObjectKey(url, PURPOSE)); }
            catch (IllegalArgumentException foreignLink) { /* Not a URL capable of referencing a trusted cover. */ }
        }
        List<Claim> claims = new ArrayList<>();
        try {
            for (String key : keys) {
                String raw = redis.opsForValue().get(registryKey(key));
                if (raw == null) {
                    if (referenced(key)) continue;
                    throw new IllegalStateException("封面没有有效上传授权，请重新上传");
                }
                TemporaryCover record = read(raw, key);
                if ("referenced".equals(record.state()) && referenced(key)) continue;
                claims.add(claim(key, raw, "publishing", owner));
            }
        } catch (RuntimeException failed) {
            claims.forEach(this::restorePending);
            throw failed;
        }
        return new Publication(claims);
    }

    public final class Publication implements AutoCloseable {
        private final List<Claim> claims;
        private final boolean transactional;
        private boolean success;
        private boolean finished;

        private Publication(List<Claim> claims) {
            this.claims = claims;
            transactional = TransactionSynchronizationManager.isSynchronizationActive();
            if (transactional) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { finish(status == STATUS_COMMITTED && success); }
            });
        }
        public void complete() { success = true; }
        @Override public void close() { if (!transactional) finish(success); }

        private void finish(boolean committed) {
            if (finished) return;
            finished = true;
            for (Claim claim : claims) {
                if (!committed) { restorePending(claim); continue; }
                TemporaryCover record = read(claim.claimed(), claim.key());
                String next = JSON.toJSONString(new TemporaryCover(record.owner(), claim.key(), PURPOSE, "referenced", record.operation()));
                try {
                    if (!swap(claim.key(), claim.claimed(), next)) log.warn("已提交封面引用的授权状态待核实: {}", claim.key());
                } catch (RuntimeException unavailable) {
                    log.warn("封面引用已提交，授权状态更新失败，保持拒绝删除: {}", claim.key());
                }
            }
        }
    }
}
