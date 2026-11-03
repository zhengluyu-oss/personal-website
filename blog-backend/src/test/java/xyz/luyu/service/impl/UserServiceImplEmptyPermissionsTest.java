package xyz.luyu.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.luyu.domain.entity.Role;
import xyz.luyu.domain.entity.User;
import xyz.luyu.domain.entity.UserRole;
import xyz.luyu.mapper.PermissionMapper;
import xyz.luyu.mapper.RoleMapper;
import xyz.luyu.mapper.RolePermissionMapper;
import xyz.luyu.mapper.UserRoleMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserServiceImplEmptyPermissionsTest {
    @Test
    void roleWithoutPermissionsDoesNotFailLogin() {
        UserServiceImpl service = new UserServiceImpl();
        UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
        RoleMapper roleMapper = mock(RoleMapper.class);
        RolePermissionMapper rolePermissionMapper = mock(RolePermissionMapper.class);
        PermissionMapper permissionMapper = mock(PermissionMapper.class);
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(service, "rolePermissionMapper", rolePermissionMapper);
        ReflectionTestUtils.setField(service, "permissionMapper", permissionMapper);

        User user = new User().setId(42L).setIsDisable(0).setIsDeleted(0);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                UserRole.builder().userId(42L).roleId(7L).build()));
        when(roleMapper.selectById(7L)).thenReturn(new Role()
                .setId(7L).setRoleKey("ADMIN").setStatus(0).setIsDeleted(0));
        when(rolePermissionMapper.selectBatchIds(List.of(7L))).thenReturn(List.of());

        assertEquals(List.of("ROLE_ADMIN"), service.handlerLogin(user).getAuthorities().stream()
                .map(authority -> authority.getAuthority()).toList());
        verifyNoInteractions(permissionMapper);
    }
}
