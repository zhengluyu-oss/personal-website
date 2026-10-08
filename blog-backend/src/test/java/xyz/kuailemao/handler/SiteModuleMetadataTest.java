package xyz.kuailemao.handler;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import xyz.kuailemao.controller.WebsiteInfoController;
import xyz.kuailemao.controller.SiteProfileController;
import xyz.kuailemao.domain.vo.WebsiteInfoVO;
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.service.WebsiteInfoService;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SiteModuleMetadataTest {
    @Test void sharedMetadataDoesNotExposeGatedHomeBlogOrProfileFields() throws Exception {
        var access = mock(SiteModuleAccessService.class);
        var website = mock(WebsiteInfoService.class);
        var info = new WebsiteInfoVO(); info.setWebsiteName("public-brand"); info.setHeroDescription("private-home");
        info.setBlogFeaturedArticleTitle("private-title"); info.setWebmasterCopy("private-profile"); info.setArticleCount(4L);
        when(website.selectWebsiteInfo()).thenReturn(info);
        var controller = new WebsiteInfoController(); ReflectionTestUtils.setField(controller, "websiteInfoService", website);
        MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new SiteModuleResponseAdvice(access)).build()
                .perform(get("/websiteInfo/front")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.websiteName").value("public-brand"))
                .andExpect(jsonPath("$.data.heroDescription").doesNotExist())
                .andExpect(jsonPath("$.data.blogFeaturedArticleTitle").doesNotExist())
                .andExpect(jsonPath("$.data.articleCount").doesNotExist())
                .andExpect(jsonPath("$.data.webmasterCopy").doesNotExist())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }
    @Test void directProfileEndpointRequiresAboutPermission() throws Exception {
        var access = mock(SiteModuleAccessService.class); var website = mock(WebsiteInfoService.class);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "没有权限")).when(access).require("about");
        MockMvcBuilders.standaloneSetup(new SiteProfileController(access, website))
                .setControllerAdvice(new SiteModuleExceptionHandler()).build()
                .perform(get("/site-modules/about")).andExpect(status().isForbidden());
        verifyNoInteractions(website);
    }
}
