SET NAMES utf8mb4;
DROP PROCEDURE IF EXISTS add_article_tdk_column;
DELIMITER $$
CREATE PROCEDURE add_article_tdk_column(IN column_name_value VARCHAR(64), IN column_definition TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_article' AND COLUMN_NAME = column_name_value) THEN
    SET @sql = CONCAT('ALTER TABLE `t_article` ADD COLUMN `', column_name_value, '` ', column_definition);
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
CALL add_article_tdk_column('seo_title', 'varchar(70) NULL COMMENT ''SEO标题'' AFTER `article_title`');
CALL add_article_tdk_column('seo_description', 'varchar(200) NULL COMMENT ''SEO描述'' AFTER `seo_title`');
CALL add_article_tdk_column('seo_keywords', 'varchar(200) NULL COMMENT ''SEO关键词'' AFTER `seo_description`');
DROP PROCEDURE add_article_tdk_column;
