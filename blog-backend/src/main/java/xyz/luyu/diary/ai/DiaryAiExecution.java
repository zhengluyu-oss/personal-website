package xyz.luyu.diary.ai;

import xyz.luyu.diary.DiaryFailure;
import java.util.*;
import java.util.function.Supplier;

/** Narrow shared-budget/lease adapter. Never exposes endpoint credentials or opens a second pool. */
public final class DiaryAiExecution {
    private final DiaryAiStore store;
    public DiaryAiExecution(DiaryAiStore store) { this.store=store; }
    public <T>T queue(long owner,Supplier<T> operation) {
        store.ensureProfile(owner);
        return store.tx(owner,()->{
            var p=Map.of("owner",owner);
            store.jdbc.queryForObject("SELECT owner_id FROM diary_ai.owner_settings WHERE owner_id=:owner FOR UPDATE",p,Long.class);
            // Counts supplied by extension must be obtained inside this lock, through queueCounts below.
            return operation.get();
        });
    }
    public void queueCounts(long owner,long extraQueued,long extraReports) {
        var p=Map.of("owner",owner);
        long queued=store.jdbc.queryForObject("SELECT count(*) FROM diary_ai.job WHERE owner_id=:owner AND state IN('QUEUED','RUNNING')",p,Long.class);
        long reports=store.jdbc.queryForObject("SELECT count(*) FROM diary_ai.report WHERE owner_id=:owner",p,Long.class);
        if(queued+extraQueued>=store.properties.getMaxQueue() || reports+extraReports+queued+extraQueued>=store.properties.getMaxReports()) throw new DiaryFailure(429,"共享任务或报告已达到上限");
    }
    public void lease(long owner,UUID token) { store.tx(owner,()->{store.lease(owner,token);return null;}); }
    public void reserveGeneration(long owner) { store.reserve(owner,1,0); }
    public void usage(long owner,Long input,Long output) { store.usage(owner,input,output); }
}
