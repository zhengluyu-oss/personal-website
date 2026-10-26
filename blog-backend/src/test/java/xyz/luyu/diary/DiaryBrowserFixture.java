package xyz.luyu.diary;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import xyz.luyu.domain.entity.LoginUser;
import xyz.luyu.domain.entity.User;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.filter.JwtAuthorizeFilter;
import xyz.luyu.utils.JwtUtils;
import java.util.*;
import static org.mockito.Mockito.*;

/** 只打包进test-classes，本机虚构身份+真实PG的浏览器验收入口。 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,DataSourceTransactionManagerAutoConfiguration.class,MybatisPlusAutoConfiguration.class})
@Import({DiaryModuleAccess.class,DiaryReadController.class,DiaryDataConfiguration.class,DiaryController.class,DiaryService.class,DiarySecurityConfiguration.class,DiaryExceptionHandler.class,DiaryBrowserFixture.PublicFixtures.class})
public class DiaryBrowserFixture {
    @Bean xyz.luyu.service.SiteModuleAccessService modules(){ var modules=mock(xyz.luyu.service.SiteModuleAccessService.class);when(modules.canView("diary")).thenReturn(true);return modules; }

    @Bean JwtAuthorizeFilter jwtAuthorizeFilter(){return new JwtAuthorizeFilter();}
    @Bean UserDetailsService fixtureUsers(){return username->{throw new UsernameNotFoundException("fixture-only");};}
    public static void main(String[] args) {
        if(!"true".equals(System.getenv("BLOG_DIARY_QA"))) throw new IllegalStateException("Explicit local diary QA opt-in required");
        var jwt=mock(JwtUtils.class);var owner=mock(DecodedJWT.class);var other=mock(DecodedJWT.class);
        when(jwt.resolveJwt("Bearer owner-fixture")).thenReturn(owner);when(jwt.resolveJwt("Bearer other-admin-fixture")).thenReturn(other);
        when(jwt.toUser(owner)).thenReturn(new LoginUser(User.builder().id(7L).build(),List.of("ROLE_ADMIN")));
        when(jwt.toUser(other)).thenReturn(new LoginUser(User.builder().id(8L).build(),List.of("ROLE_ADMIN")));
        var app=new SpringApplication(DiaryBrowserFixture.class);
        app.addInitializers(context->context.addBeanFactoryPostProcessor(factory->factory.registerSingleton("jwtUtils",jwt)));
        app.run("--server.address=127.0.0.1","--server.port=18002","--server.ssl.enabled=false",
                "--spring.config.location=classpath:/application.yml","--spring.profiles.active=diary-qa",
                "--spring.quartz.auto-startup=false","--diary.enabled=true","--diary.owner-user-id=7",
                "--diary.url=jdbc:postgresql://127.0.0.1:15432/"+("true".equals(System.getenv("BLOG_DIARY_RESTORE_QA"))?"diary_restore_java_qa":"diary_qa"),"--diary.username=diary_runtime",
                "--diary.password=isolated-diary-runtime","--logging.level.root=INFO",
                "--logging.level.org.springframework.web=INFO","--logging.level.xyz.luyu.diary=OFF",
                "--logging.level.org.springframework.jdbc.core.StatementCreatorUtils=OFF");
    }
    @RestController static class PublicFixtures {
        @GetMapping("/user/auth/info") Object user(@RequestHeader(value="Authorization",defaultValue="") String token){return ResponseResult.success(Map.of("username",token.contains("other-admin")?"other-fixture":"owner-fixture","nickname","本地虚构账号","roles",List.of("ROLE_ADMIN"),"avatar","","email","fixture@example.invalid","registerType",0));}
        @GetMapping("/websiteInfo/front") Object site(){return ResponseResult.success(Map.of("websiteName","本地日记验收","webmasterAvatar","","recordNumber",""));}
        @GetMapping("/site-modules/access") Object access(){return ResponseResult.success(List.of("home","experience","blog","shares","photos","about","diary").stream().map(key->Map.of("key",key,"name",key,"allowed",true,"publicAccess",true)).toList());}
        @GetMapping({"/category/list","/category/front/list","/banners/list"}) Object empty(){return ResponseResult.success(List.of());}
    }
}
