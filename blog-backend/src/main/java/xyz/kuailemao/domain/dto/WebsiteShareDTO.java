package xyz.kuailemao.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import xyz.kuailemao.domain.BaseData;

@Data
public class WebsiteShareDTO implements BaseData {
    private Long id;
    @NotBlank(message = "网站名称不能为空")
    @Length(max = 150, message = "网站名称不能超过150字")
    private String title;
    @NotBlank(message = "网站地址不能为空")
    @Length(max = 500, message = "网站地址不能超过500字")
    private String siteUrl;
    @NotBlank(message = "网站简介不能为空")
    @Length(max = 500, message = "网站简介不能超过500字")
    private String summary;
    @Length(max = 500, message = "封面地址不能超过500字")
    private String coverImage;
    @NotBlank(message = "详情正文不能为空")
    private String content;
    @Length(max = 70, message = "SEO标题不能超过70字")
    private String seoTitle;
    @Length(max = 200, message = "SEO描述不能超过200字")
    private String seoDescription;
    @Length(max = 200, message = "SEO关键词不能超过200字")
    private String seoKeywords;
    private Integer orderNum;
    @Min(value = 0, message = "状态无效")
    @Max(value = 1, message = "状态无效")
    private Integer status;
}
