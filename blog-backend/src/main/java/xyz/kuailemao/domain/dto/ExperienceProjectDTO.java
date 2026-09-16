package xyz.kuailemao.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import xyz.kuailemao.domain.BaseData;
import java.util.Date;

@Data
public class ExperienceProjectDTO implements BaseData {
    private Long id;
    @NotBlank(message="项目名称不能为空") @Length(max=150, message="项目名称过长") private String projectName;
    @NotBlank(message="项目摘要不能为空") @Length(max=500, message="项目摘要过长") private String summary;
    @Length(max=500, message="封面地址过长") @Pattern(regexp="^$|^https?://.+", message="封面地址必须是 HTTP(S) URL") private String coverImage;
    @JsonFormat(pattern="yyyy-MM-dd", timezone="GMT+8") private Date startDate;
    @JsonFormat(pattern="yyyy-MM-dd", timezone="GMT+8") private Date endDate;
    @Length(max=100, message="项目角色过长") private String roleTitle;
    private String techStack;
    private String contributions;
    private String outcomes;
    private String content;
    private Integer orderNum;
    private Integer status;
}
