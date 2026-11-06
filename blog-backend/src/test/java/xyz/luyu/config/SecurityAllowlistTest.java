package xyz.luyu.config;

import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.mock.web.MockServletContext;
import xyz.luyu.filter.AuthenticationRateLimitFilter;
import xyz.luyu.filter.JwtAuthorizeFilter;
import xyz.luyu.handler.SecurityHandler;
import xyz.luyu.utils.JwtUtils;
import xyz.luyu.utils.RedisCache;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityAllowlistTest {
    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @EnableMethodSecurity
    @org.springframework.context.annotation.Import({SecurityConfiguration.class, JwtAuthorizeFilter.class, AuthenticationRateLimitFilter.class})
    static class Config {
        @Bean UserDetailsService users() { return username -> { throw new UsernameNotFoundException(username); }; }
        @Bean FixtureController fixtureController() { return new FixtureController(); }
    }

    @RestController
    static class FixtureController {
        @GetMapping({"/article/list", "/website-share/list", "/website-share/1", "/public/ask-code"})
        String publicRead() { return "public"; }
        @PostMapping("/user/register") String register() { return "registered"; }
        @GetMapping("/unlisted") String unlisted() { return "unlisted"; }
        @PreAuthorize("hasAuthority('blog:websiteShare:list')")
        @GetMapping("/website-share/back/list") String backList() { return "admin"; }
    }

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.addBeanFactoryPostProcessor(factory -> {
            SecurityHandler handler = mock(SecurityHandler.class);
            try {
                doAnswer(call -> { ((jakarta.servlet.http.HttpServletResponse) call.getArgument(1)).setStatus(401); return null; })
                        .when(handler).onUnAuthenticated(any(), any(), any());
                doAnswer(call -> { ((jakarta.servlet.http.HttpServletResponse) call.getArgument(1)).setStatus(403); return null; })
                        .when(handler).onAccessDeny(any(), any(), any());
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            factory.registerSingleton("securityHandler", handler);
            factory.registerSingleton("jwtUtils", mock(JwtUtils.class));
            factory.registerSingleton("redisCache", mock(RedisCache.class));
        });
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach void cleanup() { context.close(); }

    @Test void publicReadsAndRegistrationAreAnonymousButMethodSpecific() throws Exception {
        mvc.perform(get("/article/list")).andExpect(status().isOk());
        mvc.perform(get("/website-share/list")).andExpect(status().isOk());
        mvc.perform(get("/website-share/1")).andExpect(status().isOk());
        mvc.perform(get("/public/ask-code")).andExpect(status().isOk());
        mvc.perform(post("/user/register")).andExpect(status().isOk());
        mvc.perform(post("/article/list")).andExpect(status().isUnauthorized());
    }

    @Test void unknownRoutesAndUnannotatedMethodsAreDeniedEvenWhenSignedIn() throws Exception {
        mvc.perform(get("/unlisted")).andExpect(status().isUnauthorized());
        mvc.perform(get("/unlisted").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(get("/ghost").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(get("/menu/new").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(post("/user/auth/new").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(get("/diary/unlisted").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(get("/knowledge/unlisted").with(user("member"))).andExpect(status().isForbidden());
    }

    @Test void annotatedAdminMethodStillChecksAuthority() throws Exception {
        mvc.perform(get("/website-share/back/list")).andExpect(status().isUnauthorized());
        mvc.perform(get("/website-share/back/list").with(user("member"))).andExpect(status().isForbidden());
        mvc.perform(get("/website-share/back/list").with(user("admin").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("blog:websiteShare:list"))))
                .andExpect(status().isOk()).andExpect(content().string("admin"));
    }
}
