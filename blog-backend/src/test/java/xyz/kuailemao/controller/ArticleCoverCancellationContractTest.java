package xyz.kuailemao.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.service.ArticleService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ArticleCoverCancellationContractTest {
    @Test void deleteUsesTheExistingQueryFieldAndGetCannotMutate() throws Exception {
        ArticleService service = mock(ArticleService.class);
        ArticleController controller = new ArticleController();
        ReflectionTestUtils.setField(controller, "articleService", service);
        String url = "https://media.example.invalid/article/articleCover/synthetic.png";
        when(service.deleteArticleCover(url)).thenReturn(ResponseResult.success());
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(get("/article/delete/articleCover").param("articleCoverUrl", url)).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
        mvc.perform(delete("/article/delete/articleCover").param("articleCoverUrl", url))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(service).deleteArticleCover(url);
        assertEquals("hasAnyAuthority('blog:publish:article')", ArticleController.class
                .getMethod("deleteArticleCover", String.class).getAnnotation(PreAuthorize.class).value());
    }
}
