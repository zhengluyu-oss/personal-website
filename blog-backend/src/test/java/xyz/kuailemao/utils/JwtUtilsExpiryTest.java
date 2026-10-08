package xyz.kuailemao.utils;

import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import xyz.kuailemao.service.AccountAuthenticationVersion;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.domain.entity.User;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class JwtUtilsExpiryTest {
    private final JwtUtils jwtUtils = new JwtUtils();
    private final RedisCache redisCache = mock(RedisCache.class);

    private JwtUtilsExpiryTest() {
        AccountAuthenticationVersion versions = spy(new AccountAuthenticationVersion());
        doReturn("a".repeat(64)).when(versions).forCompletedLogin(anyLong(), any());
        ReflectionTestUtils.setField(jwtUtils, "authenticationVersion", versions);
        UserMapper users = mock(UserMapper.class);
        when(users.selectById(anyLong())).thenAnswer(call -> new User().setId(call.getArgument(0)).setIsDisable(0).setIsDeleted(0));
        ReflectionTestUtils.setField(jwtUtils, "userMapper", users);
        ReflectionTestUtils.setField(jwtUtils, "key", "local-test-key");
        ReflectionTestUtils.setField(jwtUtils, "expire", 7);
        ReflectionTestUtils.setField(jwtUtils, "adminExpireMinutes", 1440);
        ReflectionTestUtils.setField(jwtUtils, "redisCache", redisCache);
    }

    @Test
    void administratorJwtAndRedisEntryLastOneDay() {
        UserDetails admin = mock(UserDetails.class);
        when(admin.getAuthorities()).thenAnswer(ignored -> List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        long before = System.currentTimeMillis();
        String token = jwtUtils.createJwt("admin-session", admin, 1L, "admin");
        long after = System.currentTimeMillis();
        long expiry = JWT.decode(token).getExpiresAt().getTime();

        assertTrue(expiry >= before + Duration.ofDays(1).toMillis() - 1000);
        assertTrue(expiry <= after + Duration.ofDays(1).toMillis());
        verify(redisCache).setCacheObject(eq("jwt:white:list:admin-session"), eq(token),
                intThat(ttl -> ttl >= Duration.ofDays(1).toMillis() - 1000
                        && ttl <= Duration.ofDays(1).toMillis()), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    void regularUserKeepsExistingSevenDayLifetime() {
        UserDetails regular = mock(UserDetails.class);
        when(regular.getAuthorities()).thenAnswer(ignored -> List.of(new SimpleGrantedAuthority("ROLE_USER")));

        long before = System.currentTimeMillis();
        String token = jwtUtils.createJwt("regular-session", regular, 2L, "user");
        long expiry = JWT.decode(token).getExpiresAt().getTime();

        assertTrue(expiry >= before + Duration.ofDays(7).toMillis() - 1000);
        verify(redisCache).setCacheObject(eq("jwt:white:list:regular-session"), eq(token), anyInt(), eq(TimeUnit.MILLISECONDS));
    }
}
