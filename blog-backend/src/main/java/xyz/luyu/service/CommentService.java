package xyz.luyu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import xyz.luyu.domain.dto.CommentIsCheckDTO;
import xyz.luyu.domain.dto.SearchCommentDTO;
import xyz.luyu.domain.dto.UserCommentDTO;
import xyz.luyu.domain.entity.Comment;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.ArticleCommentVO;
import xyz.luyu.domain.vo.CommentListVO;
import xyz.luyu.domain.vo.PageVO;

import java.util.List;


/**
 * (CommentEmail)表服务接口
 *
 * @author luyu
 * @since 2023-10-19 15:44:57
 */
public interface CommentService extends IService<Comment> {
    /**
     * 查询文章评论
     */
    PageVO<List<ArticleCommentVO>> getComment(Integer type, Integer typeId, Integer pageNum, Integer pageSize);

    /**
     * 添加评论
     */
    ResponseResult<String> userComment(UserCommentDTO commentDTO);

    /**
     * 后台评论列表
     * @return 结果
     */
    List<CommentListVO> getBackCommentList(SearchCommentDTO searchDTO);

    /**
     * 是否通过评论
     * @param isCheckDTO 是否通过
     * @return 是否成功
     */
    ResponseResult<Void> isCheckComment(CommentIsCheckDTO isCheckDTO);

    /**
     * 删除评论
     * @param id id 列表
     * @return 是否成功
     */
    ResponseResult<Void> deleteComment(Long id);
}
