package xyz.kuailemao.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xyz.kuailemao.domain.BaseData;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_work_experience")
public class WorkExperience implements BaseData {
    private Long id;
    private String company;
    private String roleTitle;
    private Date startDate;
    private Date endDate;
    private Integer isCurrent;
    private String highlights;
    /** Markdown 详情正文（可含图片） */
    private String content;
    private Integer orderNum;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
    private Integer isDeleted;
}
