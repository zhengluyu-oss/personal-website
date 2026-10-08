package xyz.kuailemao.service.impl;

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.kuailemao.service.OauthBrowserBinding;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.LoginUser;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.service.UserService;
import xyz.kuailemao.service.IpService;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class OauthServiceImplExchangeTest {
    private OauthServiceImpl service;
    private UserMapper users;
    private UserService userService;
    private OauthBrowserBinding binding;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new OauthServiceImpl();
        users = mock(UserMapper.class);
        userService = mock(UserService.class);
        binding = mock(OauthBrowserBinding.class);
        when(binding.issue(isNull(), anyInt(), anyLong())).thenReturn("c".repeat(64));
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "browserBinding", binding);
    }

    @Test
    void callbackOnlyExposesShortLivedCodeNotProviderTokenOrUsername() {
        AuthResponse response = mock(AuthResponse.class);
        AuthUser providerUser = mock(AuthUser.class);
        when(response.getCode()).thenReturn(2000);
        when(response.getData()).thenReturn(providerUser);
        when(providerUser.getUuid()).thenReturn("42");
        when(users.selectById(42L)).thenReturn(User.builder().id(42L)
                .registerType(1).isDisable(0).isDeleted(0).build());

        String redirectQuery = service.handleLogin(response, null, 1);

        assertTrue(redirectQuery.matches("\\?oauth_code=[0-9a-f]{64}"));
        assertFalse(redirectQuery.contains("access_token"));
        assertFalse(redirectQuery.contains("user_name"));
        verify(binding).issue(null, 1, 42L);
    }

    @Test
    void providerIdCollisionCannotSignInAsAnotherProvider() {
        AuthResponse response = mock(AuthResponse.class);
        AuthUser providerUser = mock(AuthUser.class);
        when(response.getCode()).thenReturn(2000);
        when(response.getData()).thenReturn(providerUser);
        when(providerUser.getUuid()).thenReturn("42");
        when(users.selectById(42L)).thenReturn(User.builder().id(42L).registerType(2).build());

        assertEquals("?oauth_error=account_conflict", service.handleLogin(response, null, 1));
        verify(binding, never()).issue(any(), anyInt(), anyLong());
    }

    @Test
    void newOauthAccountGetsRandomLocalPasswordInsteadOfProviderToken() {
        AuthResponse response = mock(AuthResponse.class);
        AuthUser providerUser = mock(AuthUser.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        IpService ips = mock(IpService.class);
        when(response.getCode()).thenReturn(2000);
        when(response.getData()).thenReturn(providerUser);
        when(providerUser.getUuid()).thenReturn("42");
        when(providerUser.getUsername()).thenReturn("provider-user");
        when(passwords.encode(anyString())).thenReturn("local-random-hash");
        when(userService.save(any(User.class))).thenReturn(true);
        ReflectionTestUtils.setField(service, "passwordEncoder", passwords);
        ReflectionTestUtils.setField(service, "ipService", ips);

        String redirectQuery = service.handleLogin(response, null, 2);

        ArgumentCaptor<User> inserted = ArgumentCaptor.forClass(User.class);
        verify(userService).save(inserted.capture());
        assertEquals("local-random-hash", inserted.getValue().getPassword());
        assertEquals(2, inserted.getValue().getRegisterType());
        assertTrue(redirectQuery.startsWith("?oauth_code="));
        verify(ips).refreshIpDetailAsyncByUidAndRegister(42L);
    }

    @Test
    void handoffIsConsumedOnceAndRechecksCurrentAccount() {
        String code = "a".repeat(64);
        LoginUser user = new LoginUser();
        when(binding.exchange(null, code)).thenReturn("2:42", null);
        when(userService.loadLoginUserById(42L, 2)).thenReturn(user);

        assertSame(user, service.exchangeCode(code));
        assertThrows(BadCredentialsException.class, () -> service.exchangeCode(code));
        verify(userService, times(1)).loadLoginUserById(42L, 2);
    }

    @Test
    void malformedHandoffIsRejectedWithoutLookup() {
        assertThrows(BadCredentialsException.class, () -> service.exchangeCode("not-a-code"));
        verify(binding, never()).exchange(any(), anyString());
    }
}
