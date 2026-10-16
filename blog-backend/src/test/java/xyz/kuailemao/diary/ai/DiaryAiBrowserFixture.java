package xyz.kuailemao.diary.ai;

import com.alibaba.fastjson2.JSON;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.*;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import xyz.kuailemao.diary.*;
import xyz.kuailemao.domain.entity.*;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.filter.JwtAuthorizeFilter;
import xyz.kuailemao.utils.JwtUtils;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.mockito.Mockito.*;
import static xyz.kuailemao.diary.ai.DiaryAiModels.*;

/** test-classes only; actual browser -> Spring security -> PG -> controlled HTTP. */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude={DataSourceAutoConfiguration.class,DataSourceTransactionManagerAutoConfiguration.class,MybatisPlusAutoConfiguration.class})
@Import({DiaryDataConfiguration.class,DiaryController.class,DiaryService.class,DiarySecurityConfiguration.class,DiaryExceptionHandler.class,DiaryAiController.class,DiaryAiService.class,DiaryAiExceptionHandler.class,DiaryAiBrowserFixture.PublicFixtures.class})
public class DiaryAiBrowserFixture {
    @Bean JwtAuthorizeFilter filter(){return new JwtAuthorizeFilter();}
    @Bean UserDetailsService users(){return username->{throw new UsernameNotFoundException("fixture-only");};}
    @Bean DiaryAiProperties ai(){var p=spy(new DiaryAiProperties());p.setEnabled(true);p.setProvider("fictional-http");p.setDimensions(3);p.setEmbeddingModel("fixture-vector");p.setGenerationModel("fixture-generation");p.setMinimumSimilarity(0.8);p.setEmbeddingEndpoint("http://127.0.0.1:18004/fixture");p.setGenerationEndpoint(p.getEmbeddingEndpoint());p.setEmbeddingKey("fictional-fixture-only");p.setGenerationKey("fictional-fixture-only");p.setDailyEmbeddingBlocks(10000);p.setDailyGenerations(1000);doReturn(true).when(p).configured();return p;}
    @Bean DiaryAiStore store(@Qualifier("diaryJdbcTemplate") NamedParameterJdbcTemplate jdbc,@Qualifier("diaryTransactionManager") DataSourceTransactionManager tx,DiaryAiProperties p){return new DiaryAiStore(jdbc,tx,p);}
    @Bean DiaryAiCloud cloud(DiaryAiProperties p){return new DiaryAiCloud(p,HttpClient.newHttpClient(),uri->{});}
    @Bean DiaryAiIndex index(DiaryAiStore s,DiaryAiCloud c,DiaryAiProperties p){return new DiaryAiIndex(s,c,p);}
    @Bean DiaryAiWorker worker(DiaryAiStore s,DiaryAiIndex i,DiaryAiCloud c,DiaryAiProperties p){return new DiaryAiWorker(s,i,c,p,7,()->true);}
    @Bean(destroyMethod="stop") ControlledServer controlledServer() throws Exception {return new ControlledServer();}
    static final class ControlledServer {
        final HttpServer server;
        ControlledServer() throws Exception {
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",18004),0);
            server.createContext("/fixture",e->{
                var body=JSON.parseObject(new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));String response;
                if(body.containsKey("input")){var data=new ArrayList<Map<String,Object>>();for(int n=0;n<body.getJSONArray("input").size();n++)data.add(Map.of("index",n,"embedding",List.of(1,0,0)));response=JSON.toJSONString(Map.of("data",data));}
                else {
                    var input=JSON.parseObject(body.getJSONArray("messages").getJSONObject(1).getString("content"));var s=input.getJSONArray("sources").getJSONObject(0);
                    String text=s.getString("text"),quote=slice(text,0,Math.min(30,points(text)));
                    var statement=new Statement("虚构夹具回答 <img src=https://fixture-blocked.invalid/private onerror=alert(1)>",List.of(s.getString("sourceId")),List.of(new Quote(s.getString("sourceId"),quote)));
                    boolean question=!input.getString("question").isBlank();var result=new Result(question?statement:null,question?null:statement,List.of(statement),List.of(),List.of(),List.of("只是受控 HTTP 链路夹具，不代表模型质量；跨日记录不能当作每天都有记录"),false);
                    response=JSON.toJSONString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",JSON.toJSONString(result))))));
                }
                byte[] bytes=response.getBytes(StandardCharsets.UTF_8);try{e.sendResponseHeaders(200,bytes.length);e.getResponseBody().write(bytes);}finally{e.close();}
            });server.start();
        }
        public void stop(){server.stop(0);}
    }
    public static void main(String[] ignored) {
        if(!"true".equals(System.getenv("BLOG_DIARY_AI_QA")))throw new IllegalStateException("Explicit isolated AI fixture opt-in required");
        var jwt=mock(JwtUtils.class);var owner=mock(DecodedJWT.class);var other=mock(DecodedJWT.class);
        when(jwt.resolveJwt("Bearer owner-fixture")).thenReturn(owner);when(jwt.resolveJwt("Bearer other-admin-fixture")).thenReturn(other);
        when(jwt.toUser(owner)).thenReturn(new LoginUser(User.builder().id(7L).build(),List.of("ROLE_ADMIN")));when(jwt.toUser(other)).thenReturn(new LoginUser(User.builder().id(8L).build(),List.of("ROLE_ADMIN")));
        var app=new SpringApplication(DiaryAiBrowserFixture.class);app.addInitializers(c->c.addBeanFactoryPostProcessor(f->f.registerSingleton("jwtUtils",jwt)));
        app.run("--server.address=127.0.0.1","--server.port=18002","--server.ssl.enabled=false","--spring.config.location=classpath:/application.yml","--spring.profiles.active=diary-qa","--spring.quartz.auto-startup=false","--diary.enabled=true","--diary.owner-user-id=7","--diary.url=jdbc:postgresql://127.0.0.1:15432/diary_qa","--diary.username=diary_runtime","--diary.password=isolated-diary-runtime","--logging.level.root=INFO","--logging.level.xyz.kuailemao.diary=OFF","--logging.level.org.springframework.jdbc.core.StatementCreatorUtils=OFF");
    }
    @RestController static class PublicFixtures {
        @GetMapping("/user/auth/info") Object user(@RequestHeader(value="Authorization",defaultValue="") String token){return ResponseResult.success(Map.of("username",token.contains("other-admin")?"other-fixture":"owner-fixture","nickname","本地虚构账号","roles",List.of("ROLE_ADMIN"),"avatar","","email","fixture@example.invalid","registerType",0));}
        @GetMapping("/websiteInfo/front") Object site(){return ResponseResult.success(Map.of("websiteName","本地日记AI验收","webmasterAvatar","","recordNumber",""));}
        @GetMapping("/site-modules/access") Object access(){return ResponseResult.success(List.of("home","experience","blog","shares","photos","about").stream().map(key->Map.of("key",key,"name",key,"allowed",true,"publicAccess",true)).toList());}
        @GetMapping({"/category/list","/category/front/list","/banners/list"}) Object empty(){return ResponseResult.success(List.of());}
    }
}
