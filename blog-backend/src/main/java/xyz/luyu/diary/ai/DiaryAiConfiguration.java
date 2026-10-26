package xyz.luyu.diary.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import xyz.luyu.diary.DiaryProperties;
import xyz.luyu.mapper.UserMapper;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(DiaryAiProperties.class)
public class DiaryAiConfiguration {
    @Configuration(proxyBeanMethods=false)
    @ConditionalOnProperty(name={"diary.enabled","diary.ai.enabled"},havingValue="true")
    static class Enabled {
        @Bean DiaryAiStore diaryAiStore(@Qualifier("diaryJdbcTemplate") NamedParameterJdbcTemplate jdbc,@Qualifier("diaryTransactionManager") DataSourceTransactionManager tx,DiaryAiProperties p,ObjectProvider<DiaryAiExtension> extensions){return new DiaryAiStore(jdbc,tx,p,extensions::getIfAvailable);}
        @Bean DiaryAiCloud diaryAiCloud(DiaryAiProperties p){return new DiaryAiCloud(p);}
        @Bean DiaryAiIndex diaryAiIndex(DiaryAiStore s,DiaryAiCloud c,DiaryAiProperties p){return new DiaryAiIndex(s,c,p);}
        @Bean DiaryAiWorker diaryAiWorker(DiaryAiStore s,DiaryAiIndex i,DiaryAiCloud c,DiaryAiProperties p,DiaryProperties diary,UserMapper users,ObjectProvider<DiaryAiExtension> extensions){
            long owner=diary.getOwnerUserId()==null?0:diary.getOwnerUserId();
            return new DiaryAiWorker(s,i,c,p,owner,()->{var u=users.selectById(owner);return u!=null && Integer.valueOf(0).equals(u.getIsDisable()) && Integer.valueOf(0).equals(u.getIsDeleted());},extensions::getIfAvailable);
        }
    }
}
