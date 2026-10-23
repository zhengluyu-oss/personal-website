package xyz.luyu.diary.coaching;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import xyz.luyu.diary.DiaryFailure;
import xyz.luyu.diary.ai.DiaryAiText;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import static xyz.luyu.diary.ai.DiaryAiModels.points;

/** 人工记录的日期、版本和事实类型独立于模型推测；不接受客户端提供 owner。 */
public final class CoachingModels {
    private CoachingModels() {}
    public static final ZoneId ZONE=ZoneId.of("Asia/Shanghai");
    public static final String RENDER_VERSION="coaching-v1";
    public enum Kind {
        GOAL("goal"), ACTION("action"), FEEDBACK("feedback");
        public final String table;
        Kind(String table) { this.table=table; }
        public static Kind parse(String path) {
            return switch(path) { case "goals" -> GOAL; case "actions" -> ACTION; case "feedback" -> FEEDBACK;
                default -> throw DiaryFailure.invalid("记录类型无效"); };
        }
    }
    public record Origin(String type,UUID reportId,int suggestionIndex,String suggestionHash) {
        public void validate() {
            if(!Set.of("AI_REPORT","PERIOD_REPORT").contains(type==null?"":type) || reportId==null
                    || suggestionIndex<0 || suggestionIndex>19 || suggestionHash==null || !suggestionHash.matches("[0-9a-f]{64}"))
                throw DiaryFailure.invalid("建议来源无效，请重新打开建议");
        }
    }
    public record Write(UUID id,UUID mutationId,long expectedRevision,JSONObject content,Origin origin,Long completeActionRevision) {}
    public record Row(Kind kind,UUID id,UUID parentId,long revision,JSONObject content,Instant createdAt,Instant updatedAt,Instant deletedAt) {}
    public record Ack(UUID id,UUID mutationId,long revision,Instant savedAt) {}
    public record History(long revision,JSONObject content,Instant savedAt,Instant deletedAt) {}
    public record Page<T>(List<T> records,long total,int page,int pageSize) {}
    public record Capabilities(boolean enabled,boolean available,boolean aiAvailable,String message) {}
    public record Source(String sourceId,String kind,UUID entityId,long revision,String field,int start,int end,String sha256,String text) {}
    public record Period(String type,LocalDate anchor,LocalDate from,LocalDate to,String axis,List<UUID> goalIds,boolean includeUndated) {
        public Window normalize(LocalDate today) {
            if(type==null || !Set.of("WEEK","MONTH","CUSTOM").contains(type)
                    || axis!=null && !Set.of("covered","created").contains(axis)) throw DiaryFailure.invalid("期间或日记时间轴无效");
            if(goalIds==null || goalIds.isEmpty() || goalIds.size()>20 || goalIds.stream().anyMatch(Objects::isNull)
                    || new HashSet<>(goalIds).size()!=goalIds.size()) throw DiaryFailure.invalid("请选择1至20个不同目标");
            LocalDate first,last;
            if("CUSTOM".equals(type)) {
                if(anchor!=null || from==null || to==null) throw DiaryFailure.invalid("自定义期间需要起止日期");
                first=from;last=to;
            } else {
                if(anchor==null || from!=null || to!=null) throw DiaryFailure.invalid("请选择期间基准日期");
                first="WEEK".equals(type)?anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)):anchor.withDayOfMonth(1);
                last="WEEK".equals(type)?first.plusDays(6):first.with(TemporalAdjusters.lastDayOfMonth());
            }
            if(first.isAfter(last) || first.isAfter(today) || first.plusYears(1).isBefore(last)) throw DiaryFailure.invalid("期间无效或完全位于未来");
            return new Window(type,first,last,axis==null?"covered":axis,List.copyOf(goalIds),includeUndated,last.isAfter(today),today);
        }
    }
    public record Window(String type,LocalDate from,LocalDate to,String axis,List<UUID> goalIds,boolean includeUndated,boolean unfinished,LocalDate asOf) {}
    public record Statistics(int feedback,int tried,int completed,int skipped,int helpful,int notHelpful,int unsure,int notEvaluated,
                             int evaluable,Integer helpfulPercent,int undated,String explanation) {}
    public record Preview(Window window,List<JSONObject> diaries,List<Row> goals,List<Row> actions,List<Row> feedback,
                          Statistics statistics,List<Source> sources,int characters,boolean withinBudget,String snapshotDigest,List<String> limitations) {}

    public static JSONObject normalize(Kind kind,JSONObject raw,LocalDate today) {
        if(raw==null || points(raw.toJSONString())>12000) throw DiaryFailure.invalid("记录内容缺失或过长");
        Set<String> fields=switch(kind) {
            case GOAL -> Set.of("title","outcome","criterion","startOn","targetOn","state");
            case ACTION -> Set.of("goalId","text","plannedOn","state");
            case FEEDBACK -> Set.of("actionId","execution","effect","occurredOn","note");
        };
        if(!fields.containsAll(raw.keySet())) throw DiaryFailure.invalid("记录含有不支持的字段");
        JSONObject data=new JSONObject();
        switch(kind) {
            case GOAL -> {
                data.put("title",text(raw,"title",120,true));data.put("outcome",text(raw,"outcome",2000,false));
                data.put("criterion",text(raw,"criterion",2000,false));
                LocalDate start=date(raw,"startOn"),target=date(raw,"targetOn");
                if(start!=null && target!=null && start.isAfter(target)) throw DiaryFailure.invalid("目标起始日期不能晚于目标日期");
                putDate(data,"startOn",start);putDate(data,"targetOn",target);
                data.put("state",choice(raw,"state",Set.of("ACTIVE","PAUSED","COMPLETED","ARCHIVED")));
            }
            case ACTION -> {
                data.put("goalId",uuid(raw,"goalId").toString());data.put("text",text(raw,"text",2000,true));
                putDate(data,"plannedOn",date(raw,"plannedOn"));
                data.put("state",choice(raw,"state",Set.of("PLANNED","IN_PROGRESS","COMPLETED","SKIPPED","ARCHIVED")));
            }
            case FEEDBACK -> {
                data.put("actionId",uuid(raw,"actionId").toString());
                data.put("execution",choice(raw,"execution",Set.of("TRIED","COMPLETED","SKIPPED")));
                data.put("effect",choice(raw,"effect",Set.of("HELPFUL","NOT_HELPFUL","UNSURE","NOT_EVALUATED")));
                LocalDate occurred=date(raw,"occurredOn");
                if(occurred!=null && occurred.isAfter(today)) throw DiaryFailure.invalid("实际发生日期不能位于未来");
                putDate(data,"occurredOn",occurred);data.put("note",text(raw,"note",2000,false));
            }
        }
        return data;
    }
    private static void putDate(JSONObject data,String field,LocalDate date) { data.put(field,date==null?null:date.toString()); }
    private static UUID uuid(JSONObject raw,String field) { return UUID.fromString(raw.getString(field)); }
    private static LocalDate date(JSONObject raw,String field) {
        String value=raw.getString(field);return value==null||value.isBlank()?null:LocalDate.parse(value);
    }
    private static String choice(JSONObject raw,String field,Set<String> choices) {
        String value=raw.getString(field);if(value==null || !choices.contains(value)) throw DiaryFailure.invalid("请选择有效状态或评价");return value;
    }
    private static String text(JSONObject raw,String field,int max,boolean required) {
        Object value=raw.get(field);if(value!=null && !(value instanceof String)) throw DiaryFailure.invalid("文本字段格式无效");
        String text=value==null?"":(String)value;
        if(points(text)>max || required && text.isBlank() || text.indexOf('\0')>=0) throw DiaryFailure.invalid("文本为空、过长或含无效字符");return text;
    }
    public static String canonical(Object value) { return JSON.toJSONString(ordered(JSON.toJSON(value))); }
    private static Object ordered(Object value) {
        if(value instanceof Map<?,?> map) {
            var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),ordered(v)));return sorted;
        }
        if(value instanceof Collection<?> list) return list.stream().map(CoachingModels::ordered).toList();return value;
    }
    public static String hash(Object value) { return DiaryAiText.hash(canonical(value)); }
}
