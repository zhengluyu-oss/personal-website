package xyz.kuailemao.service;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import xyz.kuailemao.domain.entity.*;
import xyz.kuailemao.mapper.*;
import java.util.List;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SiteModuleAccessServiceTest {
    @BeforeAll static void initializeMapperMetadata() {
        var builder = new MapperBuilderAssistant(new MybatisConfiguration(), "site-module-access-test");
        for (Class<?> type : List.of(SiteModule.class, SiteModuleRole.class, Role.class, UserRole.class, User.class))
            TableInfoHelper.initTableInfo(builder, type);
    }
    SiteModuleMapper modules = mock(SiteModuleMapper.class);
    SiteModuleRoleMapper grants = mock(SiteModuleRoleMapper.class);
    UserRoleMapper userRoles = mock(UserRoleMapper.class);
    RoleMapper roles = mock(RoleMapper.class);
    UserMapper users = mock(UserMapper.class);
    SiteModuleAccessService service = new SiteModuleAccessService(modules, grants, userRoles, roles, users);

    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    SiteModule policy(boolean publicAccess) {
        SiteModule policy = new SiteModule(); policy.setModuleKey("blog"); policy.setPublicAccess(publicAccess ? 1 : 0); policy.setRevision(0);
        when(modules.selectById("blog")).thenReturn(policy);
        return policy;
    }
    Role signedIn(String key) {
        User user = User.builder().id(7L).isDeleted(0).isDisable(0).build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new LoginUser(user), null, List.of()));
        when(users.selectById(7L)).thenReturn(user);
        when(userRoles.selectList(any())).thenReturn(List.of(new UserRole(1L, 7L, 2L)));
        Role role = new Role().setId(2L).setRoleKey(key).setStatus(0).setIsDeleted(0);
        when(roles.selectList(any())).thenReturn(List.of(role));
        return role;
    }
    @Test void publicModuleAllowsGuest() { policy(true); assertTrue(service.canView("blog")); }
    @Test void missingPolicyFailsClosed() {
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.canView("blog")).getStatusCode().value());
    }
    @Test void unknownModuleRejected() {
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.canView("unknown")).getStatusCode().value());
    }
    @Test void privateModuleRejectsGuestWith401() {
        policy(false); when(grants.selectList(any())).thenReturn(List.of(new SiteModuleRole("blog", 2L)));
        assertEquals(401, assertThrows(ResponseStatusException.class, () -> service.require("blog")).getStatusCode().value());
    }
    @Test void matchingRoleAllowsView() {
        policy(false); signedIn("MEMBER"); when(grants.selectList(any())).thenReturn(List.of(new SiteModuleRole("blog", 2L)));
        assertTrue(service.canView("blog"));
    }
    @Test void unrelatedRoleRejectedWith403() {
        policy(false); signedIn("MEMBER"); when(grants.selectList(any())).thenReturn(List.of(new SiteModuleRole("blog", 9L)));
        assertEquals(403, assertThrows(ResponseStatusException.class, () -> service.require("blog")).getStatusCode().value());
    }
    @Test void revokedMembershipTakesEffectWithSameLogin() {
        policy(false); signedIn("MEMBER"); when(grants.selectList(any())).thenReturn(List.of(new SiteModuleRole("blog", 2L)));
        assertTrue(service.canView("blog"));
        when(userRoles.selectList(any())).thenReturn(List.of());
        assertFalse(service.canView("blog"));
    }
    @Test void disabledRoleTakesEffectWithSameLogin() {
        policy(false); signedIn("MEMBER"); when(grants.selectList(any())).thenReturn(List.of(new SiteModuleRole("blog", 2L)));
        assertTrue(service.canView("blog"));
        when(roles.selectList(any())).thenReturn(List.of());
        assertFalse(service.canView("blog"));
    }
    @Test void disabledAccountCannotUseExistingRoleGrant() {
        policy(false); signedIn("MEMBER"); when(grants.selectList(any())).thenReturn(List.of(new SiteModuleRole("blog", 2L)));
        when(users.selectById(7L)).thenReturn(User.builder().id(7L).isDeleted(0).isDisable(1).build());
        assertFalse(service.canView("blog"));
    }
    @Test void activeSuperAdminCanViewWithoutExplicitGrant() { policy(false); signedIn("ADMIN"); assertTrue(service.canView("blog")); }
    @Test void restrictedConfigMustSelectRoles() {
        policy(true);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update("blog", false, List.of(), 0)).getStatusCode().value());
        verify(modules, never()).update(any(), any());
    }
    @Test void staleRevisionDoesNotReplaceGrants() {
        policy(false); when(roles.selectCount(any())).thenReturn(1L); when(modules.update(isNull(), any())).thenReturn(0);
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.update("blog", false, List.of(2L), 0)).getStatusCode().value());
        verify(grants, never()).delete(any());
    }
    @Test void invalidOrDisabledRoleCannotBeGranted() {
        policy(true); when(roles.selectCount(any())).thenReturn(0L);
        assertEquals(400, assertThrows(ResponseStatusException.class, () -> service.update("blog", false, List.of(2L), 0)).getStatusCode().value());
        verify(modules, never()).update(any(), any());
    }
    @Test void publicConfigClearsPreviousGrants() {
        policy(false); when(modules.update(isNull(), any())).thenReturn(1);
        service.update("blog", true, List.of(), 0);
        verify(grants).delete(any()); verify(grants, never()).insert(any(SiteModuleRole.class));
    }
}
