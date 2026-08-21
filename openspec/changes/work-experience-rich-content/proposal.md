## Why

当前「工作经历」只有结构化元数据与纯文本要点，无法承载真实履历中的项目说明、成果图与排版内容，对求职展示偏弱。用户已选定方案 A：在保留时间线元数据的前提下，增加 Markdown 正文与详情页，使经历可图文并茂地展示。

## What Changes

- 为 `t_work_experience` 增加 Markdown `content` 字段（可含图片链接）
- 后台经历编辑接入与发文一致的 Markdown 编辑器，并复用文章图片上传到 OSS
- 公开 API：列表接口不返回完整正文；新增按 id 的详情接口返回完整 `content`（仅启用项）
- 前台 `/experience` 时间线保留摘要信息，点击进入 `/experience/:id` 用 MdPreview 渲染正文与图片
- 兼容已有数据：`content` 为空时详情页展示友好空状态或仅摘要

## Capabilities

### New Capabilities

- （无）本次为既有能力增强，不引入新 capability 名

### Modified Capabilities

- `work-experience`: 从「仅结构化时间线」扩展为「元数据 + Markdown 正文 + 列表/详情双页」；公开读模型与后台编辑能力相应调整

## Impact

- 数据库：`ALTER` 增加 `content`（MEDIUMTEXT/LONGTEXT）
- 后端：Entity/DTO/VO、`WorkExperienceController` 公开详情与列表字段裁剪
- 管理端：`pages/blog/experience` 接入 MdEditor + 图片上传
- 前台：Experience 列表交互、新详情页与路由、API 封装
- 部署：执行迁移 SQL 后发布 backend / admin / blog；不改包名与 `kuailemao-*` 目录名
