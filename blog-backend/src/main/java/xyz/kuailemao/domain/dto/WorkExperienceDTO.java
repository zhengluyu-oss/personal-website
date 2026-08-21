package xyz.kuailemao.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;
import org.hibernate.validator.constraints.Length;
import xyz.kuailemao.domain.BaseData;

import java.util.Date;

@Accessors(chain = true)
@Data
public class WorkExperienceDTO implements BaseData {
    private Long id;
    @NotBlank(message = "公司名称不能为空")
    @Length(max = 100, message = "公司名称过长")
    private String company;
    @NotBlank(message = "岗位不能为空")
    @Length(max = 100, message = "岗位名称过长")
    private String roleTitle;
    @NotNull(message = "开始日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date startDate;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date endDate;
    private Integer isCurrent;
    private String highlights;
    private String content;
    private Integer orderNum;
    private Integer status;
}
