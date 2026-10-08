package xyz.kuailemao.handler;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.domain.vo.WebsiteInfoVO;
import xyz.kuailemao.service.SiteModuleAccessService;

/** Shared site metadata remains available for navigation; gated module fields do not. */
@RestControllerAdvice
public class SiteModuleResponseAdvice implements ResponseBodyAdvice<Object> {
    private final SiteModuleAccessService service;
    public SiteModuleResponseAdvice(SiteModuleAccessService service) { this.service = service; }
    @Override
    public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return method.getDeclaringClass().getSimpleName().equals("WebsiteInfoController")
                && "selectWebsiteInfoByFront".equals(method.getMethod().getName());
    }
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        response.getHeaders().setCacheControl("private, no-store");
        if (body instanceof ResponseResult<?> result && result.getData() instanceof WebsiteInfoVO info) {
            if (!service.canView("home")) {
                info.setHeroKicker(null); info.setHeroTitle(null); info.setHeroSubtitle(null); info.setHeroDescription(null);
                info.setHeroPrimaryText(null); info.setHeroPrimaryUrl(null); info.setHeroSecondaryText(null); info.setHeroSecondaryUrl(null);
                info.setHeroAsideLabel(null); info.setHeroAsideText(null); info.setHeaderNotification(null); info.setSidebarAnnouncement(null);
            }
            if (!service.canView("blog")) {
                info.setBlogFeaturedArticleId(null); info.setBlogFeaturedArticleTitle(null); info.setArticleCount(null);
                info.setCategoryCount(null); info.setCommentCount(null); info.setWordCount(null); info.setLastUpdateTime(null); info.setVisitCount(null);
            }
            if (!service.canView("about")) {
                info.setWebmasterAvatar(null); info.setWebmasterCopy(null); info.setWebmasterProfileBackground(null);
                info.setGithubLink(null); info.setGiteeLink(null);
            }
        }
        return body;
    }
}
