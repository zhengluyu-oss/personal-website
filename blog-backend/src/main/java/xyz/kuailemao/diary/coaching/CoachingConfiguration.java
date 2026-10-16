package xyz.kuailemao.diary.coaching;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.kuailemao.diary.ai.DiaryAiStore;
import xyz.kuailemao.diary.ai.DiaryAiCloud;
import xyz.kuailemao.diary.ai.DiaryAiProperties;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(CoachingProperties.class)
public class CoachingConfiguration {
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name={"diary.enabled","diary.coaching.enabled"},havingValue="true")
    static class Enabled {
        @Bean CoachingStore coachingStore(@Qualifier("diaryJdbcTemplate") NamedParameterJdbcTemplate jdbc,
                                          @Qualifier("diaryTransactionManager") DataSourceTransactionManager tx) { return new CoachingStore(jdbc,tx); }
        @Bean CoachingPeriods coachingPeriods(CoachingStore store) { return new CoachingPeriods(store); }
        @Bean CoachingOrigins coachingOrigins(ObjectProvider<DiaryAiStore> ai,CoachingStore store,ObjectProvider<CoachingPeriodAi> periods) { return new CoachingOrigins(ai,store,periods); }
        @Bean
        @ConditionalOnProperty(name="diary.ai.enabled",havingValue="true")
        CoachingPeriodAi coachingPeriodAi(CoachingStore store,CoachingPeriods periods,CoachingProperties options,DiaryAiStore ai,DiaryAiCloud cloud,DiaryAiProperties properties) {
            return new CoachingPeriodAi(store,periods,options,ai,cloud,properties);
        }
    }
}
