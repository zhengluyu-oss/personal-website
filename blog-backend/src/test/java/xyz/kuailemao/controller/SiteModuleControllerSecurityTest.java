package xyz.kuailemao.controller;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.kuailemao.service.SiteModuleAccessService;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SiteModuleControllerSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean SiteModuleAccessService service() { return mock(SiteModuleAccessService.class); }
        @Bean SiteModuleController controller(SiteModuleAccessService service) { return new SiteModuleController(service); }
    }
    AnnotationConfigApplicationContext context;
    @BeforeEach void setup() { context = new AnnotationConfigApplicationContext(Config.class); }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    void login(String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test-user", null,
                List.of(new SimpleGrantedAuthority(authority))));
    }
    @Test void guestCannotReadOrChangeRolePolicies() {
        var controller = context.getBean(SiteModuleController.class);
        assertThrows(org.springframework.security.core.AuthenticationException.class, controller::policies);
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> controller.update("blog", new SiteModuleController.Update(false, List.of(2L), 0)));
        verifyNoInteractions(context.getBean(SiteModuleAccessService.class));
    }
    @Test void readOnlyRoleManagerCannotModifyPolicies() {
        login("system:role:list");
        var controller = context.getBean(SiteModuleController.class);
        controller.policies();
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> controller.update("blog", new SiteModuleController.Update(false, List.of(2L), 0)));
        verify(context.getBean(SiteModuleAccessService.class)).policies();
        verify(context.getBean(SiteModuleAccessService.class), never()).update(anyString(), anyBoolean(), anyList(), anyInt());
    }
    @Test void roleUpdaterCanSavePolicies() {
        login("system:role:update");
        context.getBean(SiteModuleController.class).update("blog", new SiteModuleController.Update(false, List.of(2L), 0));
        verify(context.getBean(SiteModuleAccessService.class)).update("blog", false, List.of(2L), 0);
    }
}
