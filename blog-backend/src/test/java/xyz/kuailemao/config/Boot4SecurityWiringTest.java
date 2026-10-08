package xyz.kuailemao.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import xyz.kuailemao.filter.AuthenticationRateLimitFilter;
import xyz.kuailemao.filter.JwtAuthorizeFilter;
import xyz.kuailemao.handler.SecurityHandler;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class Boot4SecurityWiringTest {
    @Test void existingStatelessLoginAndFiltersWireOnSpringSecurity7() {
        new WebApplicationContextRunner().withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class, ServletWebSecurityAutoConfiguration.class))
                .withUserConfiguration(SecurityConfiguration.class)
                .withInitializer(context -> {
                    // Already-initialized boundary doubles must not have their production @Resource fields wired.
                    context.getBeanFactory().registerSingleton("securityHandler", mock(SecurityHandler.class));
                    context.getBeanFactory().registerSingleton("jwtAuthorizeFilter", mock(JwtAuthorizeFilter.class));
                    context.getBeanFactory().registerSingleton("authenticationRateLimitFilter", mock(AuthenticationRateLimitFilter.class));
                })
                .withBean(UserDetailsService.class, () -> username -> { throw new UsernameNotFoundException("synthetic"); })
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var chain = context.getBean(SecurityFilterChain.class);
                    assertTrue(chain.getFilters().stream().anyMatch(JwtAuthorizeFilter.class::isInstance));
                    assertTrue(chain.getFilters().stream().anyMatch(AuthenticationRateLimitFilter.class::isInstance));
                });
    }
}
