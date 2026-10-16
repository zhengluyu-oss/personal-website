package xyz.kuailemao.diary.ai;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.diary.DiaryExceptionHandler;
import xyz.kuailemao.domain.response.ResponseResult;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes=DiaryAiController.class)
public class DiaryAiExceptionHandler {
    @ExceptionHandler(Exception.class) public ResponseEntity<ResponseResult<Void>> handle(Exception e){return new DiaryExceptionHandler().handle(e);}
}
