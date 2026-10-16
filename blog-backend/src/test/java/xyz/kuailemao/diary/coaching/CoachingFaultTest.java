package xyz.kuailemao.diary.coaching;

import com.alibaba.fastjson2.JSONObject;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.DefaultTransactionStatus;
import xyz.kuailemao.diary.*;
import java.sql.*;
import java.util.*;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLOG_DIARY_AI_QA",matches="true")
class CoachingFaultTest {
    HikariDataSource pool;NamedParameterJdbcTemplate jdbc;long owner;
    @BeforeEach void setup() { pool=new HikariDataSource();pool.setJdbcUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");pool.setUsername("diary_runtime");pool.setPassword("isolated-diary-runtime");pool.setMaximumPoolSize(3);pool.setMinimumIdle(0);pool.setConnectionTimeout(1500);jdbc=new NamedParameterJdbcTemplate(pool);owner=(UUID.randomUUID().getLeastSignificantBits()&0x0000ffffffffffffL)+1000; }
    @AfterEach void close() { pool.close(); }
    Write request() { return new Write(UUID.randomUUID(),UUID.randomUUID(),0,JSONObject.of("title","fictional commit probe","state","ACTIVE"),null,null); }
    @Test void commitFailureRollsBackContentHistoryAndMutationInsteadOfImplicitCommit() {
        var failed=new DataSourceTransactionManager(pool) { @Override protected void doCommit(DefaultTransactionStatus status) { throw new TransactionSystemException("synthetic commit failure"); } };
        var store=new CoachingStore(jdbc,failed);var request=request();
        assertEquals(503,assertThrows(DiaryFailure.class,()->store.write(owner,Kind.GOAL,request,false,(o,s)->{})).status());
        var normal=new CoachingStore(jdbc,new DataSourceTransactionManager(pool));assertEquals(0,normal.list(owner,Kind.GOAL,null,null,1,20).total());
        var ack=normal.write(owner,Kind.GOAL,request,false,(o,s)->{});assertEquals(1,ack.revision());assertEquals(ack,normal.write(owner,Kind.GOAL,request,false,(o,s)->{}));
    }
    @Test void historyFailureCannotLeaveCurrentRowOrReceiptAndRecoveryCanRetry() throws Exception {
        var store=new CoachingStore(jdbc,new DataSourceTransactionManager(pool));var request=request();String function="coaching_qa_"+owner;
        try(var admin=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:15432/diary_qa","postgres","isolated-diary-admin")) {
            try(var statement=admin.createStatement()) { statement.execute("CREATE FUNCTION diary_coaching."+function+"() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.owner_id="+owner+" THEN RAISE EXCEPTION 'synthetic history failure'; END IF; RETURN NEW; END $$");statement.execute("CREATE TRIGGER "+function+" BEFORE INSERT ON diary_coaching.goal_revision FOR EACH ROW EXECUTE FUNCTION diary_coaching."+function+"()"); }
            try { assertEquals(503,assertThrows(DiaryFailure.class,()->store.write(owner,Kind.GOAL,request,false,(o,s)->{})).status());assertEquals(0,store.list(owner,Kind.GOAL,null,null,1,20).total()); }
            finally { try(var statement=admin.createStatement()) { statement.execute("DROP TRIGGER "+function+" ON diary_coaching.goal_revision");statement.execute("DROP FUNCTION diary_coaching."+function+"()"); } }
        }
        assertEquals(1,store.write(owner,Kind.GOAL,request,false,(o,s)->{}).revision());
    }
    @Test void exhaustedPrivatePoolDoesNotTakeMysqlDownAndRecovers() throws Exception {
        var store=new CoachingStore(jdbc,new DataSourceTransactionManager(pool));assertTrue(store.available(owner));
        try(var a=pool.getConnection();var b=pool.getConnection();var c=pool.getConnection()) {
            long begin=System.nanoTime();assertFalse(store.available(owner));assertTrue((System.nanoTime()-begin)/1_000_000<5000);
            var mysql=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:15433/diary_mysql_qa","root","isolated-mysql-admin"));assertEquals(1,mysql.queryForObject("SELECT 1",Integer.class));
        }
        assertTrue(store.available(owner));
    }
}
