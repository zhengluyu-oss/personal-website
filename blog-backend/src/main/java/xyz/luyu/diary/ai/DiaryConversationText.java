package xyz.luyu.diary.ai;

import com.alibaba.fastjson2.JSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static xyz.luyu.diary.ai.DiaryAiModels.*;

/** Prior answers are conversational context only, never citation material. */
final class DiaryConversationText {
    private DiaryConversationText() {}

    static String summary(String raw) {
        if(raw==null || raw.isBlank()) return "";
        try {
            Result result=JSON.parseObject(raw,Result.class);
            StringBuilder value=new StringBuilder();
            if(result.answer()!=null) append(value,"上轮回答："+limit(result.answer().text(),800));
            if(result.summary()!=null) append(value,"上轮概览："+limit(result.summary().text(),800));
            if(result.observations()!=null) result.observations().stream().limit(2).forEach(item->append(value,"上轮观察："+limit(item.text(),250)));
            if(result.hypotheses()!=null) result.hypotheses().stream().limit(2).forEach(item->append(value,"上轮待核实解释："+limit(item.text(),250)));
            if(result.suggestions()!=null) result.suggestions().stream().limit(3).forEach(item->append(value,"上轮可选建议："+limit(item.action(),200)));
            return limit(value.toString(),2400);
        } catch(RuntimeException invalid) { return ""; }
    }

    static String history(List<DiaryConversationStore.History> turns,int maximum) {
        if(turns==null || turns.isEmpty() || maximum<100) return "";
        var rows=new ArrayList<Map<String,String>>();
        for(var turn:turns) {
            var row=new LinkedHashMap<String,String>();
            row.put("question",Objects.toString(turn.question(),"首轮复盘"));
            row.put("previousAssistantText",limit(turn.answer(),2400));
            rows.add(row);
        }
        String json=JSON.toJSONString(rows);
        // Preserve the user's exact goals/corrections before spending budget on old AI text.
        for(var row:rows) {
            while(points(json)>maximum && !row.get("previousAssistantText").isEmpty()) {
                row.put("previousAssistantText",limit(row.get("previousAssistantText"),Math.max(0,points(row.get("previousAssistantText"))-200)));
                json=JSON.toJSONString(rows);
            }
        }
        while(points(json)>maximum && rows.size()>1) {
            // Keep the initial goal and newest correction as long as both fit.
            rows.remove(rows.size()>2?1:0);json=JSON.toJSONString(rows);
        }
        // Omit a history row rather than changing the meaning of a truncated user correction.
        return points(json)<=maximum?json:"";
    }

    static String retrievalQuestion(String current,List<DiaryConversationStore.History> history) {
        if(history==null || history.isEmpty()) return current;
        var last=history.get(history.size()-1);
        int contextBudget=Math.max(0,1000-points(current)-2);
        if(contextBudget==0) return current;
        String context=limit(Objects.toString(last.question(),"首轮复盘")+"\n"+limit(last.answer(),350),contextBudget);
        return context+"\n"+current;
    }

    private static void append(StringBuilder out,String value) {
        if(value!=null && !value.isBlank()) { if(!out.isEmpty()) out.append("\n");out.append(value); }
    }
    private static String limit(String value,int maximum) {
        if(value==null) return "";
        return points(value)<=maximum?value:slice(value,0,maximum);
    }
}
