package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xyz.kuailemao.domain.dto.UpdateEmailDTO;
import xyz.kuailemao.domain.dto.UserRegisterDTO;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.IpService;
import xyz.kuailemao.service.OauthBrowserBinding;
import xyz.kuailemao.service.UserService;
import xyz.kuailemao.utils.RedisCache;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserEmailConflictTest {
    @Test void concurrentRegistrationFailsWithoutLeakingSqlOrSendingFollowup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
        UserServiceImpl service = spy(new UserServiceImpl());
        UserMapper users = mock(UserMapper.class);
        RedisCache redis = mock(RedisCache.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        IpService ips = mock(IpService.class);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "redisCache", redis);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(service, "ipService", ips);
        when(redis.getCacheObject(anyString())).thenReturn("123456");
        when(passwords.encode(anyString())).thenReturn("test-hash");
        doThrow(new DuplicateKeyException("private-sql@example.invalid")).when(service).save(any(User.class));
        UserRegisterDTO dto = new UserRegisterDTO();
        dto.setEmail("qa@example.invalid"); dto.setCode("123456"); dto.setUsername("qa"); dto.setPassword("test-password");
        MockHttpServletRequest request = new MockHttpServletRequest(); request.setRemoteAddr("203.0.113.2");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            var result = service.userRegister(dto);
            assertNotEquals(200, result.getCode());
            assertFalse(result.asJsonString().contains("private-sql"));
            verifyNoInteractions(ips);
            verify(redis, never()).deleteObject(anyString());
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }

    @Test void emailWriteConflictDoesNotReportSuccessOrLeakAddress() {
        for (int type : new int[]{0, 1}) {
            UserServiceImpl service = new UserServiceImpl();
            UserMapper users = mock(UserMapper.class);
            var changes = mock(xyz.kuailemao.service.EmailChangeService.class);
            ReflectionTestUtils.setField(service, "userMapper", users);
            ReflectionTestUtils.setField(service, "emailChangeService", changes);
            when(changes.complete(any())).thenReturn(xyz.kuailemao.domain.response.ResponseResult.failure("邮箱更新未完成"));
            UpdateEmailDTO dto = new UpdateEmailDTO(); dto.setEmail("new@example.invalid"); dto.setCode("123456"); dto.setPassword("test");
            try {
                var result = type == 0 ? service.updateEmailAndVerify(dto) : service.thirdUpdateEmail(dto);
                assertNotEquals(200, result.getCode());
                assertFalse(result.asJsonString().contains("private-sql"));
                verify(changes).complete(dto);
                verifyNoInteractions(users);
            } finally { SecurityContextHolder.clearContext(); }
        }
    }

    @Test void oauthConflictNeverLinksAccountOrIssuesExchange() {
        OauthServiceImpl service = new OauthServiceImpl();
        UserMapper users = mock(UserMapper.class); UserService userService = mock(UserService.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class); IpService ips = mock(IpService.class);
        OauthBrowserBinding binding = mock(OauthBrowserBinding.class);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(service, "ipService", ips);
        ReflectionTestUtils.setField(service, "browserBinding", binding);
        when(passwords.encode(anyString())).thenReturn("hash");
        when(userService.save(any(User.class))).thenThrow(new DuplicateKeyException("private-sql@example.invalid"));
        AuthResponse response = mock(AuthResponse.class); AuthUser provider = mock(AuthUser.class);
        when(response.getCode()).thenReturn(2000); when(response.getData()).thenReturn(provider);
        when(provider.getUuid()).thenReturn("42");
        assertEquals("?oauth_error=account_conflict", service.handleLogin(response, null, 1));
        verify(binding, never()).issue(any(), anyInt(), anyLong());
        verifyNoInteractions(ips);
        verify(users, never()).updateById(any(User.class));
    }
}
