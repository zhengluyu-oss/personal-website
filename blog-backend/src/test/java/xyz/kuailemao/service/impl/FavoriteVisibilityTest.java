package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.domain.entity.*;
import xyz.kuailemao.mapper.*;
import xyz.kuailemao.service.ContentVisibilityService;
import xyz.kuailemao.utils.RedisCache;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FavoriteVisibilityTest {
    private final FavoriteServiceImpl service = new FavoriteServiceImpl();
    private final ContentVisibilityService visibility = new ContentVisibilityService();
    private final ArticleMapper articles = mock(ArticleMapper.class);
    private final LeaveWordMapper messages = mock(LeaveWordMapper.class);
    private final FavoriteMapper favorites = mock(FavoriteMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final RedisCache redis = mock(RedisCache.class);

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Favorite.class);
        ReflectionTestUtils.setField(visibility, "articleMapper", articles);
        ReflectionTestUtils.setField(visibility, "leaveWordMapper", messages);
        ReflectionTestUtils.setField(service, "contentVisibilityService", visibility);
        ReflectionTestUtils.setField(service, "favoriteMapper", favorites);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "redisCache", redis);
        LoginUser principal = new LoginUser(User.builder().id(1L).build(), List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void privateDraftDeletedMissingAndPendingTargetsAreRejectedWithoutSideEffects() {
        for (int status : List.of(2, 3)) {
            when(articles.selectById(2L)).thenReturn(Article.builder().id(2L).status(status).isDeleted(0).articleContent("private").build());
            assertNotEquals(200, service.userFavorite(1, 2L).getCode());
        }
        when(articles.selectById(2L)).thenReturn(Article.builder().id(2L).status(1).isDeleted(1).articleContent("deleted").build());
        assertNotEquals(200, service.userFavorite(1, 2L).getCode());
        when(messages.selectById(2L)).thenReturn(LeaveWord.builder().id(2L).isCheck(0).isDeleted(0).content("pending").build());
        assertNotEquals(200, service.userFavorite(2, 2L).getCode());
        assertNotEquals(200, service.userFavorite(1, null).getCode());
        assertNotEquals(200, service.userFavorite(3, 2L).getCode());
        assertNotEquals(200, service.userFavorite(1, 99L).getCode());
        verifyNoInteractions(favorites, redis);
    }

    @Test void approvedPublicTargetsRemainVisible() {
        when(articles.selectById(2L)).thenReturn(Article.builder().status(1).isDeleted(0).articleContent("# Public").build());
        when(messages.selectById(3L)).thenReturn(LeaveWord.builder().isCheck(1).isDeleted(0).content("message").build());
        assertEquals("# Public", visibility.publicContent(1, 2L));
        assertEquals("message", visibility.publicContent(2, 3L));
    }

    @Test void missingUserAndTargetReturnUnavailableInsteadOfNullPointer() {
        when(favorites.selectList(any())).thenReturn(List.of(Favorite.builder().id(9L).userId(99L).type(1).typeId(100L).build()));
        var result = service.getBackFavoriteList(null);
        assertEquals(1, result.size());
        assertEquals("收藏内容已不可用", result.get(0).getContent());
        assertEquals(0, result.get(0).getIsCheck());
    }
}
