-- 为手机端竖版背景视频增加独立对象键；重复执行安全。
SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS add_home_hero_mobile_video_column;
DELIMITER $$
CREATE PROCEDURE add_home_hero_mobile_video_column()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_website_info'
      AND COLUMN_NAME = 'hero_mobile_video_key'
  ) THEN
    ALTER TABLE `sys_website_info`
      ADD COLUMN `hero_mobile_video_key` varchar(255) NULL COMMENT '首页手机端背景视频 OSS 对象键';
  END IF;
END$$
DELIMITER ;
CALL add_home_hero_mobile_video_column();
DROP PROCEDURE add_home_hero_mobile_video_column;
