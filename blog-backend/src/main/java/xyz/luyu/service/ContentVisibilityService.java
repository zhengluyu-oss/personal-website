package xyz.luyu.service;

import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import xyz.luyu.domain.entity.Article;
import xyz.luyu.domain.entity.LeaveWord;
import xyz.luyu.mapper.ArticleMapper;
import xyz.luyu.mapper.LeaveWordMapper;

/** Shared public-target rules. Administrative permissions do not imply public visibility. */
@Service
public class ContentVisibilityService {
    @Resource private ArticleMapper articleMapper;
    @Resource private LeaveWordMapper leaveWordMapper;

    public boolean isPublic(Integer type, Long id) {
        return publicContent(type, id) != null;
    }

    public String publicContent(Integer type, Long id) {
        if (type == null || id == null || id <= 0) return null;
        if (type == 1) {
            Article article = articleMapper.selectById(id);
            return article != null && Integer.valueOf(0).equals(article.getIsDeleted())
                    && Integer.valueOf(1).equals(article.getStatus()) ? article.getArticleContent() : null;
        }
        if (type == 2) {
            LeaveWord message = leaveWordMapper.selectById(id);
            return message != null && Integer.valueOf(0).equals(message.getIsDeleted())
                    && Integer.valueOf(1).equals(message.getIsCheck()) ? message.getContent() : null;
        }
        return null;
    }
}
