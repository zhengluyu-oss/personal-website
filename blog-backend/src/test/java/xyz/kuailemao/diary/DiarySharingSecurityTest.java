package xyz.kuailemao.diary;

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
import xyz.kuailemao.service.SiteModuleAccessService;
import xyz.kuailemao.utils.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DiarySharingSecurityTest {
    static final UUID ID=UUID.fromString("00000000-0000-4000-8000-000000000001");
    @Configuration(proxyBeanMethods=false) @EnableWebSecurity @EnableWebMvc
    @Import({DiarySecurityConfiguration.class,DiaryModuleAccess.class,DiaryReadController.class,DiaryService.class,DiaryController.class,DiaryExceptionHandler.class})
    static class Config {
        @Bean DiaryProperties properties(){var p=new DiaryProperties();p.setEnabled(true);p.setOwnerUserId(7L);p.setUrl("jdbc:postgresql://127.0.0.1:1/fixture-never-connected");p.setPassword("synthetic-only");return p;}
        @Bean JwtAuthorizeFilter filter(){return new JwtAuthorizeFilter();}
        @Bean DiaryStore store(){var store=mock(DiaryStore.class);when(store.available(7)).thenReturn(true);
            when(store.read(7,ID)).thenReturn(new DiaryModels.Entry(ID,"合成标题","合成正文",null,null,1,Instant.EPOCH,Instant.EPOCH,null));
            when(store.search(eq(7L),any())).thenReturn(new DiaryModels.Page<>(List.of(),0,1,20));return store;}
        @Bean DiaryReadStore reader(){return mock(DiaryReadStore.class);}
    }
    @Test void sharedReadingUsesLiveModuleGrantWhileEveryManagementPathStillRequiresAuthor() throws Exception {
        try(var context=new AnnotationConfigWebApplicationContext()) {
            AtomicBoolean publicAccess=new AtomicBoolean(false),revoked=new AtomicBoolean(false);
            var jwt=mock(JwtUtils.class);var modules=mock(SiteModuleAccessService.class);
            when(modules.canView("diary")).thenAnswer(call->publicAccess.get() || !revoked.get() && Set.of(7L,8L,9L).contains(SecurityUtils.getUserId()));
            for(long id:List.of(7L,8L,9L,10L,11L)) {
                var decoded=mock(DecodedJWT.class);when(jwt.resolveJwt("Bearer fixture-"+id)).thenReturn(decoded);
                when(jwt.toUser(decoded)).thenReturn(new LoginUser(User.builder().id(id).build(),List.of(id==8?"ROLE_ADMIN":"ROLE_USER")));
            }
            context.setServletContext(new MockServletContext());context.addBeanFactoryPostProcessor(factory->{factory.registerSingleton("jwtUtils",jwt);factory.registerSingleton("modules",modules);});
            context.register(Config.class);context.refresh();
            var mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",Filter.class)).build();
            var store=context.getBean(DiaryStore.class);
            mvc.perform(get("/diary/view/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.data.allowed").value(false));
            mvc.perform(get("/diary/view/entries/"+ID)).andExpect(status().isUnauthorized());
            for(long id:List.of(7L,8L,9L)) {
                mvc.perform(get("/diary/view/entries/"+ID).header("Authorization","Bearer fixture-"+id))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.body").value("合成正文")).andExpect(header().string("Cache-Control","private, no-store"));
                mvc.perform(get("/diary/view/capabilities").header("Authorization","Bearer fixture-"+id))
                    .andExpect(jsonPath("$.data.canManage").value(id==7));
            }
            for(long id:List.of(10L,11L)) mvc.perform(get("/diary/view/entries/"+ID).header("Authorization","Bearer fixture-"+id)).andExpect(status().isForbidden());
            mvc.perform(post("/diary/view/entries/search").header("Authorization","Bearer fixture-9").contentType("application/json").content("{\"owner\":8,\"page\":1,\"pageSize\":20}"))
                .andExpect(status().isOk());
            mvc.perform(post("/diary/view/entries/search").header("Authorization","Bearer fixture-9").contentType("application/json").content("{\"deleted\":true}"))
                .andExpect(status().isForbidden());
            when(store.read(7,ID)).thenReturn(new DiaryModels.Entry(ID,"合成标题","已删除原文",null,null,2,Instant.EPOCH,Instant.EPOCH,Instant.EPOCH));
            mvc.perform(get("/diary/view/entries/"+ID).header("Authorization","Bearer fixture-9")).andExpect(status().isNotFound());
            revoked.set(true);
            mvc.perform(get("/diary/view/entries/"+ID).header("Authorization","Bearer fixture-9")).andExpect(status().isForbidden());
            publicAccess.set(true);
            mvc.perform(get("/diary/view/calendar?month=2026-10")).andExpect(status().isOk());
            publicAccess.set(false);
            mvc.perform(get("/diary/view/calendar?month=2026-10")).andExpect(status().isUnauthorized());
            publicAccess.set(true);
            for(String path:List.of("/entries","/entries/"+ID,"/entries/"+ID+"/history","/entries/"+ID+"/restore","/export","/ai/index","/ai/questions","/ai/reviews","/ai/jobs/"+ID,"/coaching/goals","/coaching/actions","/coaching/periods/jobs","/coaching/export"))
                for(var method:List.of(org.springframework.http.HttpMethod.GET,org.springframework.http.HttpMethod.POST,org.springframework.http.HttpMethod.PUT,org.springframework.http.HttpMethod.DELETE)) {
                    for(long id:List.of(8L,9L)) mvc.perform(request(method,"/diary/auth"+path).header("Authorization","Bearer fixture-"+id).contentType("application/json").content("private-invalid-body"))
                        .andExpect(status().isForbidden());
                    mvc.perform(request(method,"/diary/auth"+path).contentType("application/json").content("private-invalid-body"))
                        .andExpect(status().isUnauthorized());
                }
            verify(store,never()).read(eq(8L),any());verify(store,never()).read(eq(9L),any());
            verify(store,never()).create(anyLong(),any());verify(store,never()).save(anyLong(),any(),any());
            verifyNoInteractions(context.getBean(DiaryReadStore.class));
        }
    }
    @Test void sourcesMustMatchTheirCurrentUnicodeTextExactly() {
        String text="合成😀日记";
        org.junit.jupiter.api.Assertions.assertEquals("😀",DiaryReadStore.verified(text,2,3,xyz.kuailemao.diary.ai.DiaryAiText.hash("😀")));
        org.junit.jupiter.api.Assertions.assertThrows(DiaryFailure.class,()->DiaryReadStore.verified(text,0,999,"bad"));
        org.junit.jupiter.api.Assertions.assertThrows(DiaryFailure.class,()->DiaryReadStore.verified(text,0,1,"bad"));
    }
}
