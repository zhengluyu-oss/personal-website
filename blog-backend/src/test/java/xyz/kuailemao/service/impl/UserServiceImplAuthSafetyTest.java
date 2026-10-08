package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.constants.RedisConst;
import xyz.kuailemao.domain.dto.UserResetPasswordDTO;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.domain.response.ResponseResult;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.mapper.UserRoleMapper;
import xyz.kuailemao.utils.RedisCache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class UserServiceImplAuthSafetyTest {
    private final UserServiceImpl service = new UserServiceImpl();
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final RedisCache redisCache = mock(RedisCache.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwordEncoder);
    }

    @Test
    void passwordLoginLookupIsLimitedToEmailAccountsForUsernameAndEmail() {
        service.findAccountByNameOrEmail("same-name");

        @SuppressWarnings("unchecked")
        var wrapperCaptor = org.mockito.ArgumentCaptor.forClass(Wrapper.class);
        verify(userMapper).selectOne(wrapperCaptor.capture());
        String sql = ((LambdaQueryWrapper<User>) wrapperCaptor.getValue()).getSqlSegment();
        assertTrue(sql.contains("register_type"), sql);
        assertTrue(sql.contains("username"), sql);
        assertTrue(sql.contains("email"), sql);
        assertTrue(sql.contains("AND ("), sql);
    }

    @Test
    void loginByIdChecksProviderAndCurrentAccountStatus() {
        User account = new User().setId(42L).setRegisterType(2).setIsDisable(0).setIsDeleted(0);
        when(userMapper.selectById(42L)).thenReturn(account);

        assertThrows(UsernameNotFoundException.class, () -> service.loadLoginUserById(42L, 1));
        LoginUser login = service.loadLoginUserById(42L, 2);
        assertSame(account, login.getUser());

        account.setIsDisable(1);
        assertThrows(BadCredentialsException.class, () -> service.loadLoginUserById(42L, 2));
        account.setIsDisable(0).setIsDeleted(1);
        assertThrows(BadCredentialsException.class, () -> service.loadLoginUserById(42L, 2));
    }

    @Test
    void passwordResetUpdatesOnlyTheSelectedEmailAccount() {
        String codeKey = RedisConst.VERIFY_CODE + RedisConst.RESET + RedisConst.SEPARATOR + "user@example.com";
        when(redisCache.getCacheObject(codeKey)).thenReturn("123456");
        when(passwordEncoder.encode("new-password")).thenReturn("encoded");
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(new User().setId(42L));
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        UserResetPasswordDTO dto = new UserResetPasswordDTO();
        dto.setEmail("user@example.com");
        dto.setCode("123456");
        dto.setPassword("new-password");

        ResponseResult<Void> response = service.userResetPassword(dto);

        assertEquals(200, response.getCode());
        var order = inOrder(userMapper, redisCache);
        order.verify(userMapper).updateById(argThat((User user) -> user.getId().equals(42L)
                && "encoded".equals(user.getPassword())));
        order.verify(redisCache).deleteObject(codeKey);
    }

    @Test
    void invalidResetCodeDoesNotChangePassword() {
        String codeKey = RedisConst.VERIFY_CODE + RedisConst.RESET + RedisConst.SEPARATOR + "user@example.com";
        when(redisCache.getCacheObject(codeKey)).thenReturn("123456");
        UserResetPasswordDTO dto = new UserResetPasswordDTO();
        dto.setEmail("user@example.com");
        dto.setCode("000000");
        dto.setPassword("new-password");

        assertNotEquals(200, service.userResetPassword(dto).getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }
}
