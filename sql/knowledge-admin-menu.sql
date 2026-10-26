-- Add the knowledge editor to the existing database-driven admin menu.
-- Only the owner account can use /knowledge/manage/**; granting this menu to
-- the ADMIN role provides navigation, not write access to other administrators.
START TRANSACTION;

INSERT INTO sys_menu
  (title, icon, path, component, redirect, affix, parent_id, name,
   hide_in_menu, url, hide_in_breadcrumb, hide_children_in_menu,
   keep_alive, target, is_disable, order_num, create_time, update_time, is_deleted)
SELECT '知识库管理', 'BookOutlined', '/knowledge', '/blog/knowledge', NULL, 0,
       parent.id, 'KnowledgeManage', 0, NULL, 0, 0, 1, NULL, 0, 8,
       NOW(), NOW(), 0
FROM sys_menu parent
WHERE parent.path = '/site' AND parent.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_menu existing WHERE existing.path = '/knowledge' AND existing.is_deleted = 0)
LIMIT 1;

INSERT INTO sys_role_menu (role_id, menu_id, is_deleted)
SELECT r.id, menu.id, 0
FROM sys_role r JOIN sys_menu menu ON menu.path = '/knowledge' AND menu.is_deleted = 0
WHERE r.role_key = 'ADMIN' AND r.is_deleted = 0
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu assigned WHERE assigned.role_id = r.id AND assigned.menu_id = menu.id AND assigned.is_deleted = 0);

COMMIT;
