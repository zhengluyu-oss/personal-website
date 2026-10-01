package xyz.kuailemao.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.Role;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.domain.entity.UserRole;
import xyz.kuailemao.mapper.PermissionMapper;
import xyz.kuailemao.mapper.RoleMapper;
import xyz.kuailemao.mapper.RolePermissionMapper;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.mapper.UserRoleMapper;

import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtUtilsSessionSafetyTest {
    private static final String SIGNING_KEY = "local-test-key";
    private static final Long USER_ID = 42L;
    private static final String ORIGINAL_PASSWORD_HASH = "$2a$10$original-password-hash";
    private static final String UPDATED_PASSWORD_HASH = "$2a$10$updated-password-hash";

    private final JwtUtils jwtUtils = new JwtUtils();
    private final RedisCache redisCache = mock(RedisCache.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserRoleMapper userRoleMapper = mock(UserRoleMapper.class);
    private final RoleMapper roleMapper = mock(RoleMapper.class);
    private final RolePermissionMapper rolePermissionMapper = mock(RolePermissionMapper.class);
    private final PermissionMapper permissionMapper = mock(PermissionMapper.class);

    JwtUtilsSessionSafetyTest() {
        ReflectionTestUtils.setField(jwtUtils, "key", SIGNING_KEY);
        ReflectionTestUtils.setField(jwtUtils, "expire", 7);
        ReflectionTestUtils.setField(jwtUtils, "redisCache", redisCache);
        ReflectionTestUtils.setField(jwtUtils, "userMapper", userMapper);
        ReflectionTestUtils.setField(jwtUtils, "userRoleMapper", userRoleMapper);
        ReflectionTestUtils.setField(jwtUtils, "roleMapper", roleMapper);
        ReflectionTestUtils.setField(jwtUtils, "rolePermissionMapper", rolePermissionMapper);
        ReflectionTestUtils.setField(jwtUtils, "permissionMapper", permissionMapper);
    }

    @Test
    void passwordChangeInvalidatesEarlierTokenAndLegacyTokenButAllowsNewToken() {
        AtomicReference<String> currentPasswordHash = new AtomicReference<>(ORIGINAL_PASSWORD_HASH);
        when(redisCache.isHasKey(anyString())).thenReturn(true);
        when(userMapper.selectById(USER_ID)).thenAnswer(ignored ->
                activeUser().setPassword(currentPasswordHash.get()));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        String legacyToken = JWT.create()
                .withJWTId("legacy-session")
                .withClaim("id", USER_ID)
                .withClaim("name", "reader")
                .withExpiresAt(new Date(System.currentTimeMillis() + 60_000))
                .sign(Algorithm.HMAC256(SIGNING_KEY));
        UserDetails details = tokenDetails(currentPasswordHash);
        String earlierToken = jwtUtils.createJwt("earlier-session", details, USER_ID, "reader");

        assertNull(JWT.decode(legacyToken).getClaim("credentialProof").asString());
        String earlierProof = JWT.decode(earlierToken).getClaim("credentialProof").asString();
        assertNotNull(earlierProof);
        assertNull(authenticate(legacyToken));
        assertNotNull(authenticate(earlierToken));

        currentPasswordHash.set(UPDATED_PASSWORD_HASH);

        assertNull(authenticate(earlierToken));

        String newToken = jwtUtils.createJwt("new-session", details, USER_ID, "reader");
        String newProof = JWT.decode(newToken).getClaim("credentialProof").asString();
        assertNotNull(newProof);
        assertNotEquals(earlierProof, newProof);
        assertNotNull(authenticate(newToken));
    }

    @Test
    void missingDisabledDeletedAndIncompleteUsersCannotBecomeAuthenticated() {
        DecodedJWT jwt = jwtForUser();
        User disabled = activeUser().setIsDisable(1);
        User deleted = activeUser().setIsDeleted(1);
        User missingDisabledFlag = activeUser().setIsDisable(null);
        User missingDeletedFlag = activeUser().setIsDeleted(null);
        when(userMapper.selectById(USER_ID)).thenReturn(null, disabled, deleted,
                missingDisabledFlag, missingDeletedFlag);

        for (int attempt = 0; attempt < 5; attempt++) {
            assertNull(jwtUtils.toUser(jwt));
        }

        verify(userMapper, times(5)).selectById(USER_ID);
        verifyNoInteractions(userRoleMapper, roleMapper, rolePermissionMapper, permissionMapper);
    }

    @Test
    void stoppedAndDeletedRolesDoNotGrantAuthorities() {
        when(userMapper.selectById(USER_ID)).thenReturn(activeUser());
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                UserRole.builder().userId(USER_ID).roleId(7L).build(),
                UserRole.builder().userId(USER_ID).roleId(8L).build()));
        when(roleMapper.selectById(7L)).thenReturn(new Role()
                .setId(7L)
                .setRoleKey("ADMIN")
                .setStatus(1)
                .setIsDeleted(0));
        when(roleMapper.selectById(8L)).thenReturn(new Role()
                .setId(8L)
                .setRoleKey("EDITOR")
                .setStatus(0)
                .setIsDeleted(1));

        UserDetails user = jwtUtils.toUser(jwtForUser());

        assertNotNull(user);
        assertTrue(user.getAuthorities().isEmpty());
        verifyNoInteractions(rolePermissionMapper, permissionMapper);
    }

    private User activeUser() {
        return new User().setId(USER_ID).setIsDisable(0).setIsDeleted(0)
                .setPassword(ORIGINAL_PASSWORD_HASH);
    }

    private DecodedJWT jwtForUser() {
        return JWT.decode(jwtUtils.createJwt("user-session", tokenDetails(
                new AtomicReference<>(ORIGINAL_PASSWORD_HASH)), USER_ID, "reader"));
    }

    private UserDetails authenticate(String token) {
        DecodedJWT jwt = jwtUtils.resolveJwt("Bearer " + token);
        return jwt == null ? null : jwtUtils.toUser(jwt);
    }

    private UserDetails tokenDetails(AtomicReference<String> passwordHash) {
        UserDetails details = mock(UserDetails.class);
        when(details.getAuthorities()).thenAnswer(ignored -> List.of());
        when(details.getPassword()).thenAnswer(ignored -> passwordHash.get());
        return details;
    }
}
