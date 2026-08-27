-- 分类级博客 Hero 文案（向前迁移）
-- 字段均可为空；旧分类无需回填，前端会逐字段回退到博客默认文案。

ALTER TABLE `t_category`
    ADD COLUMN `hero_eyebrow` VARCHAR(60) NULL DEFAULT NULL COMMENT '分类页Hero眉题' AFTER `featured_article_id`,
    ADD COLUMN `hero_title_accent` VARCHAR(60) NULL DEFAULT NULL COMMENT '分类页Hero主标题强调段' AFTER `hero_eyebrow`,
    ADD COLUMN `hero_title` VARCHAR(60) NULL DEFAULT NULL COMMENT '分类页Hero主标题后半段' AFTER `hero_title_accent`,
    ADD COLUMN `hero_description` VARCHAR(240) NULL DEFAULT NULL COMMENT '分类页Hero简介' AFTER `hero_title`;

-- 回滚语句（仅在应用版本已经回滚后执行）
-- ALTER TABLE `t_category`
--     DROP COLUMN `hero_description`,
--     DROP COLUMN `hero_title`,
--     DROP COLUMN `hero_title_accent`,
--     DROP COLUMN `hero_eyebrow`;
