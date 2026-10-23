package xyz.luyu.mapper;

import xyz.luyu.domain.email.CommentEmail;

import java.util.List;

/**
 * @author luyu
 * @since 2024/8/8 上午1:29
 */
public interface EmailInformMapper {

    /**
     * 查询用户评论信息
     * @param commentId 评论id
     * @param type 评论类型
     * @return 需要的信息
     */
    CommentEmail getCommentEmailOne(String commentId, Integer type);
}
