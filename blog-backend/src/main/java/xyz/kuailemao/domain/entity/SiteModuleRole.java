package xyz.kuailemao.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("sys_site_module_role")
public class SiteModuleRole {
    private String moduleKey;
    private Long roleId;
}
