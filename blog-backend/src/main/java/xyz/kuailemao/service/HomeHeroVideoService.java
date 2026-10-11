package xyz.kuailemao.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PolicyConditions;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import xyz.kuailemao.config.OssProperties;
import xyz.kuailemao.constants.WebsiteInfoConst;
import xyz.kuailemao.domain.entity.WebsiteInfo;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.mapper.WebsiteInfoMapper;

import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/** 首页背景视频的受限直传与发布。视频字节不经过应用服务器。 */
@Slf4j
@Service
public class HomeHeroVideoService {
    public static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final String PREFIX = "blog/home/hero-video/";
    private static final Duration TICKET_LIFETIME = Duration.ofMinutes(5);

    @Resource private OSS ossClient;
    @Resource private OssProperties ossProperties;
    @Resource private StringRedisTemplate redis;
    @Resource private WebsiteInfoMapper websiteInfoMapper;

    public record UploadTicket(String uploadUrl, String objectKey, String accessKeyId,
                               String policy, String signature, String contentType, long maxBytes) {}
    public record VideoState(String url, String objectKey) {}

    private String mediaDomain() {
        String domain = ossProperties.getMediaDomain();
        if (domain == null || !domain.matches("https://[A-Za-z0-9.-]+\\.[A-Za-z]{2,}/?")) {
            throw new IllegalStateException("请先配置 OSS 视频 HTTPS 自定义域名");
        }
        return domain.replaceAll("/+$", "");
    }

    public VideoState current() {
        WebsiteInfo info = websiteInfoMapper.selectById(WebsiteInfoConst.WEBSITE_INFO_ID);
        String key = info == null ? null : info.getHeroVideoKey();
        return key == null || key.isBlank() ? new VideoState(null, null)
                : new VideoState(mediaDomain() + "/" + key, key);
    }

    public UploadTicket issueTicket() {
        mediaDomain(); // 域名未准备好时拒绝发布无法播放的视频
        String key = PREFIX + UUID.randomUUID() + ".mp4";
        PolicyConditions conditions = new PolicyConditions();
        conditions.addConditionItem(PolicyConditions.COND_KEY, key);
        conditions.addConditionItem(PolicyConditions.COND_CONTENT_TYPE, "video/mp4");
        conditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, 1, MAX_BYTES);
        conditions.addConditionItem(PolicyConditions.COND_SUCCESS_ACTION_STATUS, "200");
        String policy = ossClient.generatePostPolicy(new Date(System.currentTimeMillis() + TICKET_LIFETIME.toMillis()), conditions);
        String signature = ossClient.calculatePostSignature(policy);
        redis.opsForValue().set(ticketKey(key), "pending", TICKET_LIFETIME.plusMinutes(2));
        return new UploadTicket("https://" + ossProperties.getBucketName() + "." +
                ossProperties.getEndpoint().replaceFirst("^https?://", ""), key,
                ossProperties.getAccessKey(), policy, signature, "video/mp4", MAX_BYTES);
    }

    @Transactional
    public ResponseResult<VideoState> confirm(String key) {
        if (!validKey(key) || !Objects.equals(redis.opsForValue().get(ticketKey(key)), "pending")) {
            return ResponseResult.failure("上传凭证无效或已过期，请重新选择视频");
        }
        try {
            ObjectMetadata metadata = ossClient.getObjectMetadata(ossProperties.getBucketName(), key);
            if (metadata.getContentLength() < 1 || metadata.getContentLength() > MAX_BYTES
                    || !"video/mp4".equalsIgnoreCase(metadata.getContentType())) {
                return ResponseResult.failure("视频格式或大小不符合要求，请上传不超过 8 MiB 的 MP4");
            }
        } catch (RuntimeException exception) {
            log.warn("首页视频对象校验失败: {}", key, exception);
            return ResponseResult.failure("OSS 视频尚不可用，请检查上传结果后重试");
        }
        WebsiteInfo before = websiteInfoMapper.selectById(WebsiteInfoConst.WEBSITE_INFO_ID);
        if (before == null) return ResponseResult.failure("网站信息不存在");
        LambdaUpdateWrapper<WebsiteInfo> update = new LambdaUpdateWrapper<WebsiteInfo>()
                .eq(WebsiteInfo::getId, WebsiteInfoConst.WEBSITE_INFO_ID)
                .set(WebsiteInfo::getHeroVideoKey, key);
        if (before.getHeroVideoKey() == null) update.isNull(WebsiteInfo::getHeroVideoKey);
        else update.eq(WebsiteInfo::getHeroVideoKey, before.getHeroVideoKey());
        int updated = websiteInfoMapper.update(null, update);
        if (updated != 1) return ResponseResult.failure("保存首页视频失败");
        afterCommit(() -> {
            try { redis.delete(ticketKey(key)); }
            catch (RuntimeException exception) { log.warn("首页视频上传凭证清理失败: {}", key, exception); }
            cleanupOld(before.getHeroVideoKey(), key);
        });
        return ResponseResult.success(new VideoState(mediaDomain() + "/" + key, key));
    }

    @Transactional
    public ResponseResult<Void> remove() {
        WebsiteInfo before = websiteInfoMapper.selectById(WebsiteInfoConst.WEBSITE_INFO_ID);
        if (before == null) return ResponseResult.failure("网站信息不存在");
        LambdaUpdateWrapper<WebsiteInfo> update = new LambdaUpdateWrapper<WebsiteInfo>()
                .eq(WebsiteInfo::getId, WebsiteInfoConst.WEBSITE_INFO_ID)
                .set(WebsiteInfo::getHeroVideoKey, null);
        if (before.getHeroVideoKey() == null) update.isNull(WebsiteInfo::getHeroVideoKey);
        else update.eq(WebsiteInfo::getHeroVideoKey, before.getHeroVideoKey());
        int updated = websiteInfoMapper.update(null, update);
        if (updated != 1) return ResponseResult.failure("移除首页视频失败");
        afterCommit(() -> cleanupOld(before.getHeroVideoKey(), null));
        return ResponseResult.success();
    }

    private boolean validKey(String key) {
        return key != null && key.matches("blog/home/hero-video/[0-9a-f-]{36}\\.mp4");
    }

    private String ticketKey(String key) { return "home:hero:video:ticket:" + key; }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else action.run();
    }

    private void cleanupOld(String oldKey, String newKey) {
        if (validKey(oldKey) && !oldKey.equals(newKey)) {
            try { ossClient.deleteObject(ossProperties.getBucketName(), oldKey); }
            catch (RuntimeException exception) { log.warn("旧首页视频清理失败: {}", oldKey, exception); }
        }
    }
}
