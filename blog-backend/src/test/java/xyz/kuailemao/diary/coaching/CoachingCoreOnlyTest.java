package xyz.kuailemao.diary.coaching;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.kuailemao.diary.*;
import xyz.kuailemao.domain.entity.*;
import java.util.*;
import static xyz.kuailemao.diary.coaching.CoachingModels.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BLOG_DIARY_COACHING_CORE_DB",matches="coaching_core_[a-f0-9]{12}")
class CoachingCoreOnlyTest {
    @Test void actual001And003WithoutAiTablesOrProviderStillSaveManualRecords() {
        String db=System.getenv("BLOG_DIARY_COACHING_CORE_DB");
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("fictional-core-qa",Map.of(
                "diary.enabled","true","diary.owner-user-id","7","diary.url","jdbc:postgresql://127.0.0.1:15432/"+db,
                "diary.username","diary_runtime","diary.password","isolated-diary-runtime","diary.coaching.enabled","true")));
            context.register(DiaryDataConfiguration.class,CoachingConfiguration.class,DiaryService.class,CoachingService.class);context.refresh();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(User.builder().id(7L).build(),List.of("ROLE_ADMIN")),null,List.of()));
            try {
                var service=context.getBean(CoachingService.class);assertTrue(service.capabilities().available());assertFalse(service.capabilities().aiAvailable());
                assertTrue(context.getBeansOfType(CoachingPeriodAi.class).isEmpty());
                UUID id=UUID.randomUUID();var ack=service.store(7).write(7,Kind.GOAL,new Write(id,UUID.randomUUID(),0,JSONObject.of("title","fictional core-only goal","state","ACTIVE"),null,null),false,service.origins(7));
                assertEquals(1,ack.revision());assertEquals("fictional core-only goal",service.store(7).read(7,Kind.GOAL,id).content().getString("title"));
                assertEquals(1,service.store(7).history(7,Kind.GOAL,id,1).total());
            } finally { SecurityContextHolder.clearContext(); }
        }
    }
}
