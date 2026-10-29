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

    @Test void historyKeepsTheInitialGoalAndExactRecentCorrectionBeforeOldAiText() {
        String initial="我的目标是减少加班，不是提高每天任务量。";
        String correction="按我补充：周三的工作安排不能调整，请不要再建议我把它取消。";
        var turns=new java.util.ArrayList<DiaryConversationStore.History>();
        turns.add(new DiaryConversationStore.History(initial,"旧建议".repeat(1000)));
        for(int i=1;i<8;i++)turns.add(new DiaryConversationStore.History("前文问题"+i,"此前AI解释".repeat(500)));
        turns.add(new DiaryConversationStore.History(correction,"本轮前的解释".repeat(500)));
        String raw=DiaryConversationText.history(turns,2400);
        var rows=JSON.parseArray(raw);
        assertTrue(points(raw)<=2400);
        assertEquals(initial,rows.getJSONObject(0).getString("question"));
        assertEquals(correction,rows.getJSONObject(rows.size()-1).getString("question"));
        assertEquals("",rows.getJSONObject(0).getString("previousAssistantText"));
    }

    @Test void summariesRetainUncertaintyAndSuggestionsForFollowupReferences() {
        String raw=JSON.toJSONString(java.util.Map.of(
            "answer",java.util.Map.of("text","上次的长回答".repeat(1000)),
            "hypotheses",List.of(java.util.Map.of("text","可能是任务边界不清，需要核实")),
            "suggestions",List.of(java.util.Map.of("action","先确认一个明确的完成标准"))));
        String summary=DiaryConversationText.summary(raw);
        assertTrue(summary.contains("上轮待核实解释：可能是任务边界不清，需要核实"));
        assertTrue(summary.contains("上轮可选建议：先确认一个明确的完成标准"));
        assertTrue(points(summary)<=2400);
    }
}
