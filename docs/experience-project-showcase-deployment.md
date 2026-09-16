# 工作经历项目展示：上线与回滚

## 上线顺序

1. 在目标数据库执行 `sql/experience-project-showcase.sql`。该脚本只新增两个可空字段和一张项目表，可重复执行。
2. 确认 `t_work_experience` 旧数据数量与内容未变化，并确认 `t_experience_project` 默认为空。
3. 发布后端，再发布管理后台和博客前台。
4. 在后台为经历补充公司介绍、主营业务和项目；项目确认无误后再从“草稿”切换为“发布”。
5. 匿名访问经历列表、公司详情和项目详情，检查草稿项目不会公开。

## 验证查询

```sql
SHOW COLUMNS FROM t_work_experience LIKE 'company_introduction';
SHOW COLUMNS FROM t_work_experience LIKE 'main_business';
SHOW CREATE TABLE t_experience_project;
SELECT COUNT(*) FROM t_work_experience WHERE is_deleted = 0;
```

## 回滚

先回滚三个应用版本。数据库新增字段均可保留，不影响旧版本；如必须清理，确认项目内容不再需要后执行：

```sql
DROP TABLE IF EXISTS t_experience_project;
ALTER TABLE t_work_experience DROP COLUMN company_introduction, DROP COLUMN main_business;
```

生产环境回滚前应先导出 `t_experience_project`，避免丢失后台已录入的项目内容。
