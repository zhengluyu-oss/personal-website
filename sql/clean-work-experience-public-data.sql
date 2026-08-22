-- 清理工作经历公开数据，并保证当前任职优先
SET NAMES utf8mb4;

UPDATE `t_work_experience`
SET `end_date` = NULL
WHERE `is_current` = 1
  AND `end_date` IS NOT NULL;

UPDATE `t_work_experience`
SET `highlights` = NULL
WHERE TRIM(COALESCE(`highlights`, '')) IN ('不应出现在前台', '测试', 'test');

UPDATE `t_work_experience`
SET `content` = NULL
WHERE TRIM(COALESCE(`content`, '')) IN ('## 机密正文', '机密正文', '测试', 'test');

UPDATE `t_work_experience`
SET `order_num` = 0
WHERE `is_current` = 1
  AND `is_deleted` = 0;
