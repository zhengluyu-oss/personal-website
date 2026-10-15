package xyz.kuailemao.diary;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.filter.JwtAuthorizeFilter;
import xyz.kuailemao.utils.JwtUtils;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DiarySecurityTest {
    @Configuration(proxyBeanMethods=false) @EnableWebSecurity @EnableWebMvc
    static class Config {
        @Bean DiaryProperties properties(){var p=new DiaryProperties();p.setOwnerUserId(7L);return p;}
        static JwtUtils jwtFixture(){
            var jwt=mock(JwtUtils.class);var owner=mock(DecodedJWT.class);var other=mock(DecodedJWT.class);var normal=mock(DecodedJWT.class);
            when(jwt.resolveJwt("Bearer owner-fixture")).thenReturn(owner);
            when(jwt.resolveJwt("Bearer other-admin-fixture")).thenReturn(other);
            when(jwt.resolveJwt("Bearer other-user-fixture")).thenReturn(normal);
            when(jwt.toUser(owner)).thenReturn(new LoginUser(User.builder().id(7L).build(),List.of("ROLE_ADMIN")));
            when(jwt.toUser(other)).thenReturn(new LoginUser(User.builder().id(8L).build(),List.of("ROLE_ADMIN")));
            when(jwt.toUser(normal)).thenReturn(new LoginUser(User.builder().id(9L).build(),List.of("ROLE_USER")));
            return jwt;
        }
        @Bean JwtAuthorizeFilter filter(){return new JwtAuthorizeFilter();}
        @Bean DiaryService service(DiaryProperties p,org.springframework.beans.factory.ObjectProvider<DiaryStore> stores){return new DiaryService(p,stores);}
        @Bean DiaryController controller(DiaryService service){return new DiaryController(service);}
        @Bean DiaryExceptionHandler handler(){return new DiaryExceptionHandler();}
    }
    @Test void authenticationAndOwnerCheckPrecedeBodyBindingForAllDiaryMethods() throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.addBeanFactoryPostProcessor(factory->factory.registerSingleton("jwtUtils",Config.jwtFixture()));
            context.register(Config.class,DiarySecurityConfiguration.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
            for(var method:List.of(org.springframework.http.HttpMethod.GET,org.springframework.http.HttpMethod.POST,
                    org.springframework.http.HttpMethod.PUT,org.springframework.http.HttpMethod.DELETE)) {
                mvc.perform(request(method,"/diary/auth/entries").contentType("application/json").content("invalid-private-fixture"))
                        .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
                mvc.perform(request(method,"/diary/auth/entries").header("Authorization","Bearer other-admin-fixture")
                        .contentType("application/json").content("invalid-private-fixture"))
                        .andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("invalid-private-fixture"))));
                mvc.perform(request(method,"/diary/auth/entries").header("Authorization","Bearer other-user-fixture")
                        .contentType("application/json").content("invalid-private-fixture"))
                        .andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","private, no-store"));
            }
            mvc.perform(get("/diary/auth/capabilities").header("Authorization","Bearer other-admin-fixture"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));
            mvc.perform(get("/diary/auth/entries/00000000-0000-4000-8000-000000000001").header("Authorization","Bearer owner-fixture"))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
