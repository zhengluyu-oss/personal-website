package xyz.kuailemao.interceptor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import xyz.kuailemao.controller.ArticleController;
import xyz.kuailemao.controller.CommentController;
import xyz.kuailemao.service.ArticleService;
import xyz.kuailemao.service.CommentService;
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.handler.SiteModuleExceptionHandler;
import xyz.kuailemao.handler.SiteModuleRequestAdvice;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SiteModuleAccessInterceptorTest {
    SiteModuleAccessService access = mock(SiteModuleAccessService.class);
    ArticleService articles = mock(ArticleService.class);
    CommentService comments = mock(CommentService.class);
    MockMvc mvc() {
        ArticleController controller = new ArticleController(); ReflectionTestUtils.setField(controller, "articleService", articles);
        CommentController comment = new CommentController(); ReflectionTestUtils.setField(comment, "commentService", comments);
        return MockMvcBuilders.standaloneSetup(controller, comment)
                .addInterceptors(new SiteModuleAccessInterceptor(access))
                .setControllerAdvice(new SiteModuleExceptionHandler(), new SiteModuleRequestAdvice(access)).build();
    }
    @Test void directDetailAndSearchNeverReachServiceWhenDenied() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "没有查看权限")).when(access).require("blog");
        MockMvc mvc = mvc();
        for (String path : new String[]{"/article/detail/5", "/article/search/init/title", "/article/search/by/content?content=secret", "/article/blog-feed", "/article/recommend", "/article/random"})
            mvc.perform(get(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
                    .andExpect(header().string("Cache-Control", "private, no-store"));
        verifyNoInteractions(articles);
    }
    @Test void anonymousDeniedRequestReturns401() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请登录")).when(access).require("blog");
        mvc().perform(get("/article/detail/5")).andExpect(status().isUnauthorized());
        verifyNoInteractions(articles);
    }
    @Test void allowedRequestReachesExistingContentService() throws Exception {
        mvc().perform(get("/article/detail/5")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"));
        verify(articles).getArticleDetail(5);
    }
    @Test void articleCommentReadCannotBypassModuleGate() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "没有查看权限")).when(access).require("blog");
        mvc().perform(get("/comment/getComment?type=1&typeId=5&pageNum=1&pageSize=10")).andExpect(status().isForbidden());
        verifyNoInteractions(comments);
    }
    @Test void articleCommentBodyCannotBypassModuleGate() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "没有查看权限")).when(access).require("blog");
        mvc().perform(post("/comment/auth/add/comment").contentType("application/json")
                .content("{\"type\":1,\"typeId\":5,\"commentContent\":\"test\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(comments);
    }
    @Test void moduleCoverageIncludesAliasesAndNestedContent() {
        assertEquals("experience", SiteModuleAccessInterceptor.moduleFor("ExperienceProjectController", null));
        assertEquals("shares", SiteModuleAccessInterceptor.moduleFor("WebsiteShareController", null));
        assertEquals("photos", SiteModuleAccessInterceptor.moduleFor("PhotoController", null));
        assertEquals("blog", SiteModuleAccessInterceptor.moduleFor("TagController", null));
        assertEquals("blog", SiteModuleAccessInterceptor.moduleFor("CategoryController", null));
        assertEquals("home", SiteModuleAccessInterceptor.moduleFor("BannersController", null));
        assertEquals("blog", SiteModuleAccessInterceptor.moduleFor("CommentController", "01"));
        assertEquals("blog", SiteModuleAccessInterceptor.moduleFor("LikeController", "+1"));
        assertNull(SiteModuleAccessInterceptor.moduleFor("CommentController", "2"));
    }
}
