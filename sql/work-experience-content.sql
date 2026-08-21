-- 工作经历增加 Markdown 正文（方案 A：列表摘要 + 详情正文）
SET NAMES utf8mb4;

-- 幂等：列已存在则跳过
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 't_work_experience'
    AND COLUMN_NAME = 'content'
);

SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE `t_work_experience` ADD COLUMN `content` mediumtext NULL COMMENT ''Markdown 详情正文（可含图片）'' AFTER `highlights`',
  'SELECT ''content column already exists'' AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
