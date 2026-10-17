package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import org.yaml.snakeyaml.Yaml;
import xyz.kuailemao.diary.coaching.CoachingEvidence;
import xyz.kuailemao.diary.coaching.CoachingModels;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** Explicit six-generation, seven-vector synthetic calibration. Never connects to an application database. */
public final class DiaryAiResponseCalibration {
    public static void main(String[] args) {
        try {run(args);} catch(Exception failure) {System.err.println("SYNTHETIC_CALIBRATION_SETUP_FAILED");System.exit(1);}
    }
    @SuppressWarnings("unchecked") static void run(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException();Path receipt=Path.of(args[1]);
        if(Files.exists(receipt) || !Files.isDirectory(receipt.getParent()))throw new IllegalArgumentException();
        Map<String,Object> config=new Yaml().load(Files.readString(Path.of(args[0])));
        Map<String,Object> ai=(Map<String,Object>)((Map<String,Object>)config.get("diary")).get("ai");
        if(!"deepseek-flash".equals(ai.get("generation-model")) || !"text-embedding-v4".equals(ai.get("embedding-model")))throw new IllegalArgumentException();
        DiaryAiProperties p=new DiaryAiProperties();p.setGenerationEndpoint((String)ai.get("generation-endpoint"));p.setGenerationKey((String)ai.get("generation-key"));p.setGenerationModel("deepseek-flash");p.setGenerationThinking(false);p.setGenerationJsonOutput(true);
        p.setEmbeddingEndpoint((String)ai.get("embedding-endpoint"));p.setEmbeddingKey((String)ai.get("embedding-key"));p.setEmbeddingModel("text-embedding-v4");p.setDimensions(1024);p.setAllowedHosts(Set.of("api.deepseek.com","dashscope.aliyuncs.com"));
        DiaryAiCloud cloud=new DiaryAiCloud(p);
        List<Source> sources=new ArrayList<>();String[] texts={"【虚构资料】九月三日，我完成了阅读计划，读完一本书，对这件事很满意。","【虚构资料】九月十日，我拖延了整理房间的安排，到晚上仍没开始，对拖延不满意。","【虚构资料】九月二十日，我练习了水彩画，完成一张小画；熬夜刷手机让我第二天疲惫，对这一点不满意。"};
        for(int i=0;i<texts.length;i++)sources.add(new Source("S"+(i+1),UUID.randomUUID(),1,"虚构九月记录",LocalDate.of(2026,9,1),LocalDate.of(2026,9,30),Instant.parse("2026-10-01T00:00:00Z"),0,points(texts[i]),texts[i],DiaryAiText.hash(texts[i])));
        List<String> questions=List.of("这些九月记录中做了什么？哪些满意，哪些不满意？","九月哪些经历让我满意？哪些做法让我不满意？","请根据记录简述九月的阅读、水彩和拖延情况。","记录中说明我九月去火星旅行的具体日期了吗？");
        List<Map<String,Object>> rows=new ArrayList<>();Map<String,Object> report=new LinkedHashMap<>();
        report.put("syntheticOnly",true);report.put("productionDiaryAccess",false);report.put("maxGenerationAttempts",6);report.put("automaticRetries",0);report.put("maxOutputTokens",p.getMaxOutputTokens());report.put("jsonOutput",true);report.put("cases",rows);report.put("status","RUNNING");
        Files.createFile(receipt,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));write(receipt,report);
        List<String> vectorInput=new ArrayList<>(List.of(texts));vectorInput.addAll(questions);
        try {var vectors=cloud.embed(vectorInput);report.put("embeddingVectors",vectors.vectors().size());report.put("embeddingInputTokens",vectors.inputTokens());report.put("embeddingValidated",true);}
        catch(DiaryAiCloud.Failure failure){report.put("embeddingValidated",false);report.put("embeddingError",failure.code());report.put("status","FAILED");write(receipt,report);System.out.println("SYNTHETIC_EMBEDDING_FAILED");return;}
        for(int i=0;i<6;i++) {
            Map<String,Object> row=new LinkedHashMap<>();row.put("case",i+1);row.put("kind",i<4?"QUESTION":i==4?"DIARY_REVIEW":"PERIOD_REVIEW");rows.add(row);write(receipt,report);
            try {
                if(i<5) {
                    Kind kind=i<4?Kind.QUESTION:Kind.REVIEW;
                    List<Source> selected=i==3?List.of():sources;
                    var result=cloud.generate(DiaryAiEvidence.system(kind),JSON.toJSONString(Map.of("question",i<4?questions.get(i):"","scope","虚构九月资料，仅校准生成适配器，不代表真实检索结果","partialIndex",false,"sources",selected)));
                    row.put("inputTokens",result.inputTokens());row.put("outputTokens",result.outputTokens());
                    var validated=DiaryAiEvidence.validate(result.content(),selected,kind);
                    boolean expected=i==3?validated.insufficientEvidence():!validated.insufficientEvidence();
                    String lead=kind==Kind.QUESTION?validated.answer().text():validated.summary()==null?"":validated.summary().text();
                    boolean relevant=i==3 || lead.contains("阅读") || lead.contains("水彩") || lead.contains("拖延");
                    row.put("evidenceValidated",true);row.put("insufficientEvidence",validated.insufficientEvidence());row.put("expectedBehavior",expected && relevant);row.put("passed",expected && relevant);
                } else {
                    List<CoachingModels.Source> period=new ArrayList<>();for(int j=0;j<sources.size();j++){Source s=sources.get(j);period.add(new CoachingModels.Source("D"+(j+1),"D",s.entryId(),1,"body",0,points(s.text()),DiaryAiText.hash(s.text()),s.text()));}
                    String goal="【虚构目标】每周尝试一次水彩画",feedback="【虚构反馈】九月二十日，我实际画了一张水彩小画，完成后感觉放松。";
                    period.add(new CoachingModels.Source("G1","G",UUID.randomUUID(),1,"title",0,points(goal),DiaryAiText.hash(goal),goal));period.add(new CoachingModels.Source("F1","F",UUID.randomUUID(),1,"text",0,points(feedback),DiaryAiText.hash(feedback),feedback));
                    var generated=cloud.generate(CoachingEvidence.system(),JSON.toJSONString(Map.of("window",Map.of("type","MONTH","from","2026-09-01","to","2026-09-30","unfinished",false,"axis","covered"),"sources",period)));
                    row.put("inputTokens",generated.inputTokens());row.put("outputTokens",generated.outputTokens());
                    var validated=CoachingEvidence.validate(generated.content(),period);boolean expected=!validated.observations().isEmpty() && !validated.feedback().isEmpty();
                    row.put("evidenceValidated",true);row.put("expectedBehavior",expected);row.put("passed",expected);
                }
            } catch(DiaryAiCloud.Failure failure){row.put("passed",false);row.put("error",failure.code());if(failure.diagnostic()!=null)row.put("diagnostic",failure.diagnostic());}
            catch(RuntimeException failure){row.put("passed",false);row.put("error","LOCAL_RESULT_VALIDATION_FAILED");}
            write(receipt,report);System.out.println("SYNTHETIC_CASE_"+(i+1)+"_COMPLETE");
        }
        long passed=rows.stream().filter(row->Boolean.TRUE.equals(row.get("passed"))).count();report.put("passed",passed);report.put("attempts",rows.size());report.put("status",passed==6?"PASSED":"NEEDS_REVIEW");write(receipt,report);
        System.out.println("SYNTHETIC_CALIBRATION passed="+passed+"/6; no retries; no production diary accessed");
    }
    static void write(Path file,Map<String,Object> data)throws Exception {Files.writeString(file,JSON.toJSONString(data));}
}
