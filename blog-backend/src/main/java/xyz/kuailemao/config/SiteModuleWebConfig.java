package xyz.kuailemao.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import xyz.kuailemao.interceptor.SiteModuleAccessInterceptor;

@Configuration
public class SiteModuleWebConfig implements WebMvcConfigurer {
    private final SiteModuleAccessInterceptor interceptor;
    public SiteModuleWebConfig(SiteModuleAccessInterceptor interceptor) { this.interceptor = interceptor; }
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/**").order(-100);
    }
}
