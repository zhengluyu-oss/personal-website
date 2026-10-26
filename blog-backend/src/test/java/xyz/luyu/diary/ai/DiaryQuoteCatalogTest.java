package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiaryQuoteCatalogTest {
    private static final UUID TASK=UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final String BODY="早上散步十五分钟。下午记录了让我满意的进展。晚上还有一件事需要核对。";
    private DiaryQuoteCatalog catalog(){return DiaryQuoteCatalog.build(TASK,List.of(new DiaryQuoteCatalog.SourceText("S1","D",1,BODY)),3000);}
    private String answer(String id,String evidence){return "{\"answer\":{\"text\":\"记录散步\",\"evidenceIds\":[\""+evidence+"\"],\"quotes\":[{\"quoteId\":\""+id+"\"}]},\"summary\":null,\"observations\":[],\"hypotheses\":[],\"suggestions\":[],\"limitations\":[\"仅依据记录\"],\"insufficientEvidence\":false}";}
    @Test void serverHydratesExactQuoteWithoutTrustingProviderText(){
        var catalog=catalog();assertFalse(catalog.items().isEmpty());
        String raw=answer(catalog.items().get(0).quoteId(),"S1");
        String hydrated=catalog.hydrate(raw);
        JSONObject quote=JSON.parseObject(hydrated).getJSONObject("answer").getJSONArray("quotes").getJSONObject(0);
        assertEquals(catalog.items().get(0).text(),quote.getString("text"));assertTrue(BODY.contains(quote.getString("text")));
        assertDoesNotThrow(()->DiaryValidation.check(hydrated,List.of(new DiaryValidation.Evidence("S1",BODY,"D")),"QUESTION"));
        assertDoesNotThrow(()->DiaryAiEvidence.validate(hydrated,List.of(new DiaryAiModels.Source("S1",TASK,1,"题目",null,null,null,0,BODY.length(),BODY,"hash")),DiaryAiModels.Kind.QUESTION));
    }
    @Test void unknownForeignAndWrongSourceIdsFailWithoutRepair(){
        var catalog=catalog();String valid=catalog.items().get(0).quoteId();
        assertEquals(DiaryValidation.Reason.QUOTE_ID_UNKNOWN,assertThrows(DiaryValidation.Issue.class,()->catalog.hydrate(answer(valid+"x","S1"))).reason());
        assertEquals(DiaryValidation.Reason.QUOTE_ID_FOREIGN,assertThrows(DiaryValidation.Issue.class,()->catalog.hydrate(answer("Qother-task-1","S1"))).reason());
        assertEquals(DiaryValidation.Reason.QUOTE_ID_SOURCE,assertThrows(DiaryValidation.Issue.class,()->catalog.hydrate(answer(valid,"S2"))).reason());
        assertEquals(DiaryValidation.Reason.QUOTE_ID_UNKNOWN,assertThrows(DiaryValidation.Issue.class,()->catalog.hydrate(answer(valid,"S1").replace("quoteId","text"))).reason());
    }
    @Test void catalogStaysBoundedAndSpansLongSources(){
        String longText="甲段记载了计划。".repeat(100)+"末尾记载了结果。";
        var catalog=DiaryQuoteCatalog.build(TASK,List.of(new DiaryQuoteCatalog.SourceText("S1","D",1,longText)),2500);
        assertTrue(DiaryAiModels.points(JSON.parseObject(catalog.attach("{}")).getJSONArray("quoteCatalog").toJSONString())<=2500);
        assertTrue(catalog.items().stream().allMatch(i->longText.contains(i.text())));
        assertTrue(catalog.items().stream().anyMatch(i->i.text().contains("末尾")));
        assertTrue(DiaryQuoteCatalog.build(TASK,List.of(new DiaryQuoteCatalog.SourceText("S1","D",1,longText)),80).items().isEmpty());
    }
    @Test void periodRequiresQuoteKindForExecution(){
        var catalog=DiaryQuoteCatalog.build(TASK,List.of(new DiaryQuoteCatalog.SourceText("G1","G",1,"希望保持运动。")),1000);
        String raw="{\"coverage\":\"测试\",\"currentGoals\":[],\"observations\":[],\"feedback\":[{\"text\":\"已执行\",\"type\":\"EXECUTION\",\"evidenceIds\":[\"G1\"],\"quotes\":[{\"quoteId\":\""+catalog.items().get(0).quoteId()+"\"}]}],\"hypotheses\":[],\"suggestions\":[],\"limitations\":[\"有限\"]}";
        assertEquals(DiaryValidation.Reason.QUOTE_ID_TYPE,assertThrows(DiaryValidation.Issue.class,()->catalog.hydrate(raw)).reason());
    }
    @Test void selectedReviewAndPeriodKeepTheirDistinctEvidenceTypes(){
        var diary=catalog();String quoteId=diary.items().get(0).quoteId();
        JSONObject review=JSON.parseObject(answer(quoteId,"S1"));review.put("summary",review.remove("answer"));review.put("answer",null);
        String hydrated=diary.hydrate(JSON.toJSONString(review,com.alibaba.fastjson2.JSONWriter.Feature.WriteNulls));
        assertDoesNotThrow(()->DiaryValidation.check(hydrated,List.of(new DiaryValidation.Evidence("S1",BODY,"D")),"REVIEW"));
        var period=DiaryQuoteCatalog.build(TASK,List.of(new DiaryQuoteCatalog.SourceText("F1","F",1,"今天确认完成了散步。")),1000);
        String periodRaw="{\"coverage\":\"一条反馈\",\"currentGoals\":[],\"observations\":[],\"feedback\":[{\"text\":\"本人确认散步\",\"type\":\"EXECUTION\",\"evidenceIds\":[\"F1\"],\"quotes\":[{\"quoteId\":\""+period.items().get(0).quoteId()+"\"}]}],\"hypotheses\":[],\"suggestions\":[],\"limitations\":[\"仅据一条反馈\"]}";
        String periodHydrated=period.hydrate(periodRaw);
        assertDoesNotThrow(()->DiaryValidation.check(periodHydrated,List.of(new DiaryValidation.Evidence("F1","今天确认完成了散步。","F")),"PERIOD"));
    }
    @Test void attachedCatalogNeverTruncatesCompleteSourceText(){
        var catalog=catalog();String source=JSON.toJSONString(Map.of("sources",List.of(Map.of("sourceId","S1","text",BODY))));
        JSONObject attached=JSON.parseObject(catalog.attach(source));
        assertEquals(BODY,attached.getJSONArray("sources").getJSONObject(0).getString("text"));
        assertFalse(attached.getJSONArray("quoteCatalog").isEmpty());
    }
    @Test void insufficientEvidenceDoesNotInventAQuote(){
        String raw="{\"answer\":null,\"summary\":null,\"observations\":[],\"hypotheses\":[],\"suggestions\":[],\"limitations\":[\"当前记录没有所问经历\"],\"insufficientEvidence\":true}";
        String hydrated=catalog().hydrate(raw);
        assertDoesNotThrow(()->DiaryValidation.check(hydrated,List.of(new DiaryValidation.Evidence("S1",BODY,"D")),"QUESTION"));
        var result=DiaryAiEvidence.validate(hydrated,List.of(),DiaryAiModels.Kind.QUESTION);
        assertTrue(result.insufficientEvidence());assertTrue(result.answer().quotes().isEmpty());
    }
    @Test void malformedAndRewrittenQuotesNeverBecomeTrustedEvidence(){
        var catalog=catalog();String valid=catalog.items().get(0).quoteId();
        assertEquals(DiaryValidation.Reason.JSON_PARSE,assertThrows(DiaryValidation.Issue.class,()->catalog.hydrate("{invalid")).reason());
        String rewritten=answer(valid,"S1").replace("\"quoteId\":\""+valid+"\"","\"quoteId\":\""+valid+"\",\"text\":\"我编造的内容\"");
        assertEquals(DiaryValidation.Reason.QUOTE_ID_UNKNOWN,assertThrows(DiaryValidation.Issue.class,()->catalog.hydrate(rewritten)).reason());
        assertThrows(DiaryValidation.Issue.class,()->DiaryValidation.check(catalog.hydrate("{}"),List.of(new DiaryValidation.Evidence("S1",BODY,"D")),"QUESTION"));
    }
}
