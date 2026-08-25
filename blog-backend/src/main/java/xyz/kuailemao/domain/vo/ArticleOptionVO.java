package xyz.kuailemao.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "ArticleOptionVO", description = "后台主推荐文章候选项")
public class ArticleOptionVO {
    private Long id;
    private String articleTitle;
    private Long categoryId;
    private String categoryName;
}
