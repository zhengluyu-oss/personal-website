package xyz.kuailemao.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.config.OssProperties;
import xyz.kuailemao.domain.entity.WebsiteInfo;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.service.impl.WebsiteInfoServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebsiteInfoHeroVideoTest {
    @Test void oldRecordKeepsCopyAndHasNoVideoUrl() {
        WebsiteInfoServiceImpl service = spy(new WebsiteInfoServiceImpl());
        WebsiteInfo info = new WebsiteInfo();
        info.setHeroTitle("旧首页文案");
        doReturn(info).when(service).getById(any());
        ArticleMapper articles = mock(ArticleMapper.class);
        ReflectionTestUtils.setField(service, "articleMapper", articles);
        ReflectionTestUtils.setField(service, "ossProperties", new OssProperties());
        var result = service.selectWebsiteInfo();
        assertEquals("旧首页文案", result.getHeroTitle());
        assertNull(result.getHeroVideoUrl());
        verify(service, times(1)).getById(any());
    }

    @Test void configuredVideoUsesMediaDomainWithoutChangingOtherFields() {
        WebsiteInfoServiceImpl service = spy(new WebsiteInfoServiceImpl());
        WebsiteInfo info = new WebsiteInfo();
        info.setHeroTitle("首页文案");
        info.setHeroVideoKey("blog/home/hero-video/11111111-1111-1111-1111-111111111111.mp4");
        doReturn(info).when(service).getById(any());
        ReflectionTestUtils.setField(service, "articleMapper", mock(ArticleMapper.class));
        OssProperties props = new OssProperties();
        props.setMediaDomain("https://media.example.com/");
        ReflectionTestUtils.setField(service, "ossProperties", props);
        var result = service.selectWebsiteInfo();
        assertEquals("首页文案", result.getHeroTitle());
        assertEquals("https://media.example.com/" + info.getHeroVideoKey(), result.getHeroVideoUrl());
    }
}
