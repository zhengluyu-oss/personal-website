package xyz.luyu.diary;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import xyz.luyu.diary.ai.DiaryAiText;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit disposable loopback database only; never loads application configuration or model clients. */
@EnabledIfEnvironmentVariable(named="BLOG_DIARY_QA",matches="true")
class DiaryReadStoreIntegrationTest {
    static HikariDataSource pool;
    static DiaryStore diary;
    static DiaryReadStore reader;
    static JdbcTemplate admin;
    static final long OWNER=734829001L;
    @BeforeAll static void setup() {
        pool=new HikariDataSource();pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");pool.setUsername("diary_runtime");pool.setPassword("isolated-diary-runtime");pool.setMaximumPoolSize(1);pool.setMinimumIdle(0);
        var jdbc=new NamedParameterJdbcTemplate(pool);var manager=new DataSourceTransactionManager(pool);
        diary=new DiaryStore(jdbc,manager);reader=new DiaryReadStore(jdbc,manager);
        admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://127.0.0.1:15432/diary_qa","postgres","isolated-diary-admin"));
        assertTrue(diary.available(OWNER));
    }
    @AfterAll static void close(){if(pool!=null)pool.close();}
    UUID entry(){var id=UUID.randomUUID();diary.create(OWNER,new DiaryModels.Write(id,UUID.randomUUID(),0,"合成记录","合成😀日记",null,null));return id;}
    UUID report(UUID entry,boolean period) {
        var job=UUID.randomUUID();var id=UUID.randomUUID();String schema=period?"diary_coaching":"diary_ai",jobs=period?"period_job":"job",reports=period?"period_report":"report";
        if(period) {
            admin.update("INSERT INTO "+schema+"."+jobs+"(owner_id,id,request_id,request_hash,state,payload) VALUES(?,?,?,?,'SUCCEEDED','{}')",OWNER,job,UUID.randomUUID(),"synthetic-hash");
            admin.update("INSERT INTO "+schema+"."+reports+"(owner_id,id,job_id,state,result,scope,model,prompt_version) VALUES(?,?,?,'SUCCEEDED','{\"coverage\":\"合成复盘\"}','{}','fixture','fixture')",OWNER,id,job);
            admin.update("INSERT INTO diary_coaching.period_source VALUES(?,?,?,'D',?,1,'body',0,6,?)",OWNER,id,"D1",entry,DiaryAiText.hash("合成😀日记"));
        } else {
            admin.update("INSERT INTO diary_ai.job(owner_id,id,request_id,request_hash,kind,state,payload) VALUES(?,?,?,?,'QUESTION','SUCCEEDED','{}')",OWNER,job,UUID.randomUUID(),"synthetic-hash");
            admin.update("INSERT INTO diary_ai.report(owner_id,id,job_id,kind,state,result,scope_description,model,prompt_version,partial) VALUES(?,?,?,'QUESTION','SUCCEEDED','{\"answer\":{\"text\":\"合成回答\"}}','{}','fixture','fixture',false)",OWNER,id,job);
            admin.update("INSERT INTO diary_ai.report_source VALUES(?,?,?,?,1,0,6,?)",OWNER,id,"D1",entry,DiaryAiText.hash("合成😀日记"));
        }
        return id;
    }
    @Test void currentSourcesReadAndChangedDeletedSourcesDisappearWithoutInvalidationWrites() {
        // Six Unicode code points? Keep offsets derived from the actual text to avoid surrogate mistakes.
        UUID entry=entry();var ai=report(entry,false);var period=report(entry,true);
        int end="合成😀日记".codePointCount(0,"合成😀日记".length());
        admin.update("UPDATE diary_ai.report_source SET end_cp=? WHERE owner_id=? AND report_id=?",end,OWNER,ai);
        admin.update("UPDATE diary_coaching.period_source SET end_cp=? WHERE owner_id=? AND report_id=?",end,OWNER,period);
        assertEquals("合成😀日记",reader.report(OWNER,"ai",ai).sources().get(0).text());
        assertEquals("合成😀日记",reader.report(OWNER,"period",period).sources().get(0).text());
        long aiBefore=reader.reports(OWNER,"ai",1).total(),periodBefore=reader.reports(OWNER,"period",1).total();
        diary.save(OWNER,entry,new DiaryModels.Write(entry,UUID.randomUUID(),1,"修改","新版本不公开旧内容",null,null));
        assertEquals(aiBefore-1,reader.reports(OWNER,"ai",1).total());
        assertEquals(periodBefore-1,reader.reports(OWNER,"period",1).total());
        assertEquals(404,assertThrows(DiaryFailure.class,()->reader.report(OWNER,"ai",ai)).status());
        assertEquals(404,assertThrows(DiaryFailure.class,()->reader.report(OWNER,"period",period)).status());
        assertEquals("SUCCEEDED",admin.queryForObject("SELECT state FROM diary_ai.report WHERE owner_id=? AND id=?",String.class,OWNER,ai));
        assertNotNull(admin.queryForObject("SELECT result::text FROM diary_ai.report WHERE owner_id=? AND id=?",String.class,OWNER,ai));
        UUID deleted=entry();UUID deletedReport=report(deleted,false);diary.delete(OWNER,deleted,new DiaryModels.Change(UUID.randomUUID(),1,null));
        assertEquals(404,assertThrows(DiaryFailure.class,()->reader.report(OWNER,"ai",deletedReport)).status());
    }
    @Test void pooledConnectionHasNoLeakedOwnerAndReaderNeverInitializesAiProfiles() {
        UUID entry=entry();UUID report=report(entry,false);
        assertEquals(0,reader.reports(OWNER+1,"ai",1).total());
        assertEquals(404,assertThrows(DiaryFailure.class,()->reader.report(OWNER+1,"ai",report)).status());
        assertEquals(0,new JdbcTemplate(pool).queryForObject("SELECT count(*) FROM diary.entry",Integer.class));
        assertEquals(0,admin.queryForObject("SELECT count(*) FROM diary_ai.embedding_profile WHERE owner_id=?",Integer.class,OWNER));
        assertEquals(0,admin.queryForObject("SELECT count(*) FROM diary_ai.usage_budget WHERE owner_id=?",Integer.class,OWNER));
        assertEquals(0,reader.coaching(OWNER,"goals",1).total());
        assertEquals(400,assertThrows(DiaryFailure.class,()->reader.reports(OWNER,"ai",0)).status());
    }
    @Test void periodGoalActionAndFeedbackSourcesAreVerifiedAndDeletedFeedbackHidesWholeReport() {
        long owner=OWNER+222;
        UUID goal=UUID.randomUUID(),action=UUID.randomUUID(),feedback=UUID.randomUUID(),job=UUID.randomUUID(),id=UUID.randomUUID();
        String goalJson="{\"title\":\"合成目标\",\"state\":\"ACTIVE\"}";
        String actionJson="{\"text\":\"合成行动\",\"state\":\"PLANNED\"}";
        String feedbackJson="{\"execution\":\"TRIED\",\"effect\":\"HELPFUL\",\"note\":\"合成反馈\"}";
        admin.update("INSERT INTO diary_coaching.goal(owner_id,id,revision,data) VALUES(?,?,1,CAST(? AS jsonb))",owner,goal,goalJson);
        admin.update("INSERT INTO diary_coaching.action(owner_id,id,parent_id,revision,data) VALUES(?,?,?,1,CAST(? AS jsonb))",owner,action,goal,actionJson);
        admin.update("INSERT INTO diary_coaching.feedback(owner_id,id,parent_id,revision,data) VALUES(?,?,?,1,CAST(? AS jsonb))",owner,feedback,action,feedbackJson);
        admin.update("INSERT INTO diary_coaching.period_job(owner_id,id,request_id,request_hash,state,payload) VALUES(?,?,?,?,'SUCCEEDED','{}')",owner,job,UUID.randomUUID(),"synthetic-hash");
        admin.update("INSERT INTO diary_coaching.period_report(owner_id,id,job_id,state,result,scope,model,prompt_version) VALUES(?,?,?,'SUCCEEDED','{\"coverage\":\"合成复盘\"}','{}','fixture','fixture')",owner,id,job);
        var entities=List.of(goal,action,feedback);var kinds=List.of("G","A","F");var contents=List.of(goalJson,actionJson,feedbackJson);
        for(int i=0;i<3;i++) {
            String canonical=xyz.luyu.diary.coaching.CoachingModels.canonical(com.alibaba.fastjson2.JSON.parseObject(contents.get(i)));
            admin.update("INSERT INTO diary_coaching.period_source VALUES(?,?,?,?,?,1,'content',0,?,?)",owner,id,kinds.get(i)+"1",kinds.get(i),entities.get(i),canonical.codePointCount(0,canonical.length()),DiaryAiText.hash(canonical));
        }
        assertEquals(3,reader.report(owner,"period",id).sources().size());
        assertEquals(1,reader.coaching(owner,"goals",1).total());
        assertEquals(1,reader.coaching(owner,"actions",1).total());
        assertEquals(1,reader.coaching(owner,"feedback",1).total());
        admin.update("UPDATE diary_coaching.feedback SET deleted_at=now(),revision=2 WHERE owner_id=? AND id=?",owner,feedback);
        assertEquals(0,reader.coaching(owner,"feedback",1).total());
        assertEquals(0,reader.reports(owner,"period",1).total());
        assertEquals(404,assertThrows(DiaryFailure.class,()->reader.report(owner,"period",id)).status());
        assertEquals("SUCCEEDED",admin.queryForObject("SELECT state FROM diary_coaching.period_report WHERE owner_id=? AND id=?",String.class,owner,id));
    }
}
