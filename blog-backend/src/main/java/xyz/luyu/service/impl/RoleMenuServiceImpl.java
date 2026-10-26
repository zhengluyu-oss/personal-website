package xyz.luyu.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import xyz.luyu.domain.entity.RoleMenu;
import xyz.luyu.mapper.RoleMenuMapper;
import xyz.luyu.service.RoleMenuService;

/**
 * (RoleMenu)表服务实现类
 *
 * @author luyu
 * @since 2023-11-28 10:23:17
 */
@Service("roleMenuService")
public class RoleMenuServiceImpl extends ServiceImpl<RoleMenuMapper, RoleMenu> implements RoleMenuService {

}
