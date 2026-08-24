-- 页面路由迁移：仅修改后台菜单路由，不修改 API 或业务表结构。
-- 可重复执行。首次执行会保存原始菜单行，供 sql/rollback-page-routes.sql 使用。
START TRANSACTION;

CREATE TABLE IF NOT EXISTS sys_menu_route_backup_20260824 LIKE sys_menu;
INSERT IGNORE INTO sys_menu_route_backup_20260824
SELECT * FROM sys_menu
WHERE id IN (1,2,3,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,42,43,44,64,65,68,71)
   OR component IN ('/blog/photo','/blog/experience');

DELETE FROM sys_role_menu WHERE menu_id IN (21,38,42,69,70);
DELETE FROM sys_menu WHERE id IN (21,38,42,69,70);

UPDATE sys_menu SET path='/system', redirect='/system/menus' WHERE id=1;
UPDATE sys_menu SET path='/system/menus' WHERE id=2;
UPDATE sys_menu SET path='/system/users' WHERE id=3;
UPDATE sys_menu SET path='/system/roles' WHERE id=23;
UPDATE sys_menu SET path='/system/permissions' WHERE id=24;
UPDATE sys_menu SET path='/system/logs', redirect='/system/operations' WHERE id=25;
UPDATE sys_menu SET path='/system/operations' WHERE id=26;
UPDATE sys_menu SET path='/system/logins' WHERE id=27;
UPDATE sys_menu SET path='/site', redirect='/site/info' WHERE id=28;
UPDATE sys_menu SET path='/site/info' WHERE id=29;
UPDATE sys_menu SET title='编辑文章', path='/articles/:id', component='/blog/essay/publish', parent_id=28, hide_in_menu=1 WHERE id=30;
UPDATE sys_menu SET path='/articles/new', parent_id=28 WHERE id=31;
UPDATE sys_menu SET path='/articles', parent_id=28 WHERE id=32;
UPDATE sys_menu SET path='/tags' WHERE id=33;
UPDATE sys_menu SET path='/categories' WHERE id=34;
UPDATE sys_menu SET path='/comments' WHERE id=35;
UPDATE sys_menu SET path='/messages' WHERE id=36;
UPDATE sys_menu SET path='/tree-hole' WHERE id=37;
UPDATE sys_menu SET path='/links' WHERE id=39;
UPDATE sys_menu SET path='/collections' WHERE id=43;
UPDATE sys_menu SET path='/system/server' WHERE id=44;
UPDATE sys_menu SET path='/roles/:id' WHERE id=64;
UPDATE sys_menu SET path='/permissions/:id' WHERE id=65;
UPDATE sys_menu SET path='/users/:id' WHERE id=68;
UPDATE sys_menu SET path='/blacklist' WHERE id=71;

-- 后续功能可能由独立迁移创建，按组件定位可避免依赖环境中的自增 ID。
UPDATE sys_menu SET path='/photos' WHERE component='/blog/photo';
UPDATE sys_menu SET path='/experiences' WHERE component='/blog/experience';

INSERT INTO sys_menu (title,icon,path,component,redirect,affix,parent_id,name,hide_in_menu,url,hide_in_breadcrumb,hide_children_in_menu,keep_alive,target,is_disable,order_num,create_time,update_time,is_deleted)
SELECT '相册管理','PictureOutlined','/photos','/blog/photo','',0,28,NULL,0,NULL,1,1,1,'',0,8,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE component='/blog/photo' AND is_deleted=0);

INSERT INTO sys_menu (title,icon,path,component,redirect,affix,parent_id,name,hide_in_menu,url,hide_in_breadcrumb,hide_children_in_menu,keep_alive,target,is_disable,order_num,create_time,update_time,is_deleted)
SELECT '工作经历','ProfileOutlined','/experiences','/blog/experience','',0,28,NULL,0,NULL,1,1,1,'',0,9,NOW(),NOW(),0
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE component='/blog/experience' AND is_deleted=0);

INSERT INTO sys_role_menu (role_id,menu_id,is_deleted)
SELECT DISTINCT roots.role_id, pages.id, 0
FROM sys_role_menu roots
JOIN sys_menu pages ON pages.component IN ('/blog/photo','/blog/experience') AND pages.is_deleted=0
WHERE roots.menu_id=28 AND roots.is_deleted=0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=roots.role_id AND existing.menu_id=pages.id AND existing.is_deleted=0);

COMMIT;
