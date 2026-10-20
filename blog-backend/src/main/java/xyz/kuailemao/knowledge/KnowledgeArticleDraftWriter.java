package xyz.kuailemao.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.kuailemao.domain.dto.ArticleDTO;
import xyz.kuailemao.domain.entity.Article;
import xyz.kuailemao.mapper.ArticleMapper;
import xyz.kuailemao.mapper.KnowledgeArticleExportMapper;
import xyz.kuailemao.service.ArticleService;

import java.util.Objects;
import java.util.UUID;

/** The article and its export mapping commit in the same existing MySQL transaction. */
@Service
@ConditionalOnProperty(name="knowledge.enabled",havingValue="true")
public class KnowledgeArticleDraftWriter {
    private final KnowledgeArticleExportMapper mappings;
    private final ArticleMapper articles;
    private final ArticleService articleService;

    public KnowledgeArticleDraftWriter(KnowledgeArticleExportMapper mappings, ArticleMapper articles, ArticleService articleService) {
        this.mappings=mappings;this.articles=articles;this.articleService=articleService;
    }

    @Transactional
    public long create(long owner, UUID exportId, String digest, ArticleDTO request) {
        if (request == null || request.getId() != null || !Integer.valueOf(3).equals(request.getStatus()))
            throw KnowledgeFailure.invalid("只能创建新的博客草稿");
        var existing = mappings.find(owner, exportId.toString());
        if (existing != null) {
            if (!Objects.equals(existing.requestHash(), digest)) throw KnowledgeFailure.conflict();
            Article article = articles.selectById(existing.articleId());
            if (article == null || !Objects.equals(article.getUserId(), owner)
                    || Integer.valueOf(1).equals(article.getIsDeleted()))
                throw new KnowledgeFailure(410,"已生成的草稿被删除，不能自动再建一篇");
            return existing.articleId();
        }
        long id = IdWorker.getId();
        if (articles.selectById(id) != null) throw KnowledgeFailure.conflict();
        request.setId(id);
        var result = articleService.publish(request);
        if (result == null || !Integer.valueOf(200).equals(result.getCode()))
            throw KnowledgeFailure.invalid(result == null ? "草稿保存失败，请稍后重试" : result.getMsg());
        Article saved = articles.selectById(id);
        if (saved == null || !Objects.equals(saved.getUserId(), owner)
                || !Integer.valueOf(3).equals(saved.getStatus()) || !Integer.valueOf(0).equals(saved.getIsDeleted()))
            throw KnowledgeFailure.unavailable();
        if (mappings.insert(owner, exportId.toString(), digest, id) != 1) throw KnowledgeFailure.unavailable();
        return id;
    }
}
