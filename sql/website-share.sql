-- 独立的网站分享模块：先建表并复制旧文章；旧文章的清理须在新页面验收后另行执行。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `t_website_share` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_article_id` bigint DEFAULT NULL COMMENT '迁移自博客的文章 ID，用于旧链接跳转',
  `title` varchar(150) NOT NULL,
  `site_url` varchar(500) NOT NULL,
  `summary` varchar(500) NOT NULL,
  `cover_image` varchar(500) DEFAULT NULL,
  `content` mediumtext NOT NULL,
  `seo_title` varchar(70) DEFAULT NULL,
  `seo_description` varchar(200) DEFAULT NULL,
  `seo_keywords` varchar(200) DEFAULT NULL,
  `order_num` int NOT NULL DEFAULT 1,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 草稿，1 公开',
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_website_share_source_article` (`source_article_id`),
  KEY `idx_website_share_public` (`is_deleted`, `status`, `order_num`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='独立网站分享';

-- 本次明确迁移的旧文章：分类 21 的 IP138（文章 62）。条件不符时不复制其他文章。
INSERT INTO `t_website_share`
  (`source_article_id`, `title`, `site_url`, `summary`, `cover_image`, `content`,
   `seo_title`, `seo_description`, `seo_keywords`, `order_num`, `status`,
   `create_time`, `update_time`, `is_deleted`)
SELECT a.`id`, a.`article_title`, 'https://www.ip138.com/',
       'IP138 是一组实用的网络查询与开发工具；这篇分享介绍 IP 查询、开发工具及使用场景。',
       a.`article_cover`, a.`article_content`, a.`seo_title`, a.`seo_description`,
       a.`seo_keywords`, 1, IF(a.`status` = 1, 1, 0),
       a.`create_time`, a.`update_time`, 0
FROM `t_article` a
JOIN `t_category` c ON c.`id` = a.`category_id`
WHERE a.`id` = 62 AND a.`category_id` = 21 AND a.`article_title` = 'ip138.com'
  AND c.`category_name` = '网站分享' AND a.`is_deleted` = 0
  AND NOT EXISTS (SELECT 1 FROM `t_website_share` s WHERE s.`source_article_id` = a.`id`);

-- 后台菜单和管理员角色授权；按路径/权限键判断是否已存在，脚本可重复执行。
SET @share_menu_id = (SELECT `id` FROM `sys_menu` WHERE `path` = '/blog/website-share' LIMIT 1);
SET @share_menu_id = COALESCE(@share_menu_id, (SELECT COALESCE(MAX(`id`), 0) + 1 FROM `sys_menu`));
INSERT INTO `sys_menu`
  (`id`, `title`, `icon`, `path`, `component`, `redirect`, `affix`, `parent_id`,
   `name`, `hide_in_menu`, `url`, `hide_in_breadcrumb`, `hide_children_in_menu`,
   `keep_alive`, `target`, `is_disable`, `order_num`, `create_time`, `update_time`, `is_deleted`)
SELECT @share_menu_id, '网站分享', 'LinkOutlined', '/blog/website-share',
       '/blog/website-share', '', 0, 28, 'WebsiteShare', 0, '', 0, 1, 1,
       '', 0, 9, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE `path` = '/blog/website-share');

SET @permission_base = (SELECT COALESCE(MAX(`id`), 0) FROM `sys_permission`);
INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT @permission_base + 1, '网站分享列表', 'blog:websiteShare:list', @share_menu_id, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_key` = 'blog:websiteShare:list');
INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT @permission_base + 2, '新增网站分享', 'blog:websiteShare:add', @share_menu_id, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_key` = 'blog:websiteShare:add');
INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT @permission_base + 3, '修改网站分享', 'blog:websiteShare:update', @share_menu_id, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_key` = 'blog:websiteShare:update');
INSERT INTO `sys_permission` (`id`, `permission_desc`, `permission_key`, `menu_id`, `create_time`, `update_time`, `is_deleted`)
SELECT @permission_base + 4, '删除网站分享', 'blog:websiteShare:delete', @share_menu_id, NOW(), NOW(), 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_permission` WHERE `permission_key` = 'blog:websiteShare:delete');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`, `is_deleted`)
SELECT 1, @share_menu_id, 0
WHERE NOT EXISTS (SELECT 1 FROM `sys_role_menu` WHERE `role_id` = 1 AND `menu_id` = @share_menu_id AND `is_deleted` = 0);
INSERT INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT 1, p.`id` FROM `sys_permission` p
WHERE p.`permission_key` IN ('blog:websiteShare:list', 'blog:websiteShare:add',
                             'blog:websiteShare:update', 'blog:websiteShare:delete')
  AND NOT EXISTS (SELECT 1 FROM `sys_role_permission` rp WHERE rp.`role_id` = 1 AND rp.`permission_id` = p.`id`);
