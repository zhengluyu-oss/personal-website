package xyz.luyu.diary.coaching;

import com.alibaba.fastjson2.JSONObject;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.luyu.diary.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static xyz.luyu.diary.coaching.CoachingModels.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class CoachingPerformanceTest {
    @Test void monthBoundaryPreviewAndConcurrentDiarySavingStayWithinThreeConnections() throws Exception {
        try(var pool=new HikariDataSource()) {
            pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");pool.setUsername("diary_runtime");pool.setPassword("isolated-diary-runtime");pool.setMaximumPoolSize(3);pool.setMinimumIdle(0);pool.setConnectionTimeout(1500);
            var jdbc=new NamedParameterJdbcTemplate(pool);var manager=new DataSourceTransactionManager(pool);var store=new CoachingStore(jdbc,manager);var periods=new CoachingPeriods(store);var diary=new DiaryStore(jdbc,manager);
            long owner=(UUID.randomUUID().getMostSignificantBits()&0x0000ffffffffffffL)+1000;List<UUID> goals=new ArrayList<>(),actions=new ArrayList<>(),entries=new ArrayList<>();
            for(int n=0;n<20;n++) { UUID id=UUID.randomUUID();goals.add(id);store.write(owner,Kind.GOAL,new Write(id,UUID.randomUUID(),0,JSONObject.of("title","fictional goal "+n,"state","ACTIVE"),null,null),false,(o,s)->{}); }
            for(int n=0;n<80;n++) { UUID id=UUID.randomUUID();actions.add(id);store.write(owner,Kind.ACTION,new Write(id,UUID.randomUUID(),0,JSONObject.of("goalId",goals.get(n%20).toString(),"text","fictional planned step "+n,"state","PLANNED"),null,null),false,(o,s)->{}); }
            for(int n=0;n<100;n++) store.write(owner,Kind.FEEDBACK,new Write(UUID.randomUUID(),UUID.randomUUID(),0,JSONObject.of("actionId",actions.get(n%80).toString(),"execution","TRIED","effect",n%2==0?"HELPFUL":"UNSURE","occurredOn",n%5==0?null:"2024-02-29","note","fictional feedback "+n),null,null),false,(o,s)->{});
            for(int n=0;n<40;n++) { UUID id=UUID.randomUUID();entries.add(id);diary.create(owner,new DiaryModels.Write(id,UUID.randomUUID(),0,"fictional boundary "+n,"fictional whole entry, not per-day activity",LocalDate.of(2024,2,1),LocalDate.of(2024,2,29))); }
            var request=new CoachingModels.Period("MONTH",LocalDate.of(2024,2,15),null,null,"covered",goals,true);
            var full=periods.preview(owner,request);assertEquals(40,full.diaries().size());assertEquals(200,full.goals().size()+full.actions().size()+full.feedback().size());assertEquals(80,full.statistics().feedback());assertEquals(20,full.statistics().undated());assertFalse(full.withinBudget());
            AtomicBoolean stop=new AtomicBoolean();var executor=Executors.newSingleThreadExecutor();List<Long> saves=Collections.synchronizedList(new ArrayList<>());
            var writer=executor.submit(()->{long rev=1;int n=0;while(!stop.get() && n<100) { long begin=System.nanoTime();rev=diary.save(owner,entries.get(0),new DiaryModels.Write(entries.get(0),UUID.randomUUID(),rev,"fictional concurrent","fictional revision "+n++,LocalDate.of(2024,2,1),LocalDate.of(2024,2,29))).revision();saves.add((System.nanoTime()-begin)/1_000_000); } });
            List<Long> timings=new ArrayList<>();
            try { for(int n=0;n<40;n++) { long begin=System.nanoTime();assertEquals(40,periods.preview(owner,request).diaries().size());timings.add((System.nanoTime()-begin)/1_000_000); } }
            finally { stop.set(true);writer.get(10,TimeUnit.SECONDS);executor.shutdownNow(); }
            Collections.sort(timings);Collections.sort(saves);long p95=timings.get((int)Math.ceil(timings.size()*.95)-1);assertTrue(p95<=1000,"Preview p95 exceeded 1 second: "+p95);assertFalse(saves.isEmpty());assertEquals(3,pool.getMaximumPoolSize());
            System.out.println("COACHING_PERFORMANCE "+JSONObject.of("previewSamples",timings.size(),"previewP95Ms",p95,"saveSamples",saves.size(),"saveP95Ms",saves.get((int)Math.ceil(saves.size()*.95)-1),"poolMax",pool.getMaximumPoolSize(),"diaries",40,"manualRecords",200));
        }
    }
}
