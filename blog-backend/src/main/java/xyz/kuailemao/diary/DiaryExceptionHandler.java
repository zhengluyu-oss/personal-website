package xyz.kuailemao.diary;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import xyz.kuailemao.domain.response.ResponseResult;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={DiaryController.class,DiaryReadController.class})
public class DiaryExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<Void>> handle(Exception exception) {
        int status=503;String message="日记暂时不可用，请保留未确认内容";
        if(exception instanceof DiaryFailure failure) { status=failure.status();message=failure.getMessage(); }
        else if(exception instanceof IllegalArgumentException || exception instanceof java.time.DateTimeException
                || exception instanceof HttpMessageNotReadableException || exception instanceof MethodArgumentTypeMismatchException) {
            status=400;message="日记参数格式不正确，请检查日期和请求格式";
        }
        // 不记录异常堆栈：驱动/校验异常可能包含完整正文或参数。
        return ResponseEntity.status(status).header("Cache-Control","private, no-store")
                .header("X-Robots-Tag","noindex, nofollow, noarchive").body(ResponseResult.failure(status,message));
    }
}
