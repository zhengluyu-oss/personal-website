package xyz.luyu.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import xyz.luyu.controller.CommentController;
import xyz.luyu.domain.dto.UserCommentDTO;
import xyz.luyu.domain.entity.*;
import xyz.luyu.mapper.*;
import xyz.luyu.service.ContentVisibilityService;
import xyz.luyu.service.PublicService;
import xyz.luyu.service.SiteModuleAccessService;
import xyz.luyu.utils.RedisCache;
import xyz.luyu.utils.SecurityUtils;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommentAccessBoundaryTest {
    private CommentServiceImpl service;
    private CommentMapper comments;
    private ArticleMapper articles;
    private LeaveWordMapper messages;
    private UserMapper users;
    private RedisCache cache;
    private PublicService mail;
    private SiteModuleAccessService modules;
    private MockedStatic<SecurityUtils> security;

    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Comment.class);
        service = spy(new CommentServiceImpl());
        comments = mock(CommentMapper.class); articles = mock(ArticleMapper.class);
        messages = mock(LeaveWordMapper.class); users = mock(UserMapper.class);
        cache = mock(RedisCache.class); mail = mock(PublicService.class); modules = mock(SiteModuleAccessService.class);
        ContentVisibilityService visibility = new ContentVisibilityService();
        ReflectionTestUtils.setField(visibility, "articleMapper", articles);
        ReflectionTestUtils.setField(visibility, "leaveWordMapper", messages);
        ReflectionTestUtils.setField(service, "contentVisibilityService", visibility);
        ReflectionTestUtils.setField(service, "siteModuleAccessService", modules);
        ReflectionTestUtils.setField(service, "commentMapper", comments);
        ReflectionTestUtils.setField(service, "baseMapper", comments);
        ReflectionTestUtils.setField(service, "leaveWordMapper", messages);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "redisCache", cache);
        ReflectionTestUtils.setField(service, "publicService", mail);
        security = mockStatic(SecurityUtils.class);
        security.when(SecurityUtils::getUserId).thenReturn(42L);
        when(articles.selectById(99L)).thenReturn(Article.builder().id(99L).status(1).isDeleted(0).articleContent("synthetic").build());
        when(users.selectById(42L)).thenReturn(User.builder().id(42L).build());
        doReturn(true).when(service).save(any(Comment.class));
    }

    @AfterEach void tearDown() { security.close(); }

    private UserCommentDTO request(int type) {
        UserCommentDTO dto = new UserCommentDTO();
        dto.setType(type); dto.setTypeId(99); dto.setCommentContent("synthetic comment");
        return dto;
    }
    private Comment reply(Long id, Long parent) {
        return Comment.builder().id(id).parentId(parent).type(1).typeId(99).isCheck(1).isDeleted(0).commentUserId(50L).build();
    }
    private void noWrite() {
        verify(service, never()).save(any(Comment.class));
        verifyNoInteractions(cache, mail, users);
    }

    @Test void jsonTypeIsCheckedWithoutQueryParameters() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "synthetic denial")).when(modules).require("blog");
        CommentController controller = new CommentController();
        ReflectionTestUtils.setField(controller, "commentService", service);
        MockMvcBuilders.standaloneSetup(controller).build().perform(post("/comment/auth/add/comment")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":1,\"typeId\":99,\"commentContent\":\"synthetic\"}"))
                .andExpect(status().isForbidden());
        verify(modules).require("blog");
        verifyNoInteractions(articles);
        noWrite();
    }

    @Test void privateOrDeletedTargetsRejectReadCountAndSubmission() {
        for (Article target : List.of(
                Article.builder().id(99L).status(2).isDeleted(0).articleContent("private").build(),
                Article.builder().id(99L).status(1).isDeleted(1).articleContent("deleted").build())) {
            when(articles.selectById(99L)).thenReturn(target);
            assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                    () -> service.getComment(1, 99, 1, 10)).getStatusCode());
            assertThrows(ResponseStatusException.class, () -> service.userComment(request(1)));
        }
        verifyNoInteractions(comments);
        noWrite();
    }

    @Test void publicToPrivateChangeHidesPreviouslyApprovedComments() {
        Page<Comment> page = new Page<>(1, 10); page.setRecords(List.of());
        when(comments.selectPage(any(Page.class), any())).thenReturn(page);
        when(comments.selectList(any())).thenReturn(List.of());
        when(comments.selectCount(any())).thenReturn(7L);
        assertEquals(7L, service.getComment(1, 99, 1, 10).getTotal());
        clearInvocations(comments);
        when(articles.selectById(99L)).thenReturn(Article.builder().id(99L).status(2).isDeleted(0).articleContent("private").build());
        assertThrows(ResponseStatusException.class, () -> service.getComment(1, 99, 1, 10));
        verifyNoInteractions(comments);
    }

    @Test void invalidAndMissingTargetsCannotWrite() {
        assertThrows(ResponseStatusException.class, () -> service.getComment(3, 99, 1, 10));
        UserCommentDTO invalid = request(3);
        assertThrows(ResponseStatusException.class, () -> service.userComment(invalid));
        UserCommentDTO negative = request(1); negative.setTypeId(-1);
        assertThrows(ResponseStatusException.class, () -> service.userComment(negative));
        when(articles.selectById(99L)).thenReturn(null);
        assertThrows(ResponseStatusException.class, () -> service.userComment(request(1)));
        noWrite();
    }

    @Test void deletedHiddenAndCrossTargetRepliesCannotWrite() {
        for (Comment value : List.of(
                Comment.builder().id(10L).type(1).typeId(100).isCheck(1).isDeleted(0).commentUserId(50L).build(),
                Comment.builder().id(10L).type(1).typeId(99).isCheck(0).isDeleted(0).commentUserId(50L).build(),
                Comment.builder().id(10L).type(1).typeId(99).isCheck(1).isDeleted(1).commentUserId(50L).build())) {
            when(comments.selectById(10L)).thenReturn(value);
            UserCommentDTO dto = request(1); dto.setReplyId(10L); dto.setParentId(10L);
            assertThrows(ResponseStatusException.class, () -> service.userComment(dto));
        }
        noWrite();
    }

    @Test void forgedRecipientAndParentCannotWrite() {
        when(comments.selectById(10L)).thenReturn(reply(10L, null));
        when(comments.selectById(11L)).thenReturn(reply(11L, 10L));
        UserCommentDTO recipient = request(1); recipient.setReplyId(11L); recipient.setParentId(10L); recipient.setReplyUserId(51L);
        assertThrows(ResponseStatusException.class, () -> service.userComment(recipient));
        UserCommentDTO parent = request(1); parent.setReplyId(11L); parent.setParentId(12L);
        assertThrows(ResponseStatusException.class, () -> service.userComment(parent));
        noWrite();
    }

    @Test void validReplyDerivesRecipientAndStillAwaitsApproval() {
        when(comments.selectById(10L)).thenReturn(reply(10L, null));
        when(comments.selectById(11L)).thenReturn(reply(11L, 10L));
        UserCommentDTO dto = request(1); dto.setReplyId(11L);
        assertEquals(200, service.userComment(dto).getCode());
        verify(service).save(argThat(comment -> Integer.valueOf(0).equals(comment.getIsCheck())
                && Integer.valueOf(0).equals(comment.getIsDeleted())
                && Long.valueOf(10L).equals(comment.getParentId()) && Long.valueOf(50L).equals(comment.getReplyUserId())));
        verify(modules).require("blog");
        verifyNoInteractions(mail);
    }

    @Test void approvedMessageUsesItsOwnTargetAndDoesNotRequireArticleModule() {
        when(messages.selectById(99L)).thenReturn(LeaveWord.builder().id(99L).isCheck(1).isDeleted(0).content("synthetic message").build());
        assertEquals(200, service.userComment(request(2)).getCode());
        verifyNoInteractions(modules, articles, cache, mail);
        verify(messages).selectById(99L);
    }

    @Test void unapprovedMessageAndAnonymousSubmissionRejectBeforeWriting() {
        when(messages.selectById(99L)).thenReturn(LeaveWord.builder().id(99L).isCheck(0).isDeleted(0).content("pending").build());
        assertThrows(ResponseStatusException.class, () -> service.userComment(request(2)));
        security.when(SecurityUtils::getUserId).thenReturn(0L);
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.userComment(request(1))).getStatusCode());
        noWrite();
    }
}
