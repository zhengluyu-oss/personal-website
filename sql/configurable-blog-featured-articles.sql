-- 博客聚合页与分类页主推荐配置（向前迁移）
-- 两个字段均可为空，旧数据无需回填；公开页面会自动回退到最新公开文章。

ALTER TABLE `sys_website_info`
    ADD COLUMN `blog_featured_article_id` BIGINT NULL DEFAULT NULL COMMENT '博客聚合页主推荐文章id' AFTER `website_name`,
    ADD INDEX `idx_blog_featured_article_id` (`blog_featured_article_id`);

ALTER TABLE `t_category`
    ADD COLUMN `featured_article_id` BIGINT NULL DEFAULT NULL COMMENT '分类页主推荐文章id' AFTER `category_name`,
    ADD INDEX `idx_featured_article_id` (`featured_article_id`);

-- 回滚语句（仅在应用版本已经回滚后执行）
-- ALTER TABLE `t_category` DROP INDEX `idx_featured_article_id`, DROP COLUMN `featured_article_id`;
-- ALTER TABLE `sys_website_info` DROP INDEX `idx_blog_featured_article_id`, DROP COLUMN `blog_featured_article_id`;
