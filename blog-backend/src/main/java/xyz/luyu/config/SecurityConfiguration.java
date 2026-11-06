package xyz.luyu.config;

import jakarta.annotation.Resource;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import xyz.luyu.constants.SecurityConst;
import xyz.luyu.filter.JwtAuthorizeFilter;
import xyz.luyu.filter.AuthenticationRateLimitFilter;
import xyz.luyu.handler.SecurityHandler;

/**
 * @author luyu
 * <p>
 * 创建时间：2023/10/10 16:26
 */
@Configuration
public class SecurityConfiguration {

    @Resource
    private SecurityHandler securityHandler;

    @Resource
    private JwtAuthorizeFilter jwtAuthorizeFilter;

    @Resource
    private AuthenticationRateLimitFilter authenticationRateLimitFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ObjectProvider<RequestMappingHandlerMapping> mappings) throws Exception {
        return http
                .authorizeHttpRequests(conf -> conf
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // Only the listed methods may be reached without a session.
                        .requestMatchers(HttpMethod.GET,
                                "/article/search/init/title", "/article/search/by/content", "/article/hot",
                                "/article/list", "/article/blog-feed", "/article/recommend", "/article/random",
                                "/article/detail/{id}", "/article/related/{categoryId}/{articleId}",
                                "/article/where/list/{typeId}", "/article/visit/{id}",
                                "/banners/list", "/category/list", "/comment/getComment", "/experience-overview",
                                "/experience/{experienceId}/projects", "/experience/{experienceId}/projects/{projectId}",
                                "/favorite/whether/favorite", "/like/whether/like", "/leaveWord/list",
                                "/link/list", "/link/email/apply", "/photo/list",
                                "/oauth/gitee/render", "/oauth/gitee/callback", "/oauth/github/render", "/oauth/github/callback",
                                "/public/ask-code", "/public/email-delivery", "/site-modules/access", "/site-modules/about",
                                "/tag/list", "/treeHole/getTreeHoleList", "/websiteInfo/front",
                                "/website-share/list", "/website-share/{id}", "/website-share/from-article/{articleId}",
                                "/experience/list", "/experience/{id}").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/oauth/exchange", "/user/admin-login/verify", "/user/admin-login/resend",
                                "/user/register", "/user/reset-confirm", "/user/reset-password", "/user/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/user/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/user/logout").permitAll()
                        // Existing member actions without a method permission are listed individually.
                        .requestMatchers(HttpMethod.GET, "/user/auth/info", "/menu/router/list/{typeId}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/user/auth/update", "/user/auth/upload/avatar",
                                "/user/auth/update/email", "/user/auth/third/update/email",
                                "/comment/auth/add/comment", "/favorite/auth/favorite", "/like/auth/like",
                                "/leaveWord/auth/userLeaveWord", "/link/auth/apply", "/treeHole/auth/addTreeHole").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/favorite/auth/favorite", "/like/auth/like").authenticated()
                        // Existing administrative methods retain their @PreAuthorize checks.
                        .requestMatchers(request -> {
                            RequestMappingHandlerMapping mapping = mappings.getIfAvailable();
                            if (mapping == null) return false;
                            try {
                                var chain = mapping.getHandler(request);
                                if (chain == null || !(chain.getHandler() instanceof HandlerMethod method)) return false;
                                return AnnotatedElementUtils.hasAnnotation(method.getMethod(), PreAuthorize.class)
                                        || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PreAuthorize.class);
                            } catch (Exception exception) {
                                return false;
                            }
                        }).authenticated()
                        .anyRequest().denyAll()
                )
                .formLogin(conf -> conf
                        // 登录页面
                        .loginProcessingUrl(SecurityConst.LOGIN_PAGE)
                        // 成功登录处理
                        .successHandler(securityHandler::onAuthenticationSuccess)
                        // 失败登录处理
                        .failureHandler(securityHandler::onAuthenticationFailure)
                )
                .logout(conf -> conf
                        // 登出页面
                        .logoutUrl(SecurityConst.LOGOUT_PAGE)
                        // 退出登录处理
                        .logoutSuccessHandler(securityHandler::onLogoutSuccess)
                )
                .exceptionHandling(conf -> conf
                        // 没有登录处理
                        .authenticationEntryPoint(securityHandler::onUnAuthenticated)
                        // 没有权限处理
                        .accessDeniedHandler(securityHandler::onAccessDeny)
                )
                // 禁用 csrf
                .csrf(AbstractHttpConfigurer::disable)
                // 不处理 session ，使用token
                .sessionManagement(conf -> conf.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // token 校验添加过滤器
                .addFilterBefore(jwtAuthorizeFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(authenticationRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

}
