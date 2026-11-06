-- 部署移除树洞功能的应用版本时执行。仅清理菜单和权限，不删除 t_tree_hole 历史内容。
-- 可重复执行；使用树洞专属权限键与菜单名称限定范围，避免影响其他互动功能。
START TRANSACTION;

DELETE role_permission FROM sys_role_permission AS role_permission
JOIN sys_permission AS permission ON permission.id = role_permission.permission_id
WHERE permission.permission_key IN (
  'blog:treeHole:list', 'blog:treeHole:search',
  'blog:treeHole:isCheck', 'blog:treeHole:delete'
);

DELETE FROM sys_permission
WHERE permission_key IN (
  'blog:treeHole:list', 'blog:treeHole:search',
  'blog:treeHole:isCheck', 'blog:treeHole:delete'
);

DELETE role_menu FROM sys_role_menu AS role_menu
JOIN sys_menu AS menu ON menu.id = role_menu.menu_id
WHERE menu.title = '树洞管理'
  AND (menu.path = '/tree-hole' OR menu.component = '/blog/tree-hole');

DELETE FROM sys_menu
WHERE title = '树洞管理'
  AND (path = '/tree-hole' OR component = '/blog/tree-hole');

COMMIT;
