-- 首页首屏独立文案配置（幂等迁移）
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_home_hero_column;
DELIMITER $$
CREATE PROCEDURE add_home_hero_column(IN column_name_value VARCHAR(64), IN column_definition TEXT)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_website_info'
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @alter_sql = CONCAT('ALTER TABLE `sys_website_info` ADD COLUMN `', column_name_value, '` ', column_definition);
    PREPARE alter_stmt FROM @alter_sql;
    EXECUTE alter_stmt;
    DEALLOCATE PREPARE alter_stmt;
  END IF;
END$$
DELIMITER ;

CALL add_home_hero_column('hero_kicker', 'varchar(40) NULL COMMENT ''首页眉题''');
CALL add_home_hero_column('hero_title', 'varchar(60) NULL COMMENT ''首页主标题''');
CALL add_home_hero_column('hero_subtitle', 'varchar(100) NULL COMMENT ''首页副标题''');
CALL add_home_hero_column('hero_description', 'varchar(240) NULL COMMENT ''首页简介''');
CALL add_home_hero_column('hero_primary_text', 'varchar(20) NULL COMMENT ''首页主按钮文字''');
CALL add_home_hero_column('hero_primary_url', 'varchar(255) NULL COMMENT ''首页主按钮链接''');
CALL add_home_hero_column('hero_secondary_text', 'varchar(20) NULL COMMENT ''首页次按钮文字''');
CALL add_home_hero_column('hero_secondary_url', 'varchar(255) NULL COMMENT ''首页次按钮链接''');
CALL add_home_hero_column('hero_aside_label', 'varchar(30) NULL COMMENT ''首页侧栏标签''');
CALL add_home_hero_column('hero_aside_text', 'varchar(120) NULL COMMENT ''首页侧栏内容''');
DROP PROCEDURE add_home_hero_column;

UPDATE `sys_website_info`
SET `hero_kicker` = COALESCE(NULLIF(`hero_kicker`, ''), 'PERSONAL JOURNAL · SINCE 2024'),
    `hero_title` = COALESCE(NULLIF(`hero_title`, ''), '你好，我是郑陆宇'),
    `hero_subtitle` = COALESCE(NULLIF(`hero_subtitle`, ''), '一名持续构建、记录与分享的开发者'),
    `hero_description` = COALESCE(NULLIF(`hero_description`, ''), '在这里记录技术实践、项目复盘与成长轨迹，也分享那些值得被长期保存的思考。'),
    `hero_primary_text` = COALESCE(NULLIF(`hero_primary_text`, ''), '浏览文章'),
    `hero_primary_url` = COALESCE(NULLIF(`hero_primary_url`, ''), '/pigeonhole'),
    `hero_secondary_text` = COALESCE(NULLIF(`hero_secondary_text`, ''), '了解我'),
    `hero_secondary_url` = COALESCE(NULLIF(`hero_secondary_url`, ''), '/about'),
    `hero_aside_label` = COALESCE(NULLIF(`hero_aside_label`, ''), 'CURRENTLY'),
    `hero_aside_text` = COALESCE(NULLIF(`hero_aside_text`, ''), '专注于把想法变成真实、可靠且有温度的产品。')
WHERE `id` = 1;
