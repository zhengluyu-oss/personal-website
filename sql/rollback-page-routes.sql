-- 仅在已执行 normalize-page-routes.sql 且备份表存在时使用。
START TRANSACTION;
DELETE role_menu FROM sys_role_menu role_menu
JOIN sys_menu introduced ON introduced.id=role_menu.menu_id
LEFT JOIN sys_menu_route_backup_20260824 backup_menu ON backup_menu.id=introduced.id
WHERE backup_menu.id IS NULL AND introduced.component IN ('/blog/photo','/blog/experience');
DELETE introduced FROM sys_menu introduced
LEFT JOIN sys_menu_route_backup_20260824 backup_menu ON backup_menu.id=introduced.id
WHERE backup_menu.id IS NULL AND introduced.component IN ('/blog/photo','/blog/experience');
DELETE current_menu FROM sys_menu current_menu
JOIN sys_menu_route_backup_20260824 backup_menu ON backup_menu.id=current_menu.id;
INSERT INTO sys_menu SELECT * FROM sys_menu_route_backup_20260824;
COMMIT;
