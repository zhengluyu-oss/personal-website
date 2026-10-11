-- 首页背景视频对象键；重复执行安全，旧记录 NULL 时继续显示图片。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS add_home_hero_video_column;
DELIMITER $$
CREATE PROCEDURE add_home_hero_video_column()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_website_info'
      AND COLUMN_NAME = 'hero_video_key'
  ) THEN
    ALTER TABLE `sys_website_info`
      ADD COLUMN `hero_video_key` varchar(255) NULL COMMENT '首页背景视频 OSS 对象键';
  END IF;
END$$
DELIMITER ;
CALL add_home_hero_video_column();
DROP PROCEDURE add_home_hero_video_column;

-- 回滚前先确认没有视频配置；需要回滚时执行：
-- ALTER TABLE `sys_website_info` DROP COLUMN `hero_video_key`;
