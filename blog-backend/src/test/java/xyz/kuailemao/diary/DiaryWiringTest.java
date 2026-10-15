package xyz.kuailemao.diary;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.kuailemao.config.MybatisPlusConfig;
import javax.sql.DataSource;
import static org.junit.jupiter.api.Assertions.*;

class DiaryWiringTest {
    @Configuration(proxyBeanMethods=false)
    static class ProbeConfiguration {
        @Bean Probe probe(DataSource dataSource) { return new Probe(dataSource); }
    }
    record Probe(DataSource source) {}
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class,MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(DiaryDataConfiguration.class,MybatisPlusConfig.class,ProbeConfiguration.class)
                .withPropertyValues("spring.datasource.url=jdbc:mysql://127.0.0.1:1/diary_wiring_only",
                        "spring.datasource.username=synthetic","spring.datasource.password=synthetic");
    }
    @Test void disabledDoesNotCreatePgPoolAndPreservesMysqlBinding() {
        runner().run(context->{
            assertNull(context.getStartupFailure());assertFalse(context.containsBean("diaryDataSource"));
            DataSource mysql=context.getBean("dataSource",DataSource.class);
            assertSame(mysql,context.getBean(Probe.class).source());
            assertSame(mysql,context.getBean(org.apache.ibatis.session.SqlSessionFactory.class).getConfiguration().getEnvironment().getDataSource());
            assertSame(mysql,context.getBean("transactionManager",DataSourceTransactionManager.class).getDataSource());
        });
    }
    @Test void enabledPgIsNotDefaultCandidateAndDoesNotConnectDuringStartup() {
        runner().withPropertyValues("diary.enabled=true","diary.owner-user-id=7",
                "diary.url=jdbc:postgresql://127.0.0.1:1/diary_wiring_only","diary.password=synthetic")
                .run(context->{
                    assertNull(context.getStartupFailure());DataSource mysql=context.getBean("dataSource",DataSource.class);
                    HikariDataSource pg=context.getBean("diaryDataSource",HikariDataSource.class);
                    assertNotSame(mysql,pg);assertNull(pg.getHikariPoolMXBean());
                    assertSame(mysql,context.getBean(Probe.class).source());
                    assertSame(mysql,context.getBean(org.apache.ibatis.session.SqlSessionFactory.class).getConfiguration().getEnvironment().getDataSource());
                    assertSame(mysql,context.getBean("transactionManager",DataSourceTransactionManager.class).getDataSource());
                    assertSame(pg,context.getBean("diaryTransactionManager",DataSourceTransactionManager.class).getDataSource());
                });
    }
    @Test void missingDiaryConfigurationDoesNotBreakMysqlStartup() {
        runner().withPropertyValues("diary.enabled=true").run(context->{
            assertNull(context.getStartupFailure());assertFalse(context.getBean(DiaryProperties.class).configured());
            assertNull(context.getBean("diaryDataSource",HikariDataSource.class).getHikariPoolMXBean());
        });
    }
    @Test void unicodeAndCoverageValidationUseCodePoints() {
        new DiaryModels.Content("🙂".repeat(120),"🙂".repeat(100000),null,null).validate();
        assertThrows(DiaryFailure.class,()->new DiaryModels.Content("🙂".repeat(121),"x",null,null).validate());
        assertThrows(DiaryFailure.class,()->new DiaryModels.Content("","x",java.time.LocalDate.now(),null).validate());
    }
}
