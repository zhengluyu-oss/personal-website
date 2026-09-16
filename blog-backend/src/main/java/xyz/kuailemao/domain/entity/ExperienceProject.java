package xyz.kuailemao.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import xyz.kuailemao.domain.BaseData;
import java.util.Date;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@TableName("t_experience_project")
public class ExperienceProject implements BaseData {
    private Long id;
    private Long experienceId;
    private String projectName;
    private String summary;
    private String coverImage;
    private Date startDate;
    private Date endDate;
    private String roleTitle;
    private String techStack;
    private String contributions;
    private String outcomes;
    private String content;
    private Integer orderNum;
    private Integer status;
    @TableField(fill = FieldFill.INSERT) private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE) private Date updateTime;
    private Integer isDeleted;
}
