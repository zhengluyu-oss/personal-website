package xyz.luyu.knowledge;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

@Configuration(proxyBeanMethods=false) @EnableConfigurationProperties(KnowledgeProperties.class)
public class KnowledgeDataConfiguration {
    @Configuration(proxyBeanMethods=false) @ConditionalOnProperty(name="knowledge.enabled",havingValue="true")
    static class Enabled {
        @Bean(name="knowledgeDataSource",defaultCandidate=false,destroyMethod="close")
        HikariDataSource dataSource(KnowledgeProperties p){
            var pool=new HikariDataSource();pool.setJdbcUrl(p.configured()?p.getPostgres().getUrl():"jdbc:postgresql://127.0.0.1:1/disabled");
            pool.setUsername(p.getPostgres().getUsername());pool.setPassword(p.getPostgres().getPassword());pool.setPoolName("knowledge");
            pool.setMaximumPoolSize(2);pool.setMinimumIdle(0);pool.setInitializationFailTimeout(-1);pool.setConnectionTimeout(1500);pool.setValidationTimeout(1000);
            pool.addDataSourceProperty("connectTimeout","2");pool.addDataSourceProperty("socketTimeout","8");pool.addDataSourceProperty("options","-c statement_timeout=5000 -c lock_timeout=2000");return pool;
        }
        @Bean(name="knowledgeJdbcTemplate",defaultCandidate=false)
        NamedParameterJdbcTemplate jdbc(@Qualifier("knowledgeDataSource") HikariDataSource pool){var jdbc=new PrivateJdbc(pool);jdbc.setQueryTimeout(5);jdbc.setFetchSize(20);return new NamedParameterJdbcTemplate(jdbc);}
        @Bean(name="knowledgeTransactionManager",defaultCandidate=false)
        DataSourceTransactionManager tx(@Qualifier("knowledgeDataSource") HikariDataSource pool){var tx=new DataSourceTransactionManager(pool);tx.setRollbackOnCommitFailure(true);return tx;}
        @Bean KnowledgeStore knowledgeStore(@Qualifier("knowledgeJdbcTemplate") NamedParameterJdbcTemplate jdbc,@Qualifier("knowledgeTransactionManager") DataSourceTransactionManager tx){return new KnowledgeStore(jdbc,tx);}
    }
    static final class PrivateJdbc extends JdbcTemplate {PrivateJdbc(HikariDataSource pool){super(pool);}}
}
