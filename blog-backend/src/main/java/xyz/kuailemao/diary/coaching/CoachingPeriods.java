package xyz.kuailemao.diary.coaching;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import xyz.kuailemao.diary.DiaryFailure;
import xyz.kuailemao.diary.ai.DiaryAiText;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.points;

/** 预览只做确定性计算。计划不等于执行，未知日期不被归入任意一天。 */
public final class CoachingPeriods {
    private final CoachingStore store;
    public CoachingPeriods(CoachingStore store) { this.store=store; }
    public Preview preview(long owner,Period request) { return store.tx(owner,true,()->build(owner,request)); }
    Preview build(long owner,Period request) {
        if(request==null) throw DiaryFailure.invalid("请选择期间");
        Window window=request.normalize(LocalDate.now(ZONE));
        var p=new HashMap<String,Object>();p.put("owner",owner);
        String condition=" owner_id=:owner AND deleted_at IS NULL";
        if("covered".equals(window.axis())) {
            condition+=" AND covered_from<=:to AND covered_to>=:from";p.put("from",window.from());p.put("to",window.to());
        } else {
            condition+=" AND created_at>=:from AND created_at<:to";
            p.put("from",Timestamp.from(window.from().atStartOfDay(ZONE).toInstant()));p.put("to",Timestamp.from(window.to().plusDays(1).atStartOfDay(ZONE).toInstant()));
        }
        var identities=store.jdbc.query("SELECT id,revision,covered_from,covered_to,length(body) AS characters FROM diary.entry WHERE "+condition+" ORDER BY id LIMIT 10001",p,
            (r,n)->JSONObject.of("id",r.getString("id"),"revision",r.getLong("revision"),"coveredFrom",r.getString("covered_from"),"coveredTo",r.getString("covered_to"),"characters",r.getInt("characters")));
        if(identities.size()>10000) throw DiaryFailure.invalid("匹配范围超过一万篇，请缩小范围");
        boolean diaryFits=identities.size()<=40 && identities.stream().mapToLong(r->r.getLongValue("characters")).sum()<=24000;
        List<JSONObject> diaries=diaryFits?store.jdbc.query("SELECT id,title,body,revision,covered_from,covered_to,created_at FROM diary.entry WHERE "+condition+" ORDER BY id",p,(r,n)->{
            var value=JSONObject.of("id",r.getString("id"),"title",r.getString("title"),"body",r.getString("body"),"revision",r.getLong("revision"),
                "coveredFrom",r.getString("covered_from"),"coveredTo",r.getString("covered_to"),"createdAt",r.getTimestamp("created_at").toInstant().toString());
            LocalDate from=r.getObject("covered_from",LocalDate.class),to=r.getObject("covered_to",LocalDate.class);
            value.put("crossesBoundary",from!=null && to!=null && (from.isBefore(window.from()) || to.isAfter(window.to())));return value;
        }):identities;
        var goals=store.selected(owner,Kind.GOAL,window.goalIds(),false,21);
        if(goals.size()!=window.goalIds().size()) throw new DiaryFailure(404,"目标不存在或不可访问");
        var actions=store.selected(owner,Kind.ACTION,window.goalIds(),true,10001);
        if(actions.size()>10000) throw DiaryFailure.invalid("行动总量过大，请减少所选目标");
        var feedback=store.selected(owner,Kind.FEEDBACK,actions.stream().map(Row::id).toList(),true,10001);
        if(feedback.size()>10000) throw DiaryFailure.invalid("反馈总量过大，请减少所选目标");
        int undated=(int)feedback.stream().filter(r->r.content().getString("occurredOn")==null).count();
        var matched=feedback.stream().filter(r->{
            String value=r.content().getString("occurredOn");if(value==null) return window.includeUndated();
            LocalDate date=LocalDate.parse(value);return !date.isBefore(window.from()) && !date.isAfter(window.to());
        }).toList();
        var dated=matched.stream().filter(r->r.content().getString("occurredOn")!=null).toList();
        Statistics statistics=statistics(dated,undated);
        var limitations=new ArrayList<String>();
        limitations.add("目标和行动状态为截至本次查看的当前状态，不代表期间结束时的历史状态；计划不能证明执行。");
        limitations.add("反馈按实际发生日期筛选，与日记时间轴独立。未记录反馈不等于没有执行；效果是本人评价，不是因果结论。");
        if(undated>0) limitations.add("另有"+undated+"条反馈实际日期未知，不计入期间计数。"+(window.includeUndated()?"作为未知日期背景资料单列。":"未发送给模型。"));
        if("covered".equals(window.axis())) limitations.add("日记按记录起止日期与期间重叠筛选；无完整起止日期的篇目不在本次范围。跨边界全文仅计一篇，不拆分或推算逐日行为。");
        if(window.unfinished()) limitations.add("本期间尚未结束，只能说明已录入资料，不能称为完整周期总结。");
        var sources=new ArrayList<Source>();
        if(diaryFits) for(JSONObject diary:diaries) source(sources,"D",UUID.fromString(diary.getString("id")),diary.getLongValue("revision"),"body",diary.getString("body"));
        for(Row goal:goals) source(sources,"G",goal.id(),goal.revision(),"content",canonical(goal.content()));
        for(Row action:actions) source(sources,"A",action.id(),action.revision(),"content",canonical(action.content()));
        for(Row item:matched) source(sources,"F",item.id(),item.revision(),"content",canonical(item.content()));
        int characters=points(canonical(context(window,diaries,goals,actions,matched,statistics,sources,limitations)));
        boolean budget=diaryFits && goals.size()<=20 && goals.size()+actions.size()+matched.size()<=200 && characters<=24000;
        if(!budget) limitations.add("完整范围超过AI输入预算，本次不能生成。请减少目标或改选更短的自定义期间；系统不会静默截断。");
        // Complete matching identities, revisions and manual content, including undated data that affects statistics.
        var scope=Map.of("type",window.type(),"from",window.from(),"to",window.to(),"axis",window.axis(),"goals",window.goalIds(),"includeUndated",window.includeUndated());
        // Viewing on another day does not change the source set. Report labels preserve the generation date separately.
        String digest=hash(Arrays.asList(RENDER_VERSION,scope,identities,goals,actions,feedback));
        return new Preview(window,diaries,goals,actions,matched,statistics,List.copyOf(sources),characters,budget,digest,List.copyOf(limitations));
    }
    public static Map<String,Object> context(Preview preview) {
        return context(preview.window(),preview.diaries(),preview.goals(),preview.actions(),preview.feedback(),preview.statistics(),preview.sources(),preview.limitations());
    }
    private static Map<String,Object> context(Window window,List<JSONObject> diaries,List<Row> goals,List<Row> actions,List<Row> feedback,Statistics statistics,List<Source> sources,List<String> limitations) {
        return Map.of("window",window,"diaries",diaries,"goals",goals,"actions",actions,"feedback",feedback,"statistics",statistics,"sources",sources,"limitations",limitations);
    }
    private void source(List<Source> out,String kind,UUID id,long revision,String field,String text) {
        out.add(new Source(kind+(out.size()+1),kind,id,revision,field,0,points(text),DiaryAiText.hash(text),text));
    }
    public static Statistics statistics(List<Row> feedback,int undated) {
        int tried=0,completed=0,skipped=0,helpful=0,notHelpful=0,unsure=0,notEvaluated=0;
        for(Row row:feedback) {
            switch(row.content().getString("execution")) { case "TRIED" -> tried++;case "COMPLETED" -> completed++;case "SKIPPED" -> skipped++;default -> throw DiaryFailure.invalid("反馈状态无效"); }
            switch(row.content().getString("effect")) { case "HELPFUL" -> helpful++;case "NOT_HELPFUL" -> notHelpful++;case "UNSURE" -> unsure++;case "NOT_EVALUATED" -> notEvaluated++;default -> throw DiaryFailure.invalid("反馈评价无效"); }
        }
        int evaluable=helpful+notHelpful;
        return new Statistics(feedback.size(),tried,completed,skipped,helpful,notHelpful,unsure,notEvaluated,evaluable,
            evaluable==0?null:Math.round(100f*helpful/evaluable),undated,"按反馈事件计数，不代表逐日次数或完成率；可评价分母仅含有帮助与无帮助，未知和未评价单列。");
    }
}
