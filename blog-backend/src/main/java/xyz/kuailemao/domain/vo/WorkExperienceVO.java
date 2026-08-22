package xyz.kuailemao.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

@Data
public class WorkExperienceVO {
    private Long id;
    private String company;
    private String roleTitle;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date startDate;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;
    private Integer isCurrent;
    private String highlights;
    private String projectSummary;
    private String coverImage;
    private String techStack;
    private String responsibilities;
    private String metrics;
    private String content;
    private Integer orderNum;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
