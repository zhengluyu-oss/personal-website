package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** Prior answers are conversational context only, never citation material. */
final class DiaryConversationText {
    private DiaryConversationText() {}

    static String summary(String raw) {
        if(raw==null || raw.isBlank()) return "";
        try {
            Result result=JSON.parseObject(raw,Result.class);
            StringBuilder value=new StringBuilder();
            if(result.answer()!=null) append(value,result.answer().text());
            if(result.summary()!=null) append(value,result.summary().text());
            if(result.observations()!=null) result.observations().stream().limit(2).forEach(item->append(value,item.text()));
            if(result.suggestions()!=null) result.suggestions().stream().limit(2).forEach(item->append(value,item.action()));
            return limit(value.toString(),1400);
        } catch(RuntimeException invalid) { return ""; }
    }

    static String history(List<DiaryConversationStore.History> turns,int maximum) {
        if(turns==null || turns.isEmpty() || maximum<100) return "";
        var rows=new ArrayList<Map<String,String>>();
        for(var turn:turns) {
            var row=new LinkedHashMap<String,String>();
            row.put("question",limit(Objects.toString(turn.question(),"首轮复盘"),500));
            row.put("previousAssistantText",limit(turn.answer(),1000));
            rows.add(row);
        }
        String json=JSON.toJSONString(rows);
        while(points(json)>maximum && rows.size()>1) {
            rows.remove(0);json=JSON.toJSONString(rows);
        }
        while(points(json)>maximum) {
            var row=rows.get(0);
            String key=row.get("previousAssistantText").isEmpty()?"question":"previousAssistantText";
            if(row.get(key).isEmpty()) return "";
            row.put(key,limit(row.get(key),Math.max(0,points(row.get(key))-100)));
            json=JSON.toJSONString(rows);
        }
        return json;
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
