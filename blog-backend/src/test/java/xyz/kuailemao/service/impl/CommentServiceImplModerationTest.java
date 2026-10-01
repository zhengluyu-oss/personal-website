package xyz.kuailemao.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import xyz.kuailemao.constants.RedisConst;
import xyz.kuailemao.domain.dto.CommentIsCheckDTO;
import xyz.kuailemao.domain.entity.Comment;
import xyz.kuailemao.mapper.CommentMapper;
import xyz.kuailemao.utils.RedisCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplModerationTest {

    @Mock private CommentMapper commentMapper;
    @Mock private RedisCache redisCache;

    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Comment.class);
        service = new CommentServiceImpl();
        ReflectionTestUtils.setField(service, "commentMapper", commentMapper);
        ReflectionTestUtils.setField(service, "redisCache", redisCache);
    }

    @Test
    void moderatingMessageBoardCommentDoesNotTouchArticleCount() {
        when(commentMapper.selectById(5L)).thenReturn(Comment.builder().id(5L).type(2).typeId(99).isCheck(0).build());
        when(commentMapper.update(any(Comment.class), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertEquals(200, service.isCheckComment(request(5L, 1)).getCode());

        verifyNoInteractions(redisCache);
    }

    @Test
    void moderatingArticleCommentChangesItsArticleCount() {
        when(commentMapper.selectById(5L)).thenReturn(Comment.builder().id(5L).type(1).typeId(99).isCheck(0).build());
        when(commentMapper.update(any(Comment.class), any(LambdaUpdateWrapper.class))).thenReturn(2);

        assertEquals(200, service.isCheckComment(request(5L, 1)).getCode());

        verify(redisCache).incrementCacheMapValue(RedisConst.ARTICLE_COMMENT_COUNT, "99", 2);
    }

    @Test
    void repeatedModerationIsSuccessfulWithoutChangingArticleCount() {
        when(commentMapper.selectById(5L)).thenReturn(Comment.builder().id(5L).type(1).typeId(99).isCheck(1).build());
        when(commentMapper.update(any(Comment.class), any(LambdaUpdateWrapper.class))).thenAnswer(invocation -> {
            Comment update = invocation.getArgument(0);
            assertNull(update.getId(), "Moderating children must never overwrite their primary keys");
            LambdaUpdateWrapper<Comment> query = invocation.getArgument(1);
            assertTrue(query.getSqlSegment().contains("is_check <>"), query.getSqlSegment());
            return 0;
        });

        assertEquals(200, service.isCheckComment(request(5L, 1)).getCode());

        verifyNoInteractions(redisCache);
    }

    @Test
    void missingCommentDoesNotUpdateAnything() {
        assertEquals(500, service.isCheckComment(request(5L, 1)).getCode());

        verify(commentMapper, never()).update(any(Comment.class), any(LambdaUpdateWrapper.class));
        verifyNoInteractions(redisCache);
    }

    private CommentIsCheckDTO request(Long id, Integer isCheck) {
        CommentIsCheckDTO request = new CommentIsCheckDTO();
        request.setId(id);
        request.setIsCheck(isCheck);
        return request;
    }
}
