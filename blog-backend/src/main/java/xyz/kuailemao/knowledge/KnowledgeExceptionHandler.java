package xyz.kuailemao.knowledge;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.domain.response.ResponseResult;

@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages="xyz.kuailemao.knowledge")
public class KnowledgeExceptionHandler {
    @ExceptionHandler(Exception.class) public ResponseEntity<ResponseResult<Void>> handle(Exception error){int status=error instanceof KnowledgeFailure f?f.status():error instanceof org.springframework.http.converter.HttpMessageNotReadableException||error instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException?400:503;String message=error instanceof KnowledgeFailure?error.getMessage():status==400?"请求参数格式无效":"知识库暂不可用，未确认内容请保留";return ResponseEntity.status(status).header("Cache-Control","private, no-store").header("X-Robots-Tag","noindex, nofollow, noarchive").body(ResponseResult.failure(status,message));}
}
