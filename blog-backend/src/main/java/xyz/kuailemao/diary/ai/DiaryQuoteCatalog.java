package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import java.util.*;

/** Per-request immutable quote choices; the provider never supplies trusted quote text. */
public final class DiaryQuoteCatalog {
    public record SourceText(String sourceId,String kind,long revision,String text) {}
    public record Item(String quoteId,String sourceId,String kind,long revision,String text) {}
    private final List<Item> items;
    private final Map<String,Item> byId;
    private final String taskPrefix;

    private DiaryQuoteCatalog(List<Item> items,String taskPrefix) {
        this.items=List.copyOf(items);
        this.byId=new HashMap<>();
        for(Item item:items)byId.put(item.quoteId(),item);
        this.taskPrefix=taskPrefix;
    }
    public List<Item> items(){return items;}

    public static DiaryQuoteCatalog build(UUID task,List<SourceText> sources,int budget) {
        String prefix="Q"+task+"-";
        if(budget<160 || sources.isEmpty())return new DiaryQuoteCatalog(List.of(),prefix);
        List<List<String>> choices=new ArrayList<>();
        for(SourceText source:sources)choices.add(chunks(source.text()));
        List<Item> result=new ArrayList<>();
        int used=2,slots=Math.min(80,Math.max(1,budget/105));
        int perSource=(slots+sources.size()-1)/sources.size();
        for(int round=0;round<perSource && result.size()<slots;round++) {
            boolean added=false;
            for(int sourceIndex=0;sourceIndex<sources.size() && result.size()<slots;sourceIndex++) {
                var candidates=choices.get(sourceIndex);
                if(round>=candidates.size())continue;
                int index=sample(round,candidates.size(),perSource);
                String quote=candidates.get(index);
                SourceText source=sources.get(sourceIndex);
                Item item=new Item(prefix+(result.size()+1),source.sourceId(),source.kind(),source.revision(),quote);
                int cost=DiaryAiModels.points(JSON.toJSONString(promptItem(item)))+1;
                if(used+cost>budget)continue;
                result.add(item);used+=cost;added=true;
            }
            if(!added)break;
        }
        return new DiaryQuoteCatalog(result,prefix);
    }

    private static int sample(int ordinal,int size,int perSource) {
        if(size<=perSource)return ordinal;
        return Math.min(size-1,(int)((long)ordinal*(size-1)/Math.max(1,perSource-1)));
    }

    private static List<String> chunks(String text) {
        if(text==null || text.isBlank())return List.of();
        List<String> result=new ArrayList<>();
        for(String sentence:text.split("(?<=[。！？!?；;\\n])")) {
            String value=sentence.trim();
            int length=DiaryAiModels.points(value);
            for(int start=0;start<length;start+=80) {
                String part=DiaryAiModels.slice(value,start,Math.min(length,start+80)).trim();
                if(!part.isBlank())result.add(part);
            }
        }
        return result;
    }

    public String attach(String context) {
        JSONObject root=JSON.parseObject(context);
        root.put("quoteCatalog",items.stream().map(DiaryQuoteCatalog::promptItem).toList());
        return JSON.toJSONString(root,JSONWriter.Feature.WriteNulls);
    }
    private static Map<String,String> promptItem(Item item) {
        return Map.of("quoteId",item.quoteId(),"sourceId",item.sourceId(),"text",item.text());
    }

    public String hydrate(String raw) {
        JSONObject root;
        try {root=JSON.parseObject(raw);}catch(RuntimeException invalid){DiaryValidation.fail(DiaryValidation.Reason.JSON_PARSE,"$");return raw;}
        if(root==null)return raw;
        for(String key:List.of("answer","summary"))hydrateStatement(root.get(key),"$."+key);
        for(String key:List.of("observations","hypotheses","currentGoals","feedback")) {
            Object value=root.get(key);
            if(!(value instanceof JSONArray array))continue;
            for(int i=0;i<array.size();i++)hydrateStatement(array.get(i),"$."+key+"["+i+"]");
        }
        return JSON.toJSONString(root,JSONWriter.Feature.WriteNulls);
    }

    private void hydrateStatement(Object value,String path) {
        if(!(value instanceof JSONObject statement))return;
        Object rawQuotes=statement.get("quotes");
        if(!(rawQuotes instanceof JSONArray quotes))return;
        Object rawIds=statement.get("evidenceIds");
        for(int i=0;i<quotes.size();i++) {
            String quotePath=path+".quotes["+i+"]";
            if(!(quotes.get(i) instanceof JSONObject quote))continue;
            if(!quote.keySet().equals(Set.of("quoteId")))DiaryValidation.fail(DiaryValidation.Reason.QUOTE_ID_UNKNOWN,quotePath);
            Object valueId=quote.get("quoteId");
            if(!(valueId instanceof String id))DiaryValidation.fail(DiaryValidation.Reason.QUOTE_ID_UNKNOWN,quotePath+".quoteId");
            String id=(String)valueId;
            if(!id.startsWith(taskPrefix))DiaryValidation.fail(DiaryValidation.Reason.QUOTE_ID_FOREIGN,quotePath+".quoteId");
            Item item=byId.get(id);
            if(item==null)DiaryValidation.fail(DiaryValidation.Reason.QUOTE_ID_UNKNOWN,quotePath+".quoteId");
            if(!(rawIds instanceof JSONArray ids) || !ids.contains(item.sourceId()))DiaryValidation.fail(DiaryValidation.Reason.QUOTE_ID_SOURCE,quotePath+".quoteId");
            String type=statement.getString("type");
            String required=switch(Objects.toString(type,"")){case "GOAL"->"G";case "PLAN"->"A";case "EXECUTION","EFFECT"->"F";default->null;};
            if(required!=null && !required.equals(item.kind()))DiaryValidation.fail(DiaryValidation.Reason.QUOTE_ID_TYPE,quotePath+".quoteId");
            quote.clear();quote.put("sourceId",item.sourceId());quote.put("text",item.text());
        }
    }
}
