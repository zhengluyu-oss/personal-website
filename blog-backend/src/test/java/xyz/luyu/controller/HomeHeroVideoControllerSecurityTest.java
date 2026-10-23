package xyz.luyu.controller;

import com.aliyun.oss.OSS;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import xyz.luyu.config.OssProperties;
import xyz.luyu.mapper.WebsiteInfoMapper;
import xyz.luyu.service.HomeHeroVideoService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class HomeHeroVideoControllerSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean HomeHeroVideoService service() { return mock(HomeHeroVideoService.class); }
        @Bean HomeHeroVideoController controller() { return new HomeHeroVideoController(); }
        @Bean OSS ossClient() { return mock(OSS.class); }
        @Bean OssProperties ossProperties() { return new OssProperties(); }
        @Bean StringRedisTemplate redis() { return mock(StringRedisTemplate.class); }
        @Bean WebsiteInfoMapper websiteInfoMapper() { return mock(WebsiteInfoMapper.class); }
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void readOnlyBannerManagerCannotIssueOrRemoveVideo() {
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("reader", null,
                    List.of(new SimpleGrantedAuthority("blog:banner:list"))));
            var controller = context.getBean(HomeHeroVideoController.class);
            controller.current();
            assertThrows(AccessDeniedException.class, controller::ticket);
            assertThrows(AccessDeniedException.class, controller::remove);
            verify(context.getBean(HomeHeroVideoService.class), never()).issueTicket();
        }
    }
}
