package xyz.kuailemao.handler;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import xyz.kuailemao.domain.response.ResponseResult;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SiteModuleExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResponseResult<Void>> status(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).header("Cache-Control", "private, no-store")
                .body(ResponseResult.failure(exception.getStatusCode().value(), exception.getReason()));
    }
}
