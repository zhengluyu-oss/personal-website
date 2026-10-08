package xyz.kuailemao.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import xyz.kuailemao.service.SiteModuleAccessService;

@Component
public class SiteModuleAccessInterceptor implements HandlerInterceptor {
    private final SiteModuleAccessService service;
    public SiteModuleAccessInterceptor(SiteModuleAccessService service) { this.service = service; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) return true;
        if ("OPTIONS".equals(request.getMethod())) return true;
        String controller = method.getBeanType().getSimpleName();
        if (controller.equals("SiteModuleController") || controller.equals("SiteProfileController"))
            response.setHeader("Cache-Control", "private, no-store");
        // Administrative methods retain their existing, separate authority checks.
        if (AnnotatedElementUtils.hasAnnotation(method.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), PreAuthorize.class)) return true;
        String key = moduleFor(method.getBeanType().getSimpleName(), request.getParameter("type"));
        if (key != null) {
            response.setHeader("Cache-Control", "private, no-store");
            service.require(key);
        }
        if (method.getBeanType().getSimpleName().equals("WebsiteInfoController"))
            response.setHeader("Cache-Control", "private, no-store");
        return true;
    }

    public static String moduleFor(String controller, String contentType) {
        boolean articleType = false;
        try { articleType = contentType != null && Integer.parseInt(contentType.trim()) == 1; }
        catch (NumberFormatException ignored) { /* Invalid types are rejected by controller validation. */ }
        return switch (controller) {
            case "ArticleController", "CategoryController", "TagController" -> "blog";
            case "WorkExperienceController", "ExperienceProjectController" -> "experience";
            case "WebsiteShareController" -> "shares";
            case "PhotoController" -> "photos";
            case "BannersController" -> "home";
            case "CommentController", "LikeController", "FavoriteController" -> articleType ? "blog" : null;
            default -> null;
        };
    }
}
