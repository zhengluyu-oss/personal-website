## Context

`blog-work-experience` 已落地：表 `t_work_experience`、公开列表 API、后台 CRUD、前台 `/experience` 时间线。当前字段仅公司/岗位/日期/纯文本 `highlights`，无法承载图文履历。用户选定方案 A：结构化元数据 + Markdown `content` + 列表/详情双页；图片复用文章 OSS 上传。约束：不改 `xyz.kuailemao` 包名与 `kuailemao-*` 目录名。

## Goals / Non-Goals

**Goals:**

- 经历条目可保存 Markdown 正文（含图片 URL）
- 后台用 MdEditor 编辑正文，插图走现有文章图片上传
- `/experience` 时间线只展示摘要级信息；详情 `/experience/:id` 渲染完整正文
- 公开详情仅返回启用记录；列表不携带完整 `content`（或等价裁剪）

**Non-Goals:**

- 不把工作经历改成普通博客文章类型
- 不新建独立图片上传服务/bucket 策略
- 不做评论、点赞、独立 SEO 文章体系
- 不强制迁移旧 `highlights` 进 `content`

## Decisions

1. **字段分工：`highlights` = 列表摘要；`content` = 详情正文**  
   - `ALTER` 增加 `content MEDIUMTEXT NULL`。  
   - 列表卡片用公司/岗位/日期/`highlights`；详情用 MdPreview 渲染 `content`。  
   - 备选「只用 content、列表截断」：截断 Markdown 易破坏语法，弃用。

2. **公开 API：list 裁剪 + `GET /experience/{id}`**  
   - list：不返回 `content`（或恒为空），减小 payload。  
   - detail：完整字段含 `content`；`status!=启用` 或不存在 → 404/业务错误。  
   - 备选「list 带全文」：履历图文体积大，弃用。

3. **图片：复用 `POST /article/upload/articleImage`（或现网等价）**  
   - 管理端编辑器 `onUploadImg` 与发文页对齐，插入 `![](url)`。  
   - 备选新建 `/experience/upload`：无收益，弃用。

4. **前台路由 `/experience/:id` + 独立 Detail 页**  
   - 时间线卡片可点击跳转；不在列表内联展开全文。  
   - 渲染组件与文章详情一致的 `MdPreview`。

5. **后台表单：在现有 experience 页内嵌编辑器**  
   - 不拆独立路由除非现有弹窗/抽屉放不下；优先抽屉或全页表单区加 MdEditor。  
   - 与文章发布共用编辑器依赖，避免新 Markdown 库。

## Risks / Trade-offs

- [列表误返回大段 Markdown] → Controller/VO 明确裁剪；联调检查响应。  
- [停用/不存在 id 被爬取] → 详情接口校验 status。  
- [仅前端发版未跑 ALTER] → 部署清单强制先 SQL。  
- [旧数据 content 为空] → 详情友好空状态，不报错。  
- [编辑器插图权限] → 沿用文章上传鉴权，管理员已有权限即可。

## Migration Plan

1. 执行 `sql/work-experience-content.sql`（ALTER `content`）。  
2. 发布 backend → admin → blog。  
3. 后台为至少一条经历填写 Markdown 并插图，验收列表→详情。  
4. 回滚：回退三端代码；列可保留（兼容空 content）。

## Open Questions

- 无阻塞项。`highlights` 继续作为列表摘要字段。
