package xyz.kuailemao.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class ExperienceProjectVO {
    private Long id;
    private Long experienceId;
    private String projectName;
    private String summary;
    private String coverImage;
    @JsonFormat(pattern="yyyy-MM-dd", timezone="GMT+8") private Date startDate;
    @JsonFormat(pattern="yyyy-MM-dd", timezone="GMT+8") private Date endDate;
    private String roleTitle;
    private String techStack;
    private String contributions;
    private String outcomes;
    private String content;
    private Integer orderNum;
    private Integer status;
    private String company;
    private String companyRoleTitle;
}
