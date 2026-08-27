package xyz.kuailemao.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;


/**
 * @author kuailemao
 * <p>
 * 创建时间：2023/10/27 14:53
 * 分类VO
 */
@Data
@Schema(name = "CategoryVO", description = "分类VO")
public class CategoryVO {
    //分类id
    @Schema(description = "分类id")
    private Long id;
    //分类名
    @Schema(description = "分类名")
    private String categoryName;
    @Schema(description = "分类页主推荐文章id")
    private Long featuredArticleId;
    @Schema(description = "分类页主推荐文章标题")
    private String featuredArticleTitle;
    @Schema(description = "分类页 Hero 眉题")
    private String heroEyebrow;
    @Schema(description = "分类页 Hero 主标题强调段")
    private String heroTitleAccent;
    @Schema(description = "分类页 Hero 主标题后半段")
    private String heroTitle;
    @Schema(description = "分类页 Hero 简介")
    private String heroDescription;
    // 分类下的文章数量
    @Schema(description = "分类下的文章数量")
    private Long articleCount;
    // 标签创建时间
    private Date createTime;
    // 标签更新时间
    private Date updateTime;
}
