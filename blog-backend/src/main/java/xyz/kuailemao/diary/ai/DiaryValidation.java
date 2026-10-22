package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.*;
import java.util.*;

/** Strict preflight. Only application-owned paths and enum values may leave this boundary. */
public final class DiaryValidation {
    private DiaryValidation() {}
    public enum Reason { JSON_PARSE, ROOT_FIELDS, REQUIRED, TYPE, TEXT_LIMIT, ARRAY_LIMIT, SOURCE_UNKNOWN, QUOTE_MISMATCH, QUOTE_ID_UNKNOWN, QUOTE_ID_FOREIGN, QUOTE_ID_SOURCE, QUOTE_ID_TYPE, EVIDENCE_TYPE, CONTRACT_UNKNOWN }
    public static final class Issue extends RuntimeException {
        private final Reason reason; private final String path;
        private Issue(Reason reason,String path){super(reason.name());this.reason=reason;this.path=path;}
        public Reason reason(){return reason;} public String path(){return path;}
    }
    public record Evidence(String id,String text,String kind) {}
    public static Issue unknown(){return new Issue(Reason.CONTRACT_UNKNOWN,"$");}
    static void fail(Reason reason,String path){throw new Issue(reason,path);}
    static JSONObject object(Object value,String path){if(value==null)fail(Reason.REQUIRED,path);if(!(value instanceof JSONObject))fail(Reason.TYPE,path);return (JSONObject)value;}
    static void fields(JSONObject value,Set<String> fields,String path){if(!fields.equals(value.keySet()))fail(Reason.ROOT_FIELDS,path);}
    static String text(Object value,int max,String path){if(value==null || value instanceof String s && s.isBlank())fail(Reason.REQUIRED,path);if(!(value instanceof String))fail(Reason.TYPE,path);String s=(String)value;if(DiaryAiModels.points(s)>max || s.indexOf('\0')>=0)fail(Reason.TEXT_LIMIT,path);return s;}
    static JSONArray array(Object value,int min,int max,String path){if(value==null)fail(Reason.REQUIRED,path);if(!(value instanceof JSONArray))fail(Reason.TYPE,path);JSONArray a=(JSONArray)value;if(a.size()<min)fail(Reason.REQUIRED,path);if(a.size()>max)fail(Reason.ARRAY_LIMIT,path);return a;}
    static void bool(Object value,String path){if(!(value instanceof Boolean))fail(value==null?Reason.REQUIRED:Reason.TYPE,path);}
    static List<String> ids(Object value,Map<String,Evidence> sources,boolean required,int max,String path){var a=array(value,required?1:0,max,path);var ids=new ArrayList<String>();for(int i=0;i<a.size();i++){String s=text(a.get(i),128,path+"["+i+"]");if(!sources.containsKey(s))fail(Reason.SOURCE_UNKNOWN,path+"["+i+"]");if(ids.contains(s))fail(Reason.TYPE,path);ids.add(s);}return ids;}
    static void statement(Object value,Map<String,Evidence> sources,String path,boolean coaching,String section){
        var s=object(value,path);fields(s,coaching?Set.of("text","type","evidenceIds","quotes"):Set.of("text","evidenceIds","quotes"),path);text(s.get("text"),2000,path+".text");
        var ids=ids(s.get("evidenceIds"),sources,true,coaching?20:100,path+".evidenceIds");
        if(coaching){String type=text(s.get("type"),32,path+".type");if(!Set.of("GOAL","PLAN","OBSERVATION","EXECUTION","EFFECT","HYPOTHESIS").contains(type))fail(Reason.EVIDENCE_TYPE,path+".type");if(section!=null && !(section.equals(type)||section.equals("FEEDBACK")&&Set.of("EXECUTION","EFFECT").contains(type)))fail(Reason.EVIDENCE_TYPE,path+".type");String needed=switch(type){case "GOAL"->"G";case "PLAN"->"A";case "EXECUTION","EFFECT"->"F";default->null;};if(needed!=null&&ids.stream().noneMatch(id->needed.equals(sources.get(id).kind())))fail(Reason.EVIDENCE_TYPE,path+".evidenceIds");}
        var quotes=array(s.get("quotes"),1,coaching?10:8,path+".quotes");for(int i=0;i<quotes.size();i++){String qp=path+".quotes["+i+"]";var q=object(quotes.get(i),qp);fields(q,Set.of("sourceId","text"),qp);String id=text(q.get("sourceId"),128,qp+".sourceId");if(!ids.contains(id))fail(Reason.SOURCE_UNKNOWN,qp+".sourceId");String quote=text(q.get("text"),coaching?2000:250,qp+".text");if(!sources.get(id).text().contains(quote))fail(Reason.QUOTE_MISMATCH,qp+".text");}
    }
    public static void check(String raw,List<Evidence> evidence,String kind){
        JSONObject root;
        if(raw==null)throw new Issue(Reason.REQUIRED,"$");
        if(DiaryAiModels.points(raw)>32000)throw new Issue(Reason.TEXT_LIMIT,"$");
        try{Object parsed=JSON.parse(raw);root=object(parsed,"$");}catch(Issue e){throw e;}catch(RuntimeException e){throw new Issue(Reason.JSON_PARSE,"$");}
        boolean period="PERIOD".equals(kind);var sources=new HashMap<String,Evidence>();for(var e:evidence)sources.put(e.id(),e);
        if(period)fields(root,Set.of("coverage","currentGoals","observations","feedback","hypotheses","suggestions","limitations"),"$");
        else if(!Set.of("answer","summary","observations","hypotheses","suggestions","limitations","insufficientEvidence").containsAll(root.keySet()))fail(Reason.ROOT_FIELDS,"$");
        if(period)text(root.get("coverage"),2000,"$.coverage");else{bool(root.get("insufficientEvidence"),"$.insufficientEvidence");}
        var limits=array(root.get("limitations"),1,period?20:10,"$.limitations");for(int i=0;i<limits.size();i++)text(limits.get(i),period?1000:500,"$.limitations["+i+"]");
        if(!period&&Boolean.TRUE.equals(root.get("insufficientEvidence")))return;
        if(!period){String key="QUESTION".equals(kind)?"answer":"summary",other=key.equals("answer")?"summary":"answer";statement(root.get(key),sources,"$."+key,false,null);if(root.get(other)!=null)fail(Reason.TYPE,"$."+other);}
        for(String key:period?List.of("currentGoals","observations","feedback","hypotheses"):List.of("observations","hypotheses")){
            var a=array(root.get(key),0,period?20:key.equals("observations")?12:8,"$."+key);String section=key.equals("currentGoals")?"GOAL":key.equals("feedback")?"FEEDBACK":key.equals("hypotheses")&&period?"HYPOTHESIS":null;
            for(int i=0;i<a.size();i++)statement(a.get(i),sources,"$."+key+"["+i+"]",period,section);
        }
        var suggestions=array(root.get("suggestions"),0,period?10:8,"$.suggestions");for(int i=0;i<suggestions.size();i++){String path="$.suggestions["+i+"]";var s=object(suggestions.get(i),path);fields(s,Set.of("action","reason","evidenceIds","effort","reviewAfter","general"),path);for(String key:List.of("action","reason","effort","reviewAfter"))text(s.get(key),period?(Set.of("action","reason").contains(key)?2000:500):(Set.of("action","reason").contains(key)?600:200),path+"."+key);bool(s.get("general"),path+".general");ids(s.get("evidenceIds"),sources,!Boolean.TRUE.equals(s.get("general")),period?20:100,path+".evidenceIds");}
    }
}
