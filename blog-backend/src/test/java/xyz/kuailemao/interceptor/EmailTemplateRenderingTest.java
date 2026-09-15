package xyz.kuailemao.interceptor;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import xyz.kuailemao.enums.MailboxAlertsEnum;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EmailTemplateRenderingTest {
    @Test
    void allNotificationTypesRenderDynamicContentAndLinks() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var context = new Context();
        context.setVariables(Map.ofEntries(
                Map.entry("code", "012345"), Map.entry("expirationTime", "5分钟"),
                Map.entry("toUrl", "https://example.com/notice"),
                Map.entry("openSourceAddress", "https://example.com/source"),
                Map.entry("url", "https://example.com/article/1"),
                Map.entry("name", "测试网站"), Map.entry("description", "网站介绍"),
                Map.entry("background", "https://example.com/cover.png"),
                Map.entry("linkEmail", "test@example.com"),
                Map.entry("verifyCode", "https://example.com/review?verifyCode=test"),
                Map.entry("title", "测试文章"), Map.entry("nickname", "新评论者"),
                Map.entry("time", "2026-09-15"), Map.entry("content", "<script>alert(1)</script>"),
                Map.entry("replyNickname", "原评论者"), Map.entry("replyTime", "2026-09-14"),
                Map.entry("replyContent", "原评论内容")));
        for (int type : new int[]{1, 2}) {
            context.setVariable("type", type);
            for (var notification : MailboxAlertsEnum.values()) {
                String html = engine.process(notification.getTemplateName(), context);
                assertTrue(html.contains("href=\"https://example.com/notice\""), notification.name());
                assertFalse(html.contains(" th:"), notification.name());
                assertFalse(html.contains("<script>"), notification.name());
                if (notification == MailboxAlertsEnum.REGISTER || notification == MailboxAlertsEnum.ADMIN_LOGIN
                        || notification == MailboxAlertsEnum.RESET || notification == MailboxAlertsEnum.RESET_EMAIL) {
                    assertTrue(html.contains("012345"));
                    assertTrue(html.contains("5分钟"));
                }
                if (notification == MailboxAlertsEnum.REPLY_COMMENT_NOTIFICATION_EMAIL) {
                    assertTrue(html.contains("原评论内容"));
                    assertTrue(html.contains("新评论者"));
                    assertTrue(html.contains("&lt;script&gt;"));
                    assertEquals(type == 1, html.contains("测试文章"));
                }
                if (notification == MailboxAlertsEnum.FRIEND_LINK_APPLICATION) {
                    assertTrue(html.contains("href=\"https://example.com/review?verifyCode=test\""));
                    assertTrue(html.contains("test@example.com"));
                }
            }
        }
    }
}
