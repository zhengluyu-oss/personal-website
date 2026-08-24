-- 清理后台已废弃的首页、接口文档和跳转前台菜单。
-- 可重复执行；先删除角色关联，再删除菜单本身。
START TRANSACTION;

DELETE FROM sys_role_menu
WHERE menu_id IN (21, 38, 42, 69, 70);

DELETE FROM sys_menu
WHERE id IN (21, 38, 42, 69, 70);

COMMIT;
