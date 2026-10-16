package xyz.kuailemao.diary.coaching;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import xyz.kuailemao.diary.*;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;

@Service
public class CoachingService {
    private final DiaryService diary;
    private final CoachingProperties properties;
    private final ObjectProvider<CoachingStore> stores;
    private final ObjectProvider<CoachingPeriods> periods;
    private final ObjectProvider<CoachingOrigins> origins;
    private final ObjectProvider<CoachingPeriodAi> ai;
    public CoachingService(DiaryService diary,CoachingProperties properties,ObjectProvider<CoachingStore> stores,
                           ObjectProvider<CoachingPeriods> periods,ObjectProvider<CoachingOrigins> origins,ObjectProvider<CoachingPeriodAi> ai) {
        this.diary=diary;this.properties=properties;this.stores=stores;this.periods=periods;this.origins=origins;this.ai=ai;
    }
    public long owner() { return diary.requireOwner(); }
    public CoachingStore store(long owner) {
        diary.store(owner);CoachingStore store=stores.getIfAvailable();
        if(!properties.isEnabled() || store==null || !store.available(owner)) throw DiaryFailure.unavailable();return store;
    }
    public Capabilities capabilities() {
        long owner=owner();CoachingStore store=stores.getIfAvailable();boolean ready=properties.isEnabled() && diary.capabilities().available() && store!=null && store.available(owner);
        CoachingPeriodAi model=ai.getIfAvailable();boolean aiReady=ready && model!=null && model.available(owner);
        return new Capabilities(properties.isEnabled(),ready,aiReady,ready?(aiReady?"手动记录和期间AI可用。只有点击生成并确认后才会发送。"+model.description():"目标、行动和反馈可用；期间AI尚未配置或关闭，手动预览不调用模型。"):"目标跟踪尚未启用或存储暂不可用。");
    }
    public CoachingPeriods periods(long owner) { store(owner);CoachingPeriods value=periods.getIfAvailable();if(value==null) throw DiaryFailure.unavailable();return value; }
    public CoachingOrigins origins(long owner) { store(owner);CoachingOrigins value=origins.getIfAvailable();if(value==null) throw DiaryFailure.unavailable();return value; }
    public CoachingPeriodAi ai(long owner) { store(owner);CoachingPeriodAi value=ai.getIfAvailable();if(value==null) throw DiaryFailure.unavailable();return value; }
}
