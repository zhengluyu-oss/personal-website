package xyz.luyu.service;

import com.baomidou.mybatisplus.spring.service.IService;
import xyz.luyu.domain.dto.RoleUserDTO;
import xyz.luyu.domain.dto.UserRoleDTO;
import xyz.luyu.domain.entity.UserRole;
import xyz.luyu.domain.response.ResponseResult;
import xyz.luyu.domain.vo.RoleAllVO;
import xyz.luyu.domain.vo.RoleUserVO;

import java.util.List;


/**
 * (UserRole)表服务接口
 *
 * @author luyu
 * @since 2023-11-17 16:34:06
 */
public interface UserRoleService extends IService<UserRole> {

    /**
     * @param roleId 角色id
     * @param username 用户名
     * @param email 邮箱
     * @param type 角色类型,0：该角色的使用用户 1：该角色的未使用用户
     * @return 角色用户列表
     */
    List<RoleUserVO> selectRoleUser(Long roleId,String username,String email,Integer type);

    /**
     * 给角色授权用户
     * @param userRoleDTO 用户角色对象
     * @return 是否成功
     */
    ResponseResult<Void> addUserRole(UserRoleDTO userRoleDTO);

    /**
     * 取消授权
     * @param userRoleDTO 用户角色对象
     * @return 是否成功
     */
    ResponseResult<Void> deleteUserRole(UserRoleDTO userRoleDTO);

    /**
     * 所有使用该用户的角色
     *
     * @param userId 用户id
     * @param roleName 角色名称
     * @param roleKey 角色字符
     * @param type 角色类型,0：该角色的使用用户 1：该角色的未使用用户
     * @return 角色列表
     */
    List<RoleAllVO> selectRoleByUserId(Long userId, String roleName, String roleKey, Integer type);

    /**
     * 给多个角色添加某个用户
     * @param roleUserDTO 角色用户数据
     * @return 是否成功
     */
    ResponseResult<Void> addRoleUser(RoleUserDTO roleUserDTO);

    /**
     * 批量或单个取消授权
     * @param roleUserDTO 角色用户数据
     * @return 是否成功
     */
    ResponseResult<Void> deleteRoleUser(RoleUserDTO roleUserDTO);
}
