package xyz.kuailemao.diary;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import xyz.kuailemao.filter.JwtAuthorizeFilter;
import xyz.kuailemao.domain.entity.LoginUser;
import org.springframework.security.authorization.AuthorizationDecision;
import java.io.IOException;

@Configuration(proxyBeanMethods=false)
public class DiarySecurityConfiguration {
    @Bean
    @Order(0)
    SecurityFilterChain diarySecurityFilterChain(HttpSecurity http,JwtAuthorizeFilter jwt,DiaryProperties properties) throws Exception {
        return http.securityMatcher("/diary/**")
                .authorizeHttpRequests(auth->auth
                    .requestMatchers("/diary/auth/capabilities").authenticated()
                    .anyRequest().access((authentication,context)->new AuthorizationDecision(
                        authentication.get().getPrincipal() instanceof LoginUser user
                        && properties.getOwnerUserId()!=null
                        && properties.getOwnerUserId().equals(user.getUser().getId()))))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session->session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors->errors
                    .authenticationEntryPoint((request,response,exception)->reject(response,401,"请先登录"))
                    .accessDeniedHandler((request,response,exception)->reject(response,403,"无权访问日记")))
                .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
                            throws ServletException,IOException {
                        response.setHeader("Cache-Control","private, no-store");
                        response.setHeader("X-Robots-Tag","noindex, nofollow, noarchive");
                        chain.doFilter(request,response);
                    }
                },JwtAuthorizeFilter.class).build();
    }
    private static void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setHeader("Cache-Control","private, no-store");
        response.setHeader("X-Robots-Tag","noindex, nofollow, noarchive");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":"+status+",\"msg\":\""+message+"\"}");
    }
}
