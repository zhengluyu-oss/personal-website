package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.kuailemao.diary.ai.DiaryValidation.Reason.*;

class DiaryValidationTest {
    final List<DiaryValidation.Evidence> sources=List.of(new DiaryValidation.Evidence("S1","今天散步十五分钟。","D"));
    JSONObject valid(){return JSON.parseObject("{\"answer\":{\"text\":\"记录散步\",\"evidenceIds\":[\"S1\"],\"quotes\":[{\"sourceId\":\"S1\",\"text\":\"散步十五分钟\"}]},\"summary\":null,\"observations\":[],\"hypotheses\":[],\"suggestions\":[],\"limitations\":[\"仅依据记录\"],\"insufficientEvidence\":false}");}
    void fails(String raw,DiaryValidation.Reason reason,String path){var e=assertThrows(DiaryValidation.Issue.class,()->DiaryValidation.check(raw,sources,"QUESTION"));assertEquals(reason,e.reason());assertEquals(path,e.path());assertFalse(e.toString().contains("PRIVATE_PROBE"));}
    @Test void validAnswerAndSelectedReviewRemainAccepted(){var a=valid();assertDoesNotThrow(()->DiaryValidation.check(a.toJSONString(JSONWriter.Feature.WriteNulls),sources,"QUESTION"));a.put("summary",a.get("answer"));a.put("answer",null);assertDoesNotThrow(()->DiaryValidation.check(a.toJSONString(JSONWriter.Feature.WriteNulls),sources,"REVIEW"));}
    @Test void parsingFieldsAndTypesAreDistinct(){fails("PRIVATE_PROBE",JSON_PARSE,"$");var a=valid();a.put("PRIVATE_PROBE",true);fails(a.toJSONString(JSONWriter.Feature.WriteNulls),ROOT_FIELDS,"$");a=valid();a.put("insufficientEvidence","false");fails(a.toJSONString(JSONWriter.Feature.WriteNulls),TYPE,"$.insufficientEvidence");a=valid();a.getJSONObject("answer").put("text","");fails(a.toJSONString(JSONWriter.Feature.WriteNulls),REQUIRED,"$.answer.text");}
    @Test void limitsAndUnknownSourcesAreDistinct(){var a=valid();a.getJSONObject("answer").put("text","x".repeat(2001));fails(a.toJSONString(JSONWriter.Feature.WriteNulls),TEXT_LIMIT,"$.answer.text");a=valid();a.put("hypotheses",Collections.nCopies(9,Map.of()));fails(a.toJSONString(JSONWriter.Feature.WriteNulls),ARRAY_LIMIT,"$.hypotheses");a=valid();a.getJSONObject("answer").put("evidenceIds",List.of("PRIVATE_PROBE"));fails(a.toJSONString(JSONWriter.Feature.WriteNulls),SOURCE_UNKNOWN,"$.answer.evidenceIds[0]");}
    @Test void quoteMismatchHasExactSafePath(){var a=valid();a.getJSONObject("answer").getJSONArray("quotes").getJSONObject(0).put("text","PRIVATE_PROBE");fails(a.toJSONString(JSONWriter.Feature.WriteNulls),QUOTE_MISMATCH,"$.answer.quotes[0].text");}
    @Test void periodRejectsPlansAsExecution(){var p=JSON.parseObject("{\"coverage\":\"记录\",\"currentGoals\":[],\"observations\":[],\"feedback\":[{\"type\":\"EXECUTION\",\"text\":\"执行了计划\",\"evidenceIds\":[\"S1\"],\"quotes\":[{\"sourceId\":\"S1\",\"text\":\"散步\"}]}],\"hypotheses\":[],\"suggestions\":[],\"limitations\":[\"有限资料\"]}");var e=assertThrows(DiaryValidation.Issue.class,()->DiaryValidation.check(p.toJSONString(JSONWriter.Feature.WriteNulls),sources,"PERIOD"));assertEquals(EVIDENCE_TYPE,e.reason());assertEquals("$.feedback[0].evidenceIds",e.path());p.put("feedback",List.of());assertDoesNotThrow(()->DiaryValidation.check(p.toJSONString(JSONWriter.Feature.WriteNulls),sources,"PERIOD"));}
}
