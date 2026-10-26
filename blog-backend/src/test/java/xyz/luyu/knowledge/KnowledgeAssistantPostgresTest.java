package xyz.luyu.knowledge;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static xyz.luyu.knowledge.KnowledgeModels.*;

@EnabledIfEnvironmentVariable(named="BLOG_KNOWLEDGE_QA",matches="true")
class KnowledgeAssistantPostgresTest {
    private HikariDataSource pool;private KnowledgeStore knowledge;private KnowledgeAssistantStore assistant;
    private final long owner=910007L;
    @BeforeEach void setup(){
        pool=new HikariDataSource();pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15433/knowledge_qa");pool.setUsername("knowledge_runtime");pool.setPassword("isolated-knowledge-runtime");pool.setMaximumPoolSize(4);
        knowledge=new KnowledgeStore(new NamedParameterJdbcTemplate(pool),new DataSourceTransactionManager(pool));
        KnowledgeAccess access=mock(KnowledgeAccess.class);when(access.store()).thenReturn(knowledge);assistant=new KnowledgeAssistantStore(access);
        assertTrue(assistant.ready(owner));
    }
    @AfterEach void close(){pool.close();}
    @Test void quotasAndConcurrencyAreAtomicAcrossRequests()throws Exception{
        var config=new KnowledgeAssistantProperties();config.setDailyPerAccount(2);config.setDailySite(2);config.setMaxConcurrent(2);
        ExecutorService workers=Executors.newFixedThreadPool(5);
        try {var gate=new CountDownLatch(1);List<Future<Boolean>> attempts=new ArrayList<>();List<UUID> ids=new ArrayList<>();
            for(int i=0;i<5;i++){UUID id=UUID.randomUUID();ids.add(id);attempts.add(workers.submit(()->{gate.await();return assistant.reserve(owner,42,id,config);}));}
            gate.countDown();int accepted=0;for(var attempt:attempts)if(attempt.get(15,TimeUnit.SECONDS))accepted++;
            assertEquals(2,accepted);
            for(UUID id:ids)assistant.finish(owner,id,false);
            assertFalse(assistant.reserve(owner,42,UUID.randomUUID(),config));
        } finally {workers.shutdownNow();}
    }
    @Test void revisionAndDeletionInvalidateEvidence(){
        UUID id=UUID.randomUUID();var first=knowledge.save(owner,new Write(UUID.randomUUID(),id,0,new Content(Kind.NOTE,"Redis 持久化","Redis 使用 AOF 记录命令。","",null,List.of())));
        var evidence=assistant.find(owner,List.of("Redis"),4,4800);assertEquals(1,evidence.size());assertTrue(assistant.current(owner,evidence.get(0)));
        knowledge.save(owner,new Write(UUID.randomUUID(),id,first.revision(),new Content(Kind.NOTE,"Redis 持久化","Redis 使用 RDB 快照。","",null,List.of())));
        assertFalse(assistant.current(owner,evidence.get(0)));
        var newer=assistant.find(owner,List.of("Redis"),4,4800);assertEquals(1,newer.size());
        knowledge.action(owner,id,new Action(UUID.randomUUID(),newer.get(0).revision(),null),"DELETE");
        assertFalse(assistant.current(owner,newer.get(0)));assertTrue(assistant.find(owner,List.of("Redis"),4,4800).isEmpty());
    }
    @Test void onlyCurrentReadyFileSegmentsCanBecomeEvidence(){
        long fileOwner=owner+1;UUID entry=UUID.randomUUID(),version=UUID.randomUUID();
        knowledge.save(fileOwner,new Write(UUID.randomUUID(),entry,0,new Content(Kind.DOCUMENT,"合成附件","","",null,List.of())));
        knowledge.attachFile(fileOwner,entry,1,UUID.randomUUID(),version,"knowledge/private/objects/"+UUID.randomUUID(),"sample.txt","text/plain",20,"a".repeat(64),1024);
        assertTrue(assistant.find(fileOwner,List.of("可引用"),4,4800).isEmpty());
        var job=knowledge.pendingOutbox(fileOwner).get(0);var ticket=knowledge.claimJob(fileOwner,job.jobId());
        knowledge.finishJob(fileOwner,ticket,new KnowledgeParseRunner.Outcome(List.of(new KnowledgeFileParser.Extracted(0,null,1,1,"可引用的附件正文")),null));
        var evidence=assistant.find(fileOwner,List.of("可引用"),4,4800);assertEquals(1,evidence.size());assertTrue(evidence.get(0).fileSource());assertEquals(version,evidence.get(0).fileVersion());
        UUID newer=UUID.randomUUID();knowledge.attachFile(fileOwner,entry,2,UUID.randomUUID(),newer,"knowledge/private/objects/"+UUID.randomUUID(),"new.txt","text/plain",20,"b".repeat(64),1024);
        assertFalse(assistant.current(fileOwner,evidence.get(0)));
        assertTrue(assistant.find(fileOwner,List.of("可引用"),4,4800).isEmpty());
    }
}
