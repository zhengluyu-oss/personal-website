-- Grant the five photo-management actions to the active ADMIN role.
-- Safe to rerun: existing permission rows and role grants are preserved.
START TRANSACTION;

INSERT INTO sys_permission (permission_desc, permission_key, menu_id, create_time, update_time, is_deleted)
SELECT actions.description, actions.permission_key, 81, NOW(), NOW(), 0
FROM (
    SELECT '后台相册或照片列表' AS description, 'blog:photo:list' AS permission_key
    UNION ALL SELECT '后台创建相册', 'blog:album:create'
    UNION ALL SELECT '后台上传照片', 'blog:photo:upload'
    UNION ALL SELECT '后台修改相册', 'blog:album:update'
    UNION ALL SELECT '后台删除相册或照片', 'blog:photo:delete'
) AS actions
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission existing WHERE existing.permission_key = actions.permission_key
);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT role.id, permission.id
FROM sys_role role
JOIN sys_permission permission ON permission.permission_key IN (
    'blog:photo:list', 'blog:album:create', 'blog:photo:upload',
    'blog:album:update', 'blog:photo:delete'
)
WHERE role.role_key = 'ADMIN' AND role.status = 0 AND role.is_deleted = 0
  AND permission.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission existing
      WHERE existing.role_id = role.id AND existing.permission_id = permission.id
  );

COMMIT;
