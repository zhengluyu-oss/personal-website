package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.luyu.diary.ai.DiaryAiModels.points;

class DiaryConversationTextTest {
    @Test void boundedHistoryRemainsValidJsonAndPrioritizesCurrentQuestion() {
        var turns=List.of(
            new DiaryConversationStore.History("First", "A".repeat(1800)),
            new DiaryConversationStore.History("Second", "B".repeat(1800)));
        String history=DiaryConversationText.history(turns,600);
        assertTrue(points(history)<=600);
        assertNotNull(JSON.parseArray(history));
        assertTrue(history.contains("Second"));
        String current="C".repeat(900);
        String retrieval=DiaryConversationText.retrievalQuestion(current,turns);
        assertTrue(points(retrieval)<=1000);
        assertTrue(retrieval.endsWith(current));
    }
}
