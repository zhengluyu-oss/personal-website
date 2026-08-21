-- 工作经历表 + 管理端菜单/权限（方案 A）
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_work_experience` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `company` varchar(100) NOT NULL COMMENT '公司名称',
  `role_title` varchar(100) NOT NULL COMMENT '岗位',
  `start_date` date NOT NULL COMMENT '开始日期',
  `end_date` date NULL DEFAULT NULL COMMENT '结束日期（至今可空）',
  `is_current` tinyint NOT NULL DEFAULT 0 COMMENT '是否至今（0否 1是）',
  `highlights` text NULL COMMENT '职责要点，每行一条（列表摘要）',
  `content` mediumtext NULL COMMENT 'Markdown 详情正文（可含图片）',
  `order_num` int NOT NULL DEFAULT 1 COMMENT '排序，越小越靠前',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态（0停用 1启用）',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除（0否 1是）',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作经历';

-- 菜单：网站管理(28) 下「工作经历」
INSERT INTO `sys_menu` (`id`, `title`, `icon`, `path`, `component`, `redirect`, `affix`, `parent_id`, `name`, `hide_in_menu`, `url`, `hide_in_breadcrumb`, `hide_children_in_menu`, `keep_alive`, `target`, `is_disable`, `order_num`, `create_time`, `update_time`, `is_deleted`)
SELECT 80, '工作经历', 'ScheduleOutlined', '/blog/experience', '/blog/experience', '', 0, 28, 'WorkExperience', 0, '', 1, 1, 1, '', 0, 8, NOW(), NOW(), 0
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `id` = 80);

INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT 160, '工作经历列表', 'blog:experience:list', 80, NOW(), NOW(), 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = 160);
INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT 161, '新增工作经历', 'blog:experience:add', 80, NOW(), NOW(), 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = 161);
INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT 162, '修改工作经历', 'blog:experience:update', 80, NOW(), NOW(), 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = 162);
INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT 163, '删除工作经历', 'blog:experience:delete', 80, NOW(), NOW(), 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `id` = 163);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `is_deleted`)
SELECT 1, 80, 0 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = 80 AND `is_deleted` = 0);

INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, 160 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` WHERE `role_id` = 1 AND `permission_id` = 160);
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, 161 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` WHERE `role_id` = 1 AND `permission_id` = 161);
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, 162 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` WHERE `role_id` = 1 AND `permission_id` = 162);
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, 163 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `sys_role_permission` WHERE `role_id` = 1 AND `permission_id` = 163);
