package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static xyz.luyu.diary.ai.DiaryAiModels.*;
import static org.junit.jupiter.api.Assertions.*;

class DiaryAiEvidenceTest {
    private final Source source=new Source("S1",UUID.randomUUID(),1,"完全虚构",LocalDate.of(2024,2,28),LocalDate.of(2024,3,2),Instant.EPOCH,0,8,"步行十五分钟🌊",DiaryAiText.hash("步行十五分钟🌊"));
    private Statement statement(String id,String quote){return new Statement("记录中进行了步行。",List.of(id),List.of(new Quote(id,quote)));}
    private Result answer(Statement answer){return new Result(answer,null,List.of(),List.of(),List.of(),List.of("跨日记录不代表逐日发生"),false);}
    @Test void authenticSourcesAndExactQuotesAreAcceptedButUnknownOrWrongQuotesAreRejected() {
        assertEquals("记录中进行了步行。",DiaryAiEvidence.validate(JSON.toJSONString(answer(statement("S1","步行十五分钟"))),List.of(source),Kind.QUESTION).answer().text());
        for(Statement s:List.of(statement("S2","步行十五分钟"),statement("S1","跑步二十公里")))
            assertThrows(DiaryAiCloud.Failure.class,()->DiaryAiEvidence.validate(JSON.toJSONString(answer(s)),List.of(source),Kind.QUESTION));
    }
    @Test void insufficientEvidenceNeverPublishesInventedFactsAndMalformedOutputFails() {
        var raw=new Result(new Statement("编造的事实",List.of(),List.of()),null,List.of(),List.of(),List.of(),List.of("不足"),true);
        assertTrue(DiaryAiEvidence.validate(JSON.toJSONString(raw),List.of(),Kind.QUESTION).insufficientEvidence());
        assertFalse(DiaryAiEvidence.validate(JSON.toJSONString(raw),List.of(),Kind.QUESTION).answer().text().contains("编造"));
        for(String bad:List.of("not-json","{}","{\"tool_calls\":[1]}"))assertThrows(DiaryAiCloud.Failure.class,()->DiaryAiEvidence.validate(bad,List.of(source),Kind.QUESTION));
    }
    @Test void validCitationDoesNotProveInterpretationCorrect() {
        var unsupportedInference=new Statement("模型推断需要本人判断。",List.of("S1"),List.of(new Quote("S1","步行")));
        assertNotNull(DiaryAiEvidence.validate(JSON.toJSONString(answer(unsupportedInference)),List.of(source),Kind.QUESTION));
        assertTrue(DiaryAiEvidence.system(Kind.REVIEW).contains("不把未记录的事视为未发生"));
        assertTrue(DiaryAiEvidence.system(Kind.REVIEW).contains("\"summary\":{\"text\""));
    }
    @Test void questionCanIncludeObservationsAlternativeExplanationsAndNewGeneralMethods() {
        Statement observed=statement("S1","步行十五分钟");
        var hypotheses=java.util.stream.IntStream.rangeClosed(1,9)
                .mapToObj(i->new Statement("待核实角度"+i+"：可能与环境改变有关，也可能只是当日其他安排所致，可以比较下一次前后的感受。",
                        List.of("S1"),List.of(new Quote("S1","步行十五分钟")))).toList();
        var methods=java.util.stream.IntStream.rangeClosed(1,9)
                .mapToObj(i->new Suggestion("可选方法"+i+"：先记录一种你愿意尝试的环境调整。","通用方法，不代表你曾经这样做过，也不预设实际效果。",
                        List.<String>of(),"先尝试一次。","下次尝试后由本人比较是否适用。",true)).toList();
        var candidate=new Result(observed,null,Collections.nCopies(13,observed),hypotheses,methods,List.of("引用仅确认步行记录，不证明上述解释。"),false);
        String raw=JSON.toJSONString(candidate,com.alibaba.fastjson2.JSONWriter.Feature.WriteNulls);

        assertDoesNotThrow(()->DiaryValidation.check(raw,List.of(new DiaryValidation.Evidence("S1",source.text(),"D")),"QUESTION"));
        assertEquals(candidate,DiaryAiEvidence.validate(raw,List.of(source),Kind.QUESTION));
    }
    @Test void longAnswerAndSuggestionFieldsAreAcceptedWithinOverallSafetyBudget() {
        String extended="比较目标、环境条件与可尝试的方法。".repeat(4000);
        var lead=new Statement(extended,List.of("S1"),List.of(new Quote("S1","步行十五分钟")));
        var suggestion=new Suggestion(extended,extended,List.of("S1"),extended,extended,false);
        var candidate=new Result(lead,null,List.of(),List.of(),List.of(suggestion),List.of(extended),false);
        String raw=JSON.toJSONString(candidate,com.alibaba.fastjson2.JSONWriter.Feature.WriteNulls);

        assertTrue(points(raw)>32000,"Fixture must cross the old result limit");
        assertDoesNotThrow(()->DiaryValidation.check(raw,List.of(new DiaryValidation.Evidence("S1",source.text(),"D")),"QUESTION"));
        assertEquals(candidate,DiaryAiEvidence.validate(raw,List.of(source),Kind.QUESTION));
    }
    @Test void longOutputStillRequiresExactQuotesAndBoundedTotalSize() {
        String tooLarge="x".repeat(DiaryValidation.MAX_RESULT_CHARACTERS+1);
        assertThrows(DiaryAiCloud.Failure.class,()->DiaryAiEvidence.validate(tooLarge,List.of(source),Kind.QUESTION));
        String body="散".repeat(251);
        var largeSource=new Source("S1",UUID.randomUUID(),1,"引用边界",null,null,Instant.EPOCH,0,points(body),body,DiaryAiText.hash(body));
        assertThrows(DiaryAiCloud.Failure.class,()->DiaryAiEvidence.validate(JSON.toJSONString(answer(statement("S1",body))),List.of(largeSource),Kind.QUESTION));
    }
}
