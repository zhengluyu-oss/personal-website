package xyz.kuailemao.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import xyz.kuailemao.domain.BaseData;

import java.util.Date;

@Data
@TableName("t_website_share")
public class WebsiteShare implements BaseData {
    private Long id;
    private Long sourceArticleId;
    private String title;
    private String siteUrl;
    private String summary;
    private String coverImage;
    private String content;
    private String seoTitle;
    private String seoDescription;
    private String seoKeywords;
    private Integer orderNum;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    private Integer isDeleted;
}
