package xyz.luyu.diary;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import xyz.luyu.domain.entity.LoginUser;
import xyz.luyu.domain.entity.User;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.utils.AuditDataProtection;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named="BLOG_DIARY_QA",matches="true")
class DiaryFaultTest {
    @Test void exhaustedPgPoolFailsBoundedlyWithoutTakingMysqlDownAndRecovers() throws Exception {
        var p=new DiaryProperties();p.setEnabled(true);p.setOwnerUserId(7L);
        p.setUrl("jdbc:postgresql://127.0.0.1:15432/diary_qa");p.setPassword("isolated-diary-runtime");
        var config=new DiaryDataConfiguration.Enabled();
        try(var pool=config.diaryDataSource(p)) {
            var store=config.diaryStore(config.diaryJdbcTemplate(pool),config.diaryTransactionManager(pool));
            assertTrue(store.available(7));
            var beans=new DefaultListableBeanFactory();beans.registerSingleton("diaryStore",store);
            var mvc=MockMvcBuilders.standaloneSetup(new DiaryController(new DiaryService(p,beans.getBeanProvider(DiaryStore.class))))
                    .setControllerAdvice(new DiaryExceptionHandler()).build();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    new LoginUser(User.builder().id(7L).build(),List.of("ROLE_ADMIN")),null,List.of()));
            try(var a=pool.getConnection();var b=pool.getConnection();var c=pool.getConnection()) {
                long started=System.nanoTime();
                mvc.perform(get("/diary/auth/entries/"+UUID.randomUUID()))
                        .andExpect(status().isServiceUnavailable()).andExpect(header().string("Cache-Control","private, no-store"));
                assertTrue((System.nanoTime()-started)/1_000_000<5000,"Pool exhaustion must be bounded");
                var mysql=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:15433/diary_mysql_qa","root","isolated-mysql-admin"));
                assertEquals(1,mysql.queryForObject("SELECT 1",Integer.class));
            } finally {SecurityContextHolder.clearContext();}
            assertTrue(store.available(7));
        }
    }
    @Test void auditProjectionCannotSerializeDiaryTextEvenIfAccidentallyInvoked() {
        String secret="diary-private-log-probe-"+UUID.randomUUID();
        var request=new DiaryModels.Write(UUID.randomUUID(),UUID.randomUUID(),0,secret,secret,null,null);
        assertFalse(AuditDataProtection.request(new Object[]{request},DiaryController.class.getName(),"create").contains(secret));
        assertFalse(AuditDataProtection.response(ResponseResult.success(request)).contains(secret));
        for(var method:DiaryController.class.getDeclaredMethods())
            assertTrue(Arrays.stream(method.getAnnotations()).noneMatch(a->a.annotationType().getSimpleName().equals("LogAnnotation")));
    }
}
