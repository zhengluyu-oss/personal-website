SET NAMES utf8mb4;
UPDATE t_work_experience
SET company = '示例科技有限公司',
    role_title = '后端开发实习生',
    highlights = '参与业务接口开发与联调\n协助排查线上问题与日志分析'
WHERE id = 1;
SELECT id, company, role_title FROM t_work_experience;
