package xyz.luyu.diary.ai;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import xyz.luyu.diary.*;
import xyz.luyu.domain.entity.*;
import xyz.luyu.filter.JwtAuthorizeFilter;
import xyz.luyu.utils.JwtUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DiaryAiSecurityTest {
    @Configuration(proxyBeanMethods=false) @EnableWebSecurity @EnableWebMvc
    @Import({DiarySecurityConfiguration.class,DiaryAiController.class,DiaryConversationController.class,DiaryAiExceptionHandler.class,DiaryAiService.class,DiaryService.class})
    static class Config {
    @Bean xyz.luyu.service.SiteModuleAccessService modules(){ var modules=mock(xyz.luyu.service.SiteModuleAccessService.class);when(modules.canView("diary")).thenReturn(true);return modules; }

        @Bean DiaryProperties diary(){var p=new DiaryProperties();p.setOwnerUserId(7L);return p;}
        @Bean DiaryAiProperties ai(){return new DiaryAiProperties();}
        @Bean JwtAuthorizeFilter filter(){return new JwtAuthorizeFilter();}
    }
    @Test void everyAiRouteRejectsAnonymousAndOtherAdminsBeforeReadingPrivateBody() throws Exception {
        try(var c=new AnnotationConfigWebApplicationContext()) {
            var jwt=mock(JwtUtils.class);var a=mock(DecodedJWT.class);var b=mock(DecodedJWT.class);var d=mock(DecodedJWT.class);
            when(jwt.resolveJwt("Bearer owner-fixture")).thenReturn(a);when(jwt.resolveJwt("Bearer other-admin-fixture")).thenReturn(b);when(jwt.resolveJwt("Bearer other-user-fixture")).thenReturn(d);
            when(jwt.toUser(a)).thenReturn(new LoginUser(User.builder().id(7L).build(),List.of("ROLE_ADMIN")));
            when(jwt.toUser(b)).thenReturn(new LoginUser(User.builder().id(8L).build(),List.of("ROLE_ADMIN")));
            when(jwt.toUser(d)).thenReturn(new LoginUser(User.builder().id(9L).build(),List.of("ROLE_USER")));
            c.setServletContext(new MockServletContext());c.addBeanFactoryPostProcessor(f->f.registerSingleton("jwtUtils",jwt));c.register(Config.class);c.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(c).addFilters(c.getBean("springSecurityFilterChain",Filter.class)).build();
            for(String path:List.of("/capabilities","/index","/index/pause","/questions","/reviews/preview","/reviews","/jobs/00000000-0000-4000-8000-000000000001","/jobs/00000000-0000-4000-8000-000000000001/cancel","/reports","/reports/00000000-0000-4000-8000-000000000001","/conversations","/conversations/questions","/conversations/reviews","/conversations/00000000-0000-4000-8000-000000000001","/conversations/00000000-0000-4000-8000-000000000001/turns"))
                for(var method:List.of(org.springframework.http.HttpMethod.GET,org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.DELETE)) {
                    mvc.perform(request(method,"/diary/auth/ai"+path).contentType("application/json").content("private-probe"))
                        .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
                    for(String token:List.of("other-admin-fixture","other-user-fixture"))mvc.perform(request(method,"/diary/auth/ai"+path).header("Authorization","Bearer "+token).contentType("application/json").content("private-probe"))
                        .andExpect(status().isForbidden()).andExpect(header().string("X-Robots-Tag","noindex, nofollow, noarchive"));
                }
            mvc.perform(get("/diary/auth/ai/capabilities").header("Authorization","Bearer owner-fixture")).andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));
            mvc.perform(post("/diary/auth/ai/questions").header("Authorization","Bearer owner-fixture").contentType("application/json").content("invalid-private-probe"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("invalid-private-probe"))));
            mvc.perform(get("/diary/auth/ai/index").header("Authorization","Bearer owner-fixture")).andExpect(status().isServiceUnavailable());
        }
    }
}
