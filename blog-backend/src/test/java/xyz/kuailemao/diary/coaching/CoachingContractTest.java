package xyz.kuailemao.diary.coaching;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import xyz.kuailemao.diary.DiaryFailure;
import java.time.LocalDate;
import java.util.*;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;
import static org.junit.jupiter.api.Assertions.*;

class CoachingContractTest {
    final UUID goal=UUID.fromString("00000000-0000-4000-8000-000000000301");
    @Test void weeksMonthsLeapDaysAndUnfinishedWindowsUseShanghaiCalendar() {
        var week=new Period("WEEK",LocalDate.of(2024,3,1),null,null,null,List.of(goal),false).normalize(LocalDate.of(2024,3,1));
        assertEquals(LocalDate.of(2024,2,26),week.from());assertEquals(LocalDate.of(2024,3,3),week.to());assertTrue(week.unfinished());assertEquals("covered",week.axis());
        var month=new Period("MONTH",LocalDate.of(2024,2,15),null,null,"created",List.of(goal),false).normalize(LocalDate.of(2024,3,1));
        assertEquals(LocalDate.of(2024,2,29),month.to());assertFalse(month.unfinished());
        assertThrows(DiaryFailure.class,()->new Period("MONTH",LocalDate.of(2030,2,1),null,null,null,List.of(goal),false).normalize(LocalDate.of(2024,3,1)));
        assertThrows(DiaryFailure.class,()->new Period("CUSTOM",null,LocalDate.of(2024,3,2),LocalDate.of(2024,3,1),null,List.of(goal),false).normalize(LocalDate.of(2024,3,1)));
    }
    @Test void manualFieldsAndUnicodeLimitsDoNotInventDatesOrFacts() {
        var raw=JSONObject.of("actionId",goal.toString(),"execution","TRIED","effect","UNSURE","note","虚构🙂");
        var normalized=normalize(Kind.FEEDBACK,raw,LocalDate.of(2026,10,16));assertNull(normalized.get("occurredOn"));
        raw.put("occurredOn","2026-10-17");assertThrows(DiaryFailure.class,()->normalize(Kind.FEEDBACK,raw,LocalDate.of(2026,10,16)));
        raw.put("occurredOn","2026-09-30");assertEquals("2026-09-30",normalize(Kind.FEEDBACK,raw,LocalDate.of(2026,10,16)).getString("occurredOn"));
        raw.put("owner",7);assertThrows(DiaryFailure.class,()->normalize(Kind.FEEDBACK,raw,LocalDate.of(2026,10,16)));
        var title=JSONObject.of("title","🙂".repeat(120),"state","ACTIVE");assertEquals(120,normalize(Kind.GOAL,title,LocalDate.now()).getString("title").codePointCount(0,240));
        title.put("title","🙂".repeat(121));assertThrows(DiaryFailure.class,()->normalize(Kind.GOAL,title,LocalDate.now()));
    }
    @Test void feedbackEventsAndEvaluableDenominatorsRemainDistinct() {
        List<Row> rows=new ArrayList<>();
        for(String effect:List.of("HELPFUL","NOT_HELPFUL","UNSURE","NOT_EVALUATED")) rows.add(new Row(Kind.FEEDBACK,UUID.randomUUID(),goal,1,JSONObject.of("execution","TRIED","effect",effect),null,null,null));
        var stats=CoachingPeriods.statistics(rows,3);assertEquals(4,stats.feedback());assertEquals(4,stats.tried());assertEquals(2,stats.evaluable());assertEquals(50,stats.helpfulPercent());assertEquals(3,stats.undated());
        var empty=CoachingPeriods.statistics(List.of(),2);assertNull(empty.helpfulPercent());assertEquals(0,empty.evaluable());
        assertEquals(hash(JSONObject.of("a",1,"b",2)),hash(JSONObject.of("b",2,"a",1)));
    }
}
