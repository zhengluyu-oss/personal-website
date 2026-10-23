package xyz.luyu.diary;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(DiaryProperties.class)
public class DiaryDataConfiguration {
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name="diary.enabled",havingValue="true")
    static class Enabled {
        @Bean(name="diaryDataSource", defaultCandidate=false, destroyMethod="close")
        HikariDataSource diaryDataSource(DiaryProperties p) {
            // 懒启动；缺失配置时数据访问在资格边界拒绝，不尝试连接占位地址。
            HikariDataSource pool = new HikariDataSource();
            pool.setJdbcUrl(p.configured() ? p.getUrl() : "jdbc:postgresql://127.0.0.1:1/disabled");
            pool.setUsername(p.getUsername()); pool.setPassword(p.getPassword());
            pool.setPoolName("diary"); pool.setMaximumPoolSize(3); pool.setMinimumIdle(0);
            pool.setConnectionTimeout(1500); pool.setValidationTimeout(1000);
            pool.setInitializationFailTimeout(-1);
            pool.addDataSourceProperty("connectTimeout","2");
            pool.addDataSourceProperty("socketTimeout","8");
            pool.addDataSourceProperty("options","-c statement_timeout=5000 -c lock_timeout=2000");
            return pool;
        }
        @Bean(name="diaryJdbcTemplate", defaultCandidate=false)
        NamedParameterJdbcTemplate diaryJdbcTemplate(@Qualifier("diaryDataSource") HikariDataSource pool) {
            var jdbc = new DiaryJdbcTemplate(pool);
            jdbc.setQueryTimeout(5);
            jdbc.setFetchSize(20);
            return new NamedParameterJdbcTemplate(jdbc);
        }
        @Bean(name="diaryTransactionManager", defaultCandidate=false)
        DataSourceTransactionManager diaryTransactionManager(@Qualifier("diaryDataSource") HikariDataSource pool) {
            return new DataSourceTransactionManager(pool);
        }
        @Bean
        DiaryStore diaryStore(@Qualifier("diaryJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                              @Qualifier("diaryTransactionManager") DataSourceTransactionManager tx) {
            return new DiaryStore(jdbc,tx);
        }
        @Bean
        DiaryReadStore diaryReadStore(@Qualifier("diaryJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                     @Qualifier("diaryTransactionManager") DataSourceTransactionManager tx) {
            return new DiaryReadStore(jdbc,tx);
        }
    }
    // 独立日志名称，避免普通JdbcTemplate调试日志输出日记SQL参数。
    static final class DiaryJdbcTemplate extends JdbcTemplate {
        DiaryJdbcTemplate(HikariDataSource pool) { super(pool); }
    }
}
