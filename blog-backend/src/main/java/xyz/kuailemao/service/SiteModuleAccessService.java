package xyz.kuailemao.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import xyz.kuailemao.domain.entity.*;
import xyz.kuailemao.mapper.*;
import xyz.kuailemao.utils.SecurityUtils;

import java.util.*;

@Service
public class SiteModuleAccessService {
    public static final Map<String, String> MODULES;
    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("home", "首页"); names.put("experience", "工作经历");
        names.put("blog", "个人博客"); names.put("shares", "网站分享");
        names.put("photos", "相册"); names.put("about", "关于我");
        MODULES = Collections.unmodifiableMap(names);
    }
    private final SiteModuleMapper modules;
    private final SiteModuleRoleMapper grants;
    private final UserRoleMapper userRoles;
    private final RoleMapper roles;
    private final UserMapper users;

    public SiteModuleAccessService(SiteModuleMapper modules, SiteModuleRoleMapper grants,
            UserRoleMapper userRoles, RoleMapper roles, UserMapper users) {
        this.modules = modules; this.grants = grants; this.userRoles = userRoles;
        this.roles = roles; this.users = users;
    }

    public record Access(String key, String name, boolean publicAccess, boolean allowed) {}
    public record Policy(String key, String name, boolean publicAccess, List<Long> roleIds, int revision) {}

    private SiteModule policy(String key) {
        if (!MODULES.containsKey(key)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "未知模块");
        SiteModule policy = modules.selectById(key);
        if (policy == null || policy.getRevision() == null || policy.getPublicAccess() == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "模块访问配置尚未就绪");
        return policy;
    }

    // Resolve live membership: revoked/disabled roles must take effect without waiting for JWT expiry.
    private List<Role> activeRoles(Long userId) {
        if (userId == null || userId <= 0) return List.of();
        User user = users.selectById(userId);
        if (user == null || !Integer.valueOf(0).equals(user.getIsDeleted())
                || !Integer.valueOf(0).equals(user.getIsDisable())) return List.of();
        List<Long> ids = userRoles.selectList(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)).stream().map(UserRole::getRoleId).distinct().toList();
        if (ids.isEmpty()) return List.of();
        return roles.selectList(new LambdaQueryWrapper<Role>().in(Role::getId, ids)
                .eq(Role::getStatus, 0).eq(Role::getIsDeleted, 0));
    }

    public boolean canView(String key) { return canView(policy(key), activeRoles(SecurityUtils.getUserId())); }

    private boolean canView(SiteModule policy, List<Role> active) {
        if (Integer.valueOf(1).equals(policy.getPublicAccess())) return true;
        if (active.stream().anyMatch(role -> "ADMIN".equals(role.getRoleKey()))) return true;
        Set<Long> allowed = new HashSet<>(grants.selectList(new LambdaQueryWrapper<SiteModuleRole>()
                .eq(SiteModuleRole::getModuleKey, policy.getModuleKey())).stream().map(SiteModuleRole::getRoleId).toList());
        return active.stream().anyMatch(role -> allowed.contains(role.getId()));
    }

    public void require(String key) {
        if (!canView(key)) throw new ResponseStatusException(SecurityUtils.isLogin()
                ? HttpStatus.FORBIDDEN : HttpStatus.UNAUTHORIZED, "没有查看该模块的权限，请登录具有授权角色的账号");
    }

    public List<Access> access() {
        List<Role> active = activeRoles(SecurityUtils.getUserId());
        return MODULES.entrySet().stream().map(entry -> {
            SiteModule policy = policy(entry.getKey());
            return new Access(entry.getKey(), entry.getValue(), Integer.valueOf(1).equals(policy.getPublicAccess()), canView(policy, active));
        }).toList();
    }

    public List<Policy> policies() {
        return MODULES.entrySet().stream().map(entry -> {
            SiteModule policy = policy(entry.getKey());
            List<Long> ids = grants.selectList(new LambdaQueryWrapper<SiteModuleRole>()
                    .eq(SiteModuleRole::getModuleKey, entry.getKey())).stream().map(SiteModuleRole::getRoleId).toList();
            return new Policy(entry.getKey(), entry.getValue(), Integer.valueOf(1).equals(policy.getPublicAccess()), ids, policy.getRevision());
        }).toList();
    }

    @Transactional
    public void update(String key, boolean publicAccess, List<Long> roleIds, int revision) {
        policy(key);
        List<Long> ids = roleIds.stream().distinct().toList();
        if (ids.size() != roleIds.size() || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色配置无效");
        if (!publicAccess && ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择至少一个可查看的角色");
        if (publicAccess && !ids.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "公开模块无需配置角色");
        if (!ids.isEmpty() && roles.selectCount(new LambdaQueryWrapper<Role>().in(Role::getId, ids)
                .eq(Role::getStatus, 0).eq(Role::getIsDeleted, 0)) != ids.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "包含已停用或不存在的角色");
        int changed = modules.update(null, new LambdaUpdateWrapper<SiteModule>()
                .eq(SiteModule::getModuleKey, key).eq(SiteModule::getRevision, revision)
                .set(SiteModule::getPublicAccess, publicAccess ? 1 : 0).set(SiteModule::getRevision, revision + 1));
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "配置已被修改，请刷新后重试");
        grants.delete(new LambdaQueryWrapper<SiteModuleRole>().eq(SiteModuleRole::getModuleKey, key));
        for (Long id : ids) grants.insert(new SiteModuleRole(key, id));
    }
}
