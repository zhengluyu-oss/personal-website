-- 工作经历升级为作品案例，所有字段均可空以兼容历史数据
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_work_experience_case_column;
DELIMITER $$
CREATE PROCEDURE add_work_experience_case_column(
  IN column_name_value VARCHAR(64),
  IN column_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 't_work_experience'
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @alter_sql = CONCAT('ALTER TABLE `t_work_experience` ADD COLUMN `', column_name_value, '` ', column_definition);
    PREPARE stmt FROM @alter_sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL add_work_experience_case_column('project_summary', 'varchar(500) NULL COMMENT ''案例定位'' AFTER `highlights`');
CALL add_work_experience_case_column('cover_image', 'varchar(500) NULL COMMENT ''案例封面图'' AFTER `project_summary`');
CALL add_work_experience_case_column('tech_stack', 'text NULL COMMENT ''技术栈，每行一项'' AFTER `cover_image`');
CALL add_work_experience_case_column('responsibilities', 'text NULL COMMENT ''核心职责，每行一项'' AFTER `tech_stack`');
CALL add_work_experience_case_column('metrics', 'text NULL COMMENT ''量化成果，数值与说明用竖线分隔'' AFTER `responsibilities`');
DROP PROCEDURE add_work_experience_case_column;
