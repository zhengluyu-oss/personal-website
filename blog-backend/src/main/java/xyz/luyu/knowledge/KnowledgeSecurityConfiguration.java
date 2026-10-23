package xyz.luyu.knowledge;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.luyu.filter.JwtAuthorizeFilter;
import java.io.IOException;

@Configuration(proxyBeanMethods=false)
public class KnowledgeSecurityConfiguration {
    @Bean @Order(-1)
    SecurityFilterChain knowledgeSecurity(HttpSecurity http,JwtAuthorizeFilter jwt,KnowledgeAccess access)throws Exception{
        return http.securityMatcher("/knowledge/**").authorizeHttpRequests(a->a.requestMatchers("/knowledge/view/capabilities").permitAll()
            .requestMatchers("/knowledge/view/**").access((authentication,context)->new AuthorizationDecision(access.canRead()))
            .requestMatchers("/knowledge/manage/**").access((authentication,context)->new AuthorizationDecision(access.canManage())).anyRequest().denyAll())
            .csrf(AbstractHttpConfigurer::disable).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e->e.authenticationEntryPoint((q,r,x)->reject(r,401)).accessDeniedHandler((q,r,x)->reject(r,403)))
            .addFilterBefore(new OncePerRequestFilter(){@Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain c)throws jakarta.servlet.ServletException,IOException{r.setHeader("Cache-Control","private, no-store");r.setHeader("X-Robots-Tag","noindex, nofollow, noarchive");r.setHeader("X-Content-Type-Options","nosniff");c.doFilter(q,r);}},UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).build();
    }
    private static void reject(HttpServletResponse response,int status)throws IOException{response.setStatus(status);response.setContentType("application/json;charset=UTF-8");response.getWriter().write("{\"code\":"+status+",\"msg\":\"没有访问知识库的权限\"}");}
}
