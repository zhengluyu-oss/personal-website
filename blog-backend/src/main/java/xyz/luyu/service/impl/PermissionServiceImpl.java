package xyz.luyu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xyz.luyu.domain.dto.PermissionDTO;
import xyz.luyu.domain.entity.Menu;
import xyz.luyu.domain.entity.Permission;
import xyz.luyu.domain.entity.Role;
import xyz.luyu.domain.entity.RolePermission;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.PermissionMenuVO;
import xyz.luyu.domain.vo.PermissionVO;
import xyz.luyu.mapper.MenuMapper;
import xyz.luyu.mapper.PermissionMapper;
import xyz.luyu.mapper.RolePermissionMapper;
import xyz.luyu.service.PermissionService;
import xyz.luyu.utils.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * (Permission)表服务实现类
 *
 * @author luyu
 * @since 2023-12-05 19:53:31
 */
@Service
public class PermissionServiceImpl extends ServiceImpl<PermissionMapper, Permission> implements PermissionService {

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private RolePermissionMapper rolePermissionMapper;

    @Resource
    private MenuMapper menuMapper;

    @Override
    public List<PermissionVO> selectPermission(String permissionDesc, String permissionKey, Long permissionMenuId) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(Objects.nonNull(permissionDesc), Permission::getPermissionDesc, permissionDesc)
                .like(Objects.nonNull(permissionKey), Permission::getPermissionKey, permissionKey)
                .eq(Objects.nonNull(permissionMenuId), Permission::getMenuId, permissionMenuId);
        List<Permission> permissions = permissionMapper.selectList(wrapper);

        if (!permissions.isEmpty()) {
            List<Menu> menus = menuMapper.selectBatchIds(permissions.stream().map(Permission::getMenuId).toList());
            return permissions.stream().map(permission -> permission.asViewObject(PermissionVO.class, v -> {
                Optional<Menu> menu = menus.stream().filter(m -> m.getId().equals(permission.getMenuId())).findFirst();
                menu.ifPresent(m -> v.setMenuName(m.getTitle()));
            })).toList();
        }
        return new ArrayList<>();
    }

    @Override
    public List<PermissionMenuVO> selectPermissionMenu() {
        List<Permission> permissions = permissionMapper.selectList(null);
        if (!permissions.isEmpty()) {
            List<Menu> menus = menuMapper.selectBatchIds(permissions.stream().map(Permission::getMenuId).toList());
            List<PermissionMenuVO> vos = permissions.stream().map(permission -> permission.asViewObject(PermissionMenuVO.class, v -> {
                Optional<Menu> menu = menus.stream().filter(m -> m.getId().equals(permission.getMenuId())).findFirst();
                menu.ifPresent(m -> {
                    v.setMenuName(m.getTitle());
                    v.setMenuId(m.getId());
                });
            })).toList();
            return vos.stream().distinct().toList();
        }
        return new ArrayList<>();
    }

    @Transactional
    @Override
    public ResponseResult<Void> updateOrInsertPermission(PermissionDTO permissionDTO) {
        // 权限字符是否重复
        Permission isPermission = permissionMapper.selectOne(new LambdaQueryWrapper<Permission>().eq(Permission::getPermissionKey, permissionDTO.getPermissionKey().trim()));
        if (StringUtils.isNotNull(isPermission) && !isPermission.getId().equals(permissionDTO.getId())) {
            return ResponseResult.failure("权限字符不可重复");
        }
        Permission permission = permissionDTO.asViewObject(Permission.class, v -> v.setMenuId(permissionDTO.getPermissionMenuId()));
        if (this.saveOrUpdate(permission)) {
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

    @Override
    public PermissionDTO getPermission(Long id) {
        Permission permission = getById(id);
        return permission.asViewObject(PermissionDTO.class, v -> v.setPermissionMenuId(permission.getMenuId()));
    }

    @Transactional
    @Override
    public ResponseResult<Void> deletePermission(Long id) {
        if (permissionMapper.deleteById(id) > 0) {
            // 删除关系
            rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getPermissionId, id));
            return ResponseResult.success();
        }
        return ResponseResult.failure();
    }

}
