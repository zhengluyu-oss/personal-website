package xyz.kuailemao.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BlogFeedVO", description = "博客聚合展示数据")
public class BlogFeedVO {
    private ArticleVO featuredArticle;
    private List<ArticleVO> articles;
    private Long total;
    @Schema(description = "排除主推荐文章后的普通文章总数")
    private Long listTotal;
}
