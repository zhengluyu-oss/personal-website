package xyz.kuailemao.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.constants.RedisConst;
import xyz.kuailemao.domain.dto.UserCommentDTO;
import xyz.kuailemao.domain.entity.Comment;
import xyz.kuailemao.domain.entity.User;
import xyz.kuailemao.mapper.UserMapper;
import xyz.kuailemao.utils.RedisCache;
import xyz.kuailemao.utils.SecurityUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CommentServiceImplCreationCountTest {
    private CommentServiceImpl service;
    private RedisCache cache;
    private UserMapper users;

    @BeforeEach
    void setUp() {
        service = spy(new CommentServiceImpl());
        cache = mock(RedisCache.class);
        users = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "redisCache", cache);
        ReflectionTestUtils.setField(service, "userMapper", users);
        doReturn(true).when(service).save(any(Comment.class));
        when(users.selectById(42L)).thenReturn(User.builder().id(42L).build());
    }

    @Test
    void articleCommentIsCountedEvenWhenCommenterHasNoEmail() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(42L);

            assertEquals(200, service.userComment(request(1)).getCode());

            verify(cache).incrementCacheMapValue(RedisConst.ARTICLE_COMMENT_COUNT, "99", 1);
        }
    }

    @Test
    void messageBoardCommentNeverIncrementsArticleCount() {
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUserId).thenReturn(42L);

            assertEquals(200, service.userComment(request(2)).getCode());

            verifyNoInteractions(cache);
        }
    }

    private UserCommentDTO request(int type) {
        UserCommentDTO dto = new UserCommentDTO();
        dto.setType(type);
        dto.setTypeId(99);
        dto.setCommentContent("test comment");
        return dto;
    }
}
