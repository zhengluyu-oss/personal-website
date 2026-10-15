package xyz.kuailemao.diary;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import xyz.kuailemao.config.MybatisPlusConfig;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.UserMapper;
import javax.sql.DataSource;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLOG_DIARY_QA",matches="true")
class DiaryDualDatabaseTest {
    @Test void originalArticlePaginationAccountReadAndMysqlRollbackRemainIndependentFromPg() {
        new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(DiaryDataConfiguration.class,MybatisPlusConfig.class)
                .withPropertyValues("spring.datasource.url=jdbc:mysql://127.0.0.1:15433/diary_mysql_qa?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                        "spring.datasource.username=root","spring.datasource.password=isolated-mysql-admin",
                        "diary.enabled=true","diary.owner-user-id=7","diary.url=jdbc:postgresql://127.0.0.1:15432/diary_qa",
                        "diary.password=isolated-diary-runtime")
                .run(context->{
                    assertNull(context.getStartupFailure());
                    var mysql=context.getBean("dataSource",DataSource.class);var jdbc=new JdbcTemplate(mysql);
                    assertEquals("diary_mysql_qa",jdbc.queryForObject("SELECT DATABASE()",String.class));
                    jdbc.execute("CREATE TABLE IF NOT EXISTS t_article(id BIGINT PRIMARY KEY,article_title VARCHAR(120),is_deleted INT)");
                    jdbc.execute("CREATE TABLE IF NOT EXISTS sys_user(id BIGINT PRIMARY KEY,username VARCHAR(64))");
                    jdbc.update("INSERT INTO t_article VALUES(1,'synthetic article A',0),(2,'synthetic article B',0),(3,'synthetic article C',0) ON DUPLICATE KEY UPDATE is_deleted=0");
                    jdbc.update("INSERT INTO sys_user VALUES(7,'synthetic owner') ON DUPLICATE KEY UPDATE username='synthetic owner'");
                    var articles=context.getBean(ArticleMapper.class).selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<Article>(1,2),
                            new QueryWrapper<Article>().select("id","article_title").eq("is_deleted",0).orderByAsc("id"));
                    assertEquals(3,articles.getTotal());assertEquals(2,articles.getRecords().size());
                    assertEquals("synthetic owner",context.getBean(UserMapper.class).selectOne(new QueryWrapper<User>().select("id","username").eq("id",7)).getUsername());
                    DiaryStore pg=context.getBean(DiaryStore.class);assertTrue(pg.available(7007));
                    UUID id=UUID.randomUUID();var write=new DiaryModels.Write(id,UUID.randomUUID(),0,"synthetic","separate PostgreSQL commit",null,null);
                    var tx=new TransactionTemplate(context.getBean("transactionManager",DataSourceTransactionManager.class));
                    tx.execute(status->{jdbc.update("UPDATE sys_user SET username='must rollback' WHERE id=7");pg.create(7007,write);status.setRollbackOnly();return null;});
                    assertEquals("synthetic owner",jdbc.queryForObject("SELECT username FROM sys_user WHERE id=7",String.class));
                    assertEquals("separate PostgreSQL commit",pg.read(7007,id).body());
                    jdbc.update("UPDATE sys_user SET username='mysql committed' WHERE id=7");
                    assertThrows(DiaryFailure.class,()->pg.save(7007,id,new DiaryModels.Write(id,UUID.randomUUID(),999,"stale","stale",null,null)));
                    assertEquals("mysql committed",jdbc.queryForObject("SELECT username FROM sys_user WHERE id=7",String.class));
                    assertEquals(1,pg.read(7007,id).revision());
                });
    }
}
