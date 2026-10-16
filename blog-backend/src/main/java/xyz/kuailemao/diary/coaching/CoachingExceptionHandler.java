package xyz.kuailemao.diary.coaching;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.diary.DiaryExceptionHandler;
import xyz.kuailemao.domain.response.ResponseResult;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=CoachingController.class)
public class CoachingExceptionHandler {
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResponseResult<Void>> handle(Exception failure) { return new DiaryExceptionHandler().handle(failure); }
}
