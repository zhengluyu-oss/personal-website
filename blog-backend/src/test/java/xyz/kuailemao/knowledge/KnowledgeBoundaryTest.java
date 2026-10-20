package xyz.kuailemao.knowledge;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.SiteModuleAccessService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeBoundaryTest {
    KnowledgeProperties properties=new KnowledgeProperties();
    SiteModuleAccessService modules=mock(SiteModuleAccessService.class);
    UserMapper users=mock(UserMapper.class);
    @SuppressWarnings("unchecked") ObjectProvider<KnowledgeStore> stores=mock(ObjectProvider.class);
    KnowledgeAccess access=new KnowledgeAccess(properties,modules,users,stores);
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    void login(long id){var user=User.builder().id(id).isDeleted(0).isDisable(0).build();when(users.selectById(id)).thenReturn(user);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(user),null,List.of()));}
    @Test void ownerNeedsModulePermissionAndOtherAdministratorsHaveNoWriteAccess(){
        properties.setOwnerUserId(7L);login(7);
        assertFalse(access.canManage());when(modules.canView("knowledge")).thenReturn(true);assertTrue(access.canManage());
        login(8);assertTrue(access.canRead());assertFalse(access.canManage());
        when(users.selectById(8L)).thenReturn(User.builder().id(8L).isDeleted(0).isDisable(1).build());assertFalse(access.canRead());
    }
    @Test void deniedCapabilityNeverTouchesPostgres(){
        properties.setOwnerUserId(7L);login(8);
        var result=access.capabilities();assertFalse(result.allowed());assertFalse(result.available());assertFalse(result.canManage());verifyNoInteractions(stores);
    }
    @Test void anonymousReadStillRequiresPublicModuleAndNeverWrites(){
        properties.setOwnerUserId(7L);when(modules.canView("knowledge")).thenReturn(true);
        assertTrue(access.canRead());assertFalse(access.canManage());assertEquals(401,assertThrows(KnowledgeFailure.class,access::requireManage).status());
    }
    @Test void disabledModuleCreatesNoDataSource(){
        new ApplicationContextRunner().withUserConfiguration(KnowledgeDataConfiguration.class).run(context->{
            assertNull(context.getStartupFailure());assertFalse(context.containsBean("knowledgeDataSource"));assertFalse(context.containsBean("knowledgeTransactionManager"));
        });
    }
    @Test void enabledButIncompleteConfigurationIsLazyAndBounded(){
        new ApplicationContextRunner().withUserConfiguration(KnowledgeDataConfiguration.class).withPropertyValues("knowledge.enabled=true").run(context->{
            assertNull(context.getStartupFailure());var pool=context.getBean("knowledgeDataSource",com.zaxxer.hikari.HikariDataSource.class);
            assertEquals(2,pool.getMaximumPoolSize());assertEquals(0,pool.getMinimumIdle());assertFalse(context.getBean(KnowledgeProperties.class).configured());
        });
    }
    @Test void categoryCannotBeUsedAsATagAndSourceIsPassiveHttpOnly(){
        UUID id=UUID.randomUUID();assertThrows(KnowledgeFailure.class,()->new KnowledgeModels.Content(KnowledgeModels.Kind.NOTE,"标题","正文","",id,List.of(id)).validate());
        for(String source:List.of("file:///etc/passwd","javascript:alert(1)","https://user:password@example.com")) assertThrows(KnowledgeFailure.class,()->new KnowledgeModels.Content(KnowledgeModels.Kind.NOTE,"标题","正文",source,null,List.of()).validate());
    }
}
