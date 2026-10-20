package xyz.kuailemao.knowledge;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import xyz.kuailemao.domain.entity.*;
import xyz.kuailemao.filter.JwtAuthorizeFilter;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.utils.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KnowledgeSecurityTest {
    static final UUID ID=UUID.fromString("00000000-0000-4000-8000-000000000001");
    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    @Import({KnowledgeSecurityConfiguration.class,KnowledgeAccess.class,KnowledgeController.class,KnowledgeSearchController.class,KnowledgeExceptionHandler.class})
    static class Config {
        @Bean KnowledgeProperties properties(){var p=new KnowledgeProperties();p.setEnabled(true);p.setOwnerUserId(7L);p.getPostgres().setUrl("jdbc:postgresql://127.0.0.1:1/never-connected");p.getPostgres().setPassword("synthetic-only");return p;}
        @Bean JwtAuthorizeFilter jwt(){return new JwtAuthorizeFilter();}
        @Bean KnowledgeStore store(){var s=mock(KnowledgeStore.class);when(s.available(7)).thenReturn(true);when(s.read(7,ID,false)).thenReturn(new KnowledgeModels.Entry(ID,1,new KnowledgeModels.Content(KnowledgeModels.Kind.NOTE,"合成标题","私有正文","",null,List.of()),null,Instant.EPOCH,Instant.EPOCH,null));return s;}
    }
    @Test void actualFilterChainSeparatesReadAndManageAndRejectsRevokedTokens()throws Exception{
        try(var context=new AnnotationConfigWebApplicationContext()){
            var jwt=mock(JwtUtils.class);var users=mock(UserMapper.class);var modules=mock(SiteModuleAccessService.class);
            var publicAccess=new AtomicBoolean(false);var revoked=new AtomicBoolean(false);
            when(modules.canView("knowledge")).thenAnswer(c->publicAccess.get()||!revoked.get()&&Set.of(7L,8L,9L).contains(SecurityUtils.getUserId()));
            for(long id:List.of(7L,8L,9L,10L)){
                var decoded=mock(DecodedJWT.class);var user=User.builder().id(id).isDisable(0).isDeleted(0).build();
                when(jwt.resolveJwt("Bearer fixture-"+id)).thenReturn(decoded);when(jwt.toUser(decoded)).thenReturn(new LoginUser(user,List.of(id==8?"ROLE_ADMIN":"ROLE_USER")));when(users.selectById(id)).thenReturn(user);
            }
            context.setServletContext(new MockServletContext());context.addBeanFactoryPostProcessor(f->{f.registerSingleton("jwtUtils",jwt);f.registerSingleton("users",users);f.registerSingleton("modules",modules);});context.register(Config.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
            mvc.perform(get("/knowledge/view/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));
            mvc.perform(get("/knowledge/view/entries/"+ID)).andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control","private, no-store"));
            for(long id:List.of(7L,8L,9L)){
                mvc.perform(get("/knowledge/view/entries/"+ID).header("Authorization","Bearer fixture-"+id)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content.body").value("私有正文"));
                mvc.perform(get("/knowledge/view/capabilities").header("Authorization","Bearer fixture-"+id)).andExpect(jsonPath("$.data.canManage").value(id==7));
            }
            for(String path:List.of("/entries","/entries/"+ID,"/entries/"+ID+"/history","/entries/"+ID+"/delete","/labels","/files","/relations","/exports")){
                for(var method:List.of(org.springframework.http.HttpMethod.GET,org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.DELETE)){
                    for(long id:List.of(8L,9L))mvc.perform(request(method,"/knowledge/manage"+path).header("Authorization","Bearer fixture-"+id).contentType("application/json").content("invalid-private-body")).andExpect(status().isForbidden());
                }
            }
            revoked.set(true);mvc.perform(get("/knowledge/view/entries/"+ID).header("Authorization","Bearer fixture-9")).andExpect(status().isForbidden());
            publicAccess.set(true);mvc.perform(get("/knowledge/view/entries/"+ID)).andExpect(status().isOk());
            when(users.selectById(9L)).thenReturn(User.builder().id(9L).isDisable(1).isDeleted(0).build());mvc.perform(get("/knowledge/view/entries/"+ID).header("Authorization","Bearer fixture-9")).andExpect(status().isForbidden());
            verify(context.getBean(KnowledgeStore.class),never()).save(anyLong(),any());
        }
    }
}
