package xyz.kuailemao.domain.vo;

import lombok.Data;

import java.util.Date;

@Data
public class WebsiteShareVO {
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
    private Date createTime;
    private Date updateTime;
}
