package xyz.luyu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.luyu.interceptor.SiteModuleAccessInterceptor;
import xyz.luyu.service.ExperienceOverviewService;
import xyz.luyu.service.SiteModuleAccessService;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExperienceOverviewControllerTest {
    @Test
    void publicOverviewOnlyRevealsCountsWithoutRequiringExperienceAccess() throws Exception {
        ExperienceOverviewService overview = mock(ExperienceOverviewService.class);
        SiteModuleAccessService access = mock(SiteModuleAccessService.class);
        when(overview.publicCounts()).thenReturn(new ExperienceOverviewService.Overview(2, List.of(3L, 1L)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ExperienceOverviewController(overview))
                .addInterceptors(new SiteModuleAccessInterceptor(access)).build();

        mvc.perform(get("/experience-overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.projectCounts[0]").value(3))
                .andExpect(jsonPath("$.data.projectCounts[1]").value(1))
                .andExpect(jsonPath("$.data.company").doesNotExist())
                .andExpect(jsonPath("$.data.projectNames").doesNotExist());
        verifyNoInteractions(access);
    }
}
