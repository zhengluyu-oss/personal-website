package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;
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
    }
}
