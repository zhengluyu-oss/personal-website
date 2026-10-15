package xyz.kuailemao.diary;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static xyz.kuailemao.diary.DiaryModels.*;

/** 显式启动专属本机fixture后运行，URL与凭据均为虚构测试值，不读生产配置。 */
@EnabledIfEnvironmentVariable(named="BLOG_DIARY_QA",matches="true")
class DiaryStoreIntegrationTest {
    private static HikariDataSource pool;
    private static DiaryStore store;
    private static JdbcTemplate admin;
    private static final AtomicLong OWNERS=new AtomicLong((UUID.randomUUID().getMostSignificantBits() & 0x000fffffffffffffL)+1000);
    private static final LocalDate FROM=LocalDate.of(2026,10,1),TO=LocalDate.of(2026,10,15);
    @BeforeAll static void connect() {
        var p=new DiaryProperties();p.setEnabled(true);p.setOwnerUserId(7L);
        p.setUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");p.setPassword("isolated-diary-runtime");
        var config=new DiaryDataConfiguration.Enabled();pool=config.diaryDataSource(p);
        store=config.diaryStore(config.diaryJdbcTemplate(pool),config.diaryTransactionManager(pool));
        admin=new JdbcTemplate(new DriverManagerDataSource(p.getUrl(),"postgres","isolated-diary-admin"));
        assertTrue(store.available(7));
    }
    @AfterAll static void close() { if(pool!=null) pool.close(); }
    private Write createRequest(UUID id,String body){return new Write(id,UUID.randomUUID(),0,"测试日记",body,FROM,TO);}
    private void status(int expected,Runnable operation) {assertEquals(expected,assertThrows(DiaryFailure.class,operation::run).status());}

    @Test void lostAckRetryNoOpHistoryRestoreAndTombstoneStayConsistent() {
        long owner=OWNERS.incrementAndGet();UUID id=UUID.randomUUID();Write create=createRequest(id,"第一篇🙂");
        Ack first=store.create(owner,create);assertEquals(1,first.revision());assertEquals(first,store.create(owner,create));
        status(409,()->store.create(owner,new Write(UUID.randomUUID(),create.mutationId(),0,create.title(),create.body(),FROM,TO)));
        var edit=new Write(id,UUID.randomUUID(),1,"修改","记录十几天",FROM,TO);
        Ack second=store.save(owner,id,edit);assertEquals(2,second.revision());
        status(409,()->store.save(owner,id,new Write(id,edit.mutationId(),1,edit.title(),edit.body(),FROM.plusDays(1),TO)));
        assertEquals(first,store.create(owner,create));assertEquals("记录十几天",store.read(owner,id).body());
        var noop=new Write(id,UUID.randomUUID(),2,edit.title(),edit.body(),FROM,TO);
        assertEquals(2,store.save(owner,id,noop).revision());assertEquals(2,store.history(owner,id,1).total());
        status(409,()->store.save(owner,id,new Write(id,edit.mutationId(),2,"替换摘要","other",FROM,TO)));
        status(409,()->store.save(owner,id,new Write(id,UUID.randomUUID(),1,"过期","other",FROM,TO)));
        Ack removed=store.delete(owner,id,new Change(UUID.randomUUID(),2,null));assertEquals(3,removed.revision());
        assertNotNull(store.read(owner,id).deletedAt());
        assertEquals(2,store.save(owner,id,noop).revision());assertNotNull(store.read(owner,id).deletedAt());
        status(409,()->store.save(owner,id,new Write(id,UUID.randomUUID(),2,noop.title(),noop.body(),FROM,TO)));
        assertEquals(0,store.search(owner,new Search(null,null,null,null,1,20,false)).total());
        store.restore(owner,id,new Change(UUID.randomUUID(),3,1L));
        Entry restored=store.read(owner,id);assertEquals(4,restored.revision());assertEquals("第一篇🙂",restored.body());
        assertEquals(FROM,restored.coveredFrom());assertNull(restored.deletedAt());
        assertEquals(first,store.create(owner,create));assertEquals(4,store.read(owner,id).revision());
        status(404,()->store.read(owner+999999,id));
    }

    @Test void creationAndUpdateRacesHaveOneWinnerButSameDayDifferentIdsBothSucceed() throws Exception {
        long owner=OWNERS.incrementAndGet();UUID id=UUID.randomUUID();
        var executor=Executors.newFixedThreadPool(2);
        try {
            var gate=new CountDownLatch(1);
            Callable<Integer> create=()->{gate.await();try {store.create(owner,createRequest(id,"竞争"));return 200;}catch(DiaryFailure e){return e.status();}};
            var a=executor.submit(create);var b=executor.submit(create);gate.countDown();
            assertEquals(Set.of(200,409),Set.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
            var updateGate=new CountDownLatch(1);
            Callable<Integer> update=()->{updateGate.await();try{store.save(owner,id,new Write(id,UUID.randomUUID(),1,"更新","竞争更新",FROM,TO));return 200;}catch(DiaryFailure e){return e.status();}};
            var c=executor.submit(update);var d=executor.submit(update);updateGate.countDown();
            assertEquals(Set.of(200,409),Set.of(c.get(15,TimeUnit.SECONDS),d.get(15,TimeUnit.SECONDS)));
            store.create(owner,createRequest(UUID.randomUUID(),"另篇"));
            assertEquals(2,store.search(owner,new Search(null,null,null,null,1,20,false)).total());
            assertEquals(2,store.calendar(owner,YearMonth.now(java.time.ZoneId.of("Asia/Shanghai"))).stream().mapToLong(CalendarDay::count).sum());
        } finally {executor.shutdownNow();}
    }

    @Test void overlapLiteralKeywordsUndatedEntriesAndExportsUseCorrectAxis() throws Exception {
        long owner=OWNERS.incrementAndGet();UUID id=UUID.randomUUID();
        store.create(owner,createRequest(id,"中文 %_ ' OR 1=1; 🙂"));
        store.create(owner,new Write(UUID.randomUUID(),UUID.randomUUID(),0,"无日期","another",null,null));
        var overlap=new Search("covered",FROM.plusDays(7),FROM.plusDays(8),"%_",1,20,false);
        assertEquals(1,store.search(owner,overlap).total());
        assertEquals(2,store.search(owner,new Search(null,null,null,null,1,20,false)).total());
        assertEquals(0,store.search(owner,new Search(null,null,null,"%missing",1,20,false)).total());
        var download=store.export(owner,new Export(overlap,"json"));
        var rows=com.alibaba.fastjson2.JSON.parseArray(new String(download.bytes(),java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(1,rows.size());assertEquals(id.toString(),rows.getJSONObject(0).getString("id"));
        assertTrue(rows.getJSONObject(0).getString("body").contains("中文"));
        var today=LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        var zip=store.export(owner,new Export(new Search("created",today,today,null,1,20,false),"markdown"));
        try(var input=new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(zip.bytes()))) {
            var filenames=new HashSet<String>();for(java.util.zip.ZipEntry e;(e=input.getNextEntry())!=null;){filenames.add(e.getName());input.readAllBytes();}
            assertEquals(2,filenames.size());assertTrue(filenames.contains(id+".md"));
        }
        status(400,()->store.export(owner,new Export(new Search("created",FROM,FROM.plusDays(366),null,1,20,false),"json")));
    }

    @Test void shanghaiMidnightAndLeapDayUseHalfOpenCreationWindow() {
        long owner=OWNERS.incrementAndGet();UUID before=UUID.randomUUID(),midnight=UUID.randomUUID(),after=UUID.randomUUID();
        for(UUID id:List.of(before,midnight,after)) store.create(owner,createRequest(id,"跨午夜虚构记录"));
        admin.update("UPDATE diary.entry SET created_at=TIMESTAMPTZ '2024-02-28 15:59:59+00' WHERE id=?",before);
        admin.update("UPDATE diary.entry SET created_at=TIMESTAMPTZ '2024-02-28 16:00:00+00' WHERE id=?",midnight);
        admin.update("UPDATE diary.entry SET created_at=TIMESTAMPTZ '2024-02-29 16:00:00+00' WHERE id=?",after);
        var leap=LocalDate.of(2024,2,29);
        var page=store.search(owner,new Search("created",leap,leap,null,1,20,false));
        assertEquals(1,page.total());assertEquals(midnight,page.records().get(0).id());
        var month=store.calendar(owner,YearMonth.of(2024,2));
        assertEquals(2,month.stream().mapToLong(CalendarDay::count).sum());
        assertEquals(1,month.stream().filter(day->day.date().equals(leap)).findFirst().orElseThrow().count());
    }

    @Test void historyAndCommitFailureRollBackWholeMutationAndPermitOriginalRetry() {
        long owner=OWNERS.incrementAndGet();UUID id=UUID.randomUUID();store.create(owner,createRequest(id,"original"));
        Write edit=new Write(id,UUID.randomUUID(),1,"changed","history-rollback-probe",FROM,TO);
        admin.execute("ALTER TABLE diary.entry_revision ADD CONSTRAINT qa_history_failure CHECK(body<>'history-rollback-probe' OR owner_id<>"+owner+")");
        try { status(503,()->store.save(owner,id,edit));assertEquals("original",store.read(owner,id).body()); }
        finally {admin.execute("ALTER TABLE diary.entry_revision DROP CONSTRAINT qa_history_failure");}
        admin.execute("ALTER TABLE diary.mutation ADD CONSTRAINT qa_mutation_failure CHECK(owner_id<>"+owner+" OR action<>'SAVE')");
        try {status(503,()->store.save(owner,id,edit));assertEquals("original",store.read(owner,id).body());assertEquals(1,store.history(owner,id,1).total());}
        finally {admin.execute("ALTER TABLE diary.mutation DROP CONSTRAINT qa_mutation_failure");}
        assertEquals(2,store.save(owner,id,edit).revision());assertEquals(2,store.history(owner,id,1).total());
        admin.execute("CREATE FUNCTION diary.qa_commit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.body='commit-rollback-probe' THEN RAISE EXCEPTION 'synthetic deferred failure'; END IF; RETURN NEW; END $$");
        admin.execute("CREATE CONSTRAINT TRIGGER qa_commit_failure AFTER INSERT ON diary.entry_revision DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION diary.qa_commit_failure()");
        var deferred=new Write(id,UUID.randomUUID(),2,"changed","commit-rollback-probe",FROM,TO);
        try {status(503,()->store.save(owner,id,deferred));assertEquals(2,store.read(owner,id).revision());}
        finally {admin.execute("DROP TRIGGER qa_commit_failure ON diary.entry_revision");admin.execute("DROP FUNCTION diary.qa_commit_failure()");}
        assertEquals(3,store.save(owner,id,deferred).revision());
    }

    @Test void pooledConnectionsClearTransactionIdentityAndReopenedServiceRetainsData() {
        long owner=OWNERS.incrementAndGet();UUID id=UUID.randomUUID();Write request=createRequest(id,"persistent");
        Ack ack=store.create(owner,request);
        try(var connection=pool.getConnection();var statement=connection.createStatement();var rows=statement.executeQuery("SELECT count(*) FROM diary.entry")) {
            assertTrue(rows.next());assertEquals(0,rows.getLong(1));
        } catch(java.sql.SQLException e){throw new AssertionError(e);}
        var reopened=new DiaryStore(new NamedParameterJdbcTemplate(pool),new DataSourceTransactionManager(pool));
        assertEquals(ack,reopened.create(owner,request));assertEquals("persistent",reopened.read(owner,id).body());
        assertEquals(0,reopened.search(owner+999999,new Search(null,null,null,null,1,20,false)).total());
    }
}
