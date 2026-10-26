package xyz.luyu.handler;

import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import xyz.luyu.domain.dto.UserCommentDTO;
import xyz.luyu.service.SiteModuleAccessService;

@RestControllerAdvice
public class SiteModuleRequestAdvice extends RequestBodyAdviceAdapter {
    private final SiteModuleAccessService service;
    public SiteModuleRequestAdvice(SiteModuleAccessService service) { this.service = service; }
    @Override
    public boolean supports(MethodParameter method, Type type, Class<? extends HttpMessageConverter<?>> converter) {
        return method.getParameterType().equals(UserCommentDTO.class);
    }
    @Override
    public Object afterBodyRead(Object body, HttpInputMessage input, MethodParameter method, Type type,
                               Class<? extends HttpMessageConverter<?>> converter) {
        if (body instanceof UserCommentDTO comment && Integer.valueOf(1).equals(comment.getType())) service.require("blog");
        return body;
    }
}
