package xyz.kuailemao.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_site_module")
public class SiteModule {
    @TableId private String moduleKey;
    private Integer publicAccess;
    private Integer revision;
}
