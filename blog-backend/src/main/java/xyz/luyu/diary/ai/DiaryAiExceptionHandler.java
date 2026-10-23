package xyz.luyu.diary.ai;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.diary.DiaryExceptionHandler;
import xyz.luyu.domain.response.ResponseResult;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=DiaryAiController.class)
public class DiaryAiExceptionHandler {
    @ExceptionHandler(Exception.class) public ResponseEntity<ResponseResult<Void>> handle(Exception e){return new DiaryExceptionHandler().handle(e);}
}
