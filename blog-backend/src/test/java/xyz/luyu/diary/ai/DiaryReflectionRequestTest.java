package xyz.luyu.diary.ai;

import org.junit.jupiter.api.Test;
import xyz.luyu.diary.DiaryFailure;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.luyu.diary.ai.DiaryAiModels.*;

class DiaryReflectionRequestTest {
    private final Scope scope=new Scope(List.of(UUID.randomUUID()),null,null,null);

    @Test void completeReviewAcceptsAnOptionalQuestionAndKeepsPreviewRequired() {
        assertDoesNotThrow(()->new Request(UUID.randomUUID(),null,scope,"a".repeat(64),false).validate(Kind.REVIEW));
        assertDoesNotThrow(()->new Request(UUID.randomUUID(),"请从我未考虑的角度分析这段经历",scope,"a".repeat(64),false).validate(Kind.REVIEW));
        assertThrows(DiaryFailure.class,()->new Request(UUID.randomUUID(),"请分析",scope,null,false).validate(Kind.REVIEW));
        assertThrows(DiaryFailure.class,()->new Request(UUID.randomUUID(),"长".repeat(1001),scope,"a".repeat(64),false).validate(Kind.REVIEW));
    }

    @Test void questionStillRequiresTextAndCannotReuseTheReviewDigest() {
        assertThrows(DiaryFailure.class,()->new Request(UUID.randomUUID(),null,scope,null,false).validate(Kind.QUESTION));
        assertThrows(DiaryFailure.class,()->new Request(UUID.randomUUID(),"问题",scope,"a".repeat(64),false).validate(Kind.QUESTION));
    }
}
