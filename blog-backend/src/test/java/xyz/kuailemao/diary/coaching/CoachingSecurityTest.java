package xyz.kuailemao.diary.coaching;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import xyz.kuailemao.diary.*;
import xyz.kuailemao.domain.entity.*;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.filter.JwtAuthorizeFilter;
import xyz.kuailemao.utils.*;
import com.alibaba.fastjson2.JSONObject;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CoachingSecurityTest {
    @Configuration(proxyBeanMethods=false) @EnableWebSecurity @EnableWebMvc
    @Import({DiaryService.class,CoachingService.class,CoachingController.class,CoachingExceptionHandler.class})
    static class Config {
    @Bean xyz.kuailemao.service.SiteModuleAccessService modules(){ var modules=mock(xyz.kuailemao.service.SiteModuleAccessService.class);when(modules.canView("diary")).thenReturn(true);return modules; }

        @Bean DiaryProperties diary() { var p=new DiaryProperties();p.setOwnerUserId(7L);return p; }
        @Bean CoachingProperties coaching() { return new CoachingProperties(); }
        @Bean JwtAuthorizeFilter filter() { return new JwtAuthorizeFilter(); }
    }
    @Test void everyCoachingRouteChecksOwnerBeforeBindingAndNeverAuditSerializesPrivateValues() throws Exception {
        var jwt=mock(JwtUtils.class);
        for(String name:List.of("owner","other-admin","other-user")) {
            var decoded=mock(DecodedJWT.class);when(jwt.resolveJwt("Bearer "+name)).thenReturn(decoded);
            when(jwt.toUser(decoded)).thenReturn(new LoginUser(User.builder().id(name.equals("owner")?7L:name.equals("other-admin")?8L:9L).build(),List.of(name.contains("user")?"ROLE_USER":"ROLE_ADMIN")));
        }
        try(var context=new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());context.addBeanFactoryPostProcessor(f->f.registerSingleton("jwtUtils",jwt));context.register(Config.class,DiarySecurityConfiguration.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
            for(String path:List.of("/capabilities","/goals","/actions","/feedback","/periods/preview","/periods/jobs","/periods/reports","/suggestion","/export")) {
                for(var method:List.of(org.springframework.http.HttpMethod.GET,org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.PUT,org.springframework.http.HttpMethod.DELETE)) {
                    mvc.perform(request(method,"/diary/auth/coaching"+path).contentType("application/json").content("private-invalid-probe"))
                        .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
                    for(String token:List.of("other-admin","other-user")) mvc.perform(request(method,"/diary/auth/coaching"+path).header("Authorization","Bearer "+token).contentType("application/json").content("private-invalid-probe"))
                        .andExpect(status().isForbidden()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-invalid-probe"))));
                }
            }
            mvc.perform(get("/diary/auth/coaching/capabilities").header("Authorization","Bearer owner"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(false));
            mvc.perform(get("/diary/auth/coaching/goals").header("Authorization","Bearer owner"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("X-Robots-Tag","noindex, nofollow, noarchive"));
        }
        String probe="fictional-coaching-audit-"+UUID.randomUUID();var request=new CoachingModels.Write(UUID.randomUUID(),UUID.randomUUID(),0,JSONObject.of("title",probe,"state","ACTIVE"),null,null);
        assertFalse(AuditDataProtection.request(new Object[]{request},CoachingController.class.getName(),"create").contains(probe));
        assertFalse(AuditDataProtection.response(ResponseResult.success(request)).contains(probe));
        for(var method:CoachingController.class.getDeclaredMethods()) assertTrue(Arrays.stream(method.getAnnotations()).noneMatch(a->a.annotationType().getSimpleName().equals("LogAnnotation")));
    }
}
