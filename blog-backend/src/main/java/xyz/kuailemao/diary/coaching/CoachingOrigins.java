package xyz.kuailemao.diary.coaching;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.ObjectProvider;
import xyz.kuailemao.diary.DiaryFailure;
import xyz.kuailemao.diary.ai.DiaryAiStore;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;

/** 外部来源只能通过带本人验证的读取路径解析；链接本身不授予访问权。 */
public final class CoachingOrigins implements CoachingStore.OriginCheck {
    private final ObjectProvider<DiaryAiStore> ai;
    private final CoachingStore store;
    private final ObjectProvider<CoachingPeriodAi> periods;
    public CoachingOrigins(ObjectProvider<DiaryAiStore> ai,CoachingStore store,ObjectProvider<CoachingPeriodAi> periods) { this.ai=ai;this.store=store;this.periods=periods; }
    public JSONObject suggestion(long owner,Origin origin) {
        origin.validate();String result;
        if("AI_REPORT".equals(origin.type())) {
            DiaryAiStore source=ai.getIfAvailable();if(source==null || !source.available(owner)) throw DiaryFailure.unavailable();
            result=source.report(owner,origin.reportId()).result();
        } else {
            CoachingPeriodAi service=periods.getIfAvailable();if(service==null) throw DiaryFailure.unavailable();
            result=service.report(owner,origin.reportId()).result();
        }
        var suggestions=JSON.parseObject(result).getJSONArray("suggestions");
        if(suggestions==null || origin.suggestionIndex()>=suggestions.size()) throw DiaryFailure.invalid("建议不存在");
        JSONObject suggestion=suggestions.getJSONObject(origin.suggestionIndex());
        if(!hash(suggestion).equals(origin.suggestionHash())) throw DiaryFailure.conflict();return suggestion;
    }
    @Override public void verify(long owner,Origin origin) { suggestion(owner,origin); }
    public String status(long owner,java.util.UUID action) {
        var links=store.tx(owner,true,()->{
            store.get(owner,Kind.ACTION,action,false);
            return store.jdbc.query("SELECT * FROM diary_coaching.origin_link WHERE owner_id=:owner AND action_id=:id",CoachingStore.parameters(owner,action),
                (r,n)->new Origin(r.getString("type"),r.getObject("report_id",java.util.UUID.class),r.getInt("suggestion_index"),r.getString("suggestion_hash")));
        });
        // A missing external source is an expected link state. Resolve it after the owned-link transaction
        // so the legacy AI reader's rollback-only marker cannot turn an expected 404 into a commit failure.
        if(links.isEmpty()) return "MANUAL";
        try { suggestion(owner,links.get(0));return "CURRENT"; }
        catch(DiaryFailure failure) { return failure.status()==404||failure.status()==409?"STALE":"UNAVAILABLE"; }
    }
}
