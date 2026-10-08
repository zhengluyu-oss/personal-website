package xyz.kuailemao.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import xyz.kuailemao.controller.OauthController;
import xyz.kuailemao.controller.UserController;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.enums.RespEnum;

/** An invalid action proof is not a request to replace/logout the existing session. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = {UserController.class, OauthController.class})
public class SensitiveActionExceptionHandler {
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseResult<Void> invalidProof() {
        return ResponseResult.failure(RespEnum.VERIFY_CODE_ERROR.getCode(),
                "安全验证失败或已过期，请检查验证码或重新发起；原邮箱不可用时请联系管理员恢复");
    }
}
