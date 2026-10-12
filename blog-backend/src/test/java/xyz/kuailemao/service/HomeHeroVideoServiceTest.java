package xyz.kuailemao.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PolicyConditions;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.config.OssProperties;
import xyz.kuailemao.domain.entity.WebsiteInfo;
import xyz.kuailemao.mapper.WebsiteInfoMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HomeHeroVideoServiceTest {
    private final HomeHeroVideoService service = new HomeHeroVideoService();
    private final OSS oss = mock(OSS.class);
    private final WebsiteInfoMapper mapper = mock(WebsiteInfoMapper.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final OssProperties properties = new OssProperties();

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WebsiteInfo.class);
        properties.setBucketName("test-bucket");
        properties.setEndpoint("oss-cn-beijing.aliyuncs.com");
        properties.setAccessKey("test-key");
        properties.setMediaDomain("https://media.example.com");
        ReflectionTestUtils.setField(service, "ossClient", oss);
        ReflectionTestUtils.setField(service, "ossProperties", properties);
        ReflectionTestUtils.setField(service, "redis", redis);
        ReflectionTestUtils.setField(service, "websiteInfoMapper", mapper);
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test void ticketUsesUniqueMp4KeyAndConstrainedPolicy() {
        String policyJson = "{\"expiration\":\"2026-10-12T04:00:00.000Z\",\"conditions\":[]}";
        when(oss.generatePostPolicy(any(), any(PolicyConditions.class))).thenReturn(policyJson);
        when(oss.calculatePostSignature(policyJson)).thenReturn("signature");
        var ticket = service.issueTicket();
        assertEquals(policyJson, new String(Base64.getDecoder().decode(ticket.policy()), StandardCharsets.UTF_8));
        assertEquals("signature", ticket.signature());
        assertTrue(ticket.objectKey().matches("blog/home/hero-video/[0-9a-f-]{36}\\.mp4"));
        assertEquals("https://test-bucket.oss-cn-beijing.aliyuncs.com", ticket.uploadUrl());
        assertEquals(100_000_000L, HomeHeroVideoService.MAX_BYTES);
        assertEquals(HomeHeroVideoService.MAX_BYTES, ticket.maxBytes());
        ArgumentCaptor<PolicyConditions> conditions = ArgumentCaptor.forClass(PolicyConditions.class);
        verify(oss).generatePostPolicy(any(), conditions.capture());
        String policy = conditions.getValue().jsonize().replace("\\/", "/");
        assertTrue(policy.contains(ticket.objectKey()), policy);
        assertTrue(policy.contains("video/mp4"), policy);
        assertTrue(policy.contains(Long.toString(HomeHeroVideoService.MAX_BYTES)), policy);
        verify(values).set(startsWith("home:hero:video:ticket:"), eq("pending"), any());
    }

    @Test void forgedAndExpiredKeysCannotReplaceVideo() {
        assertNotEquals(200, service.confirm("other/file.mp4").getCode());
        String key = "blog/home/hero-video/11111111-1111-1111-1111-111111111111.mp4";
        assertNotEquals(200, service.confirm(key).getCode());
        verifyNoInteractions(mapper, oss);
    }

    @Test void wrongMetadataPreservesPreviousVideo() {
        String key = "blog/home/hero-video/11111111-1111-1111-1111-111111111111.mp4";
        when(values.get(anyString())).thenReturn("pending");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(HomeHeroVideoService.MAX_BYTES + 1);
        metadata.setContentType("video/mp4");
        when(oss.getObjectMetadata("test-bucket", key)).thenReturn(metadata);
        assertNotEquals(200, service.confirm(key).getCode());
        metadata.setContentLength(1024);
        metadata.setContentType("application/octet-stream");
        assertNotEquals(200, service.confirm(key).getCode());
        verifyNoInteractions(mapper);
        verify(oss, never()).deleteObject(anyString(), anyString());
    }

    @Test void successfulReplacePublishesNewObjectAndCleansOldOne() {
        String key = "blog/home/hero-video/11111111-1111-1111-1111-111111111111.mp4";
        String old = "blog/home/hero-video/22222222-2222-2222-2222-222222222222.mp4";
        when(values.get(anyString())).thenReturn("pending");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(HomeHeroVideoService.MAX_BYTES);
        metadata.setContentType("video/mp4");
        when(oss.getObjectMetadata("test-bucket", key)).thenReturn(metadata);
        WebsiteInfo info = new WebsiteInfo();
        info.setHeroVideoKey(old);
        when(mapper.selectById(any())).thenReturn(info);
        when(mapper.update(isNull(), any())).thenReturn(1);
        var result = service.confirm(key);
        assertEquals(200, result.getCode());
        assertEquals("https://media.example.com/" + key, result.getData().url());
        verify(oss).deleteObject("test-bucket", old);
    }

    @Test void databaseFailureLeavesOldObjectIntact() {
        String key = "blog/home/hero-video/11111111-1111-1111-1111-111111111111.mp4";
        when(values.get(anyString())).thenReturn("pending");
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(1024);
        metadata.setContentType("video/mp4");
        when(oss.getObjectMetadata("test-bucket", key)).thenReturn(metadata);
        when(mapper.selectById(any())).thenReturn(new WebsiteInfo());
        when(mapper.update(isNull(), any())).thenReturn(0);
        assertNotEquals(200, service.confirm(key).getCode());
        verify(oss, never()).deleteObject(anyString(), anyString());
    }
}
