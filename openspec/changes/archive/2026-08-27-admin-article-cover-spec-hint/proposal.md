## Why

博客列表与推荐位使用固定 **16:10** 封面容器，而作者上传的封面比例不一致时，前台会出现上下或左右留白。用户已决定统一封面上传规格；需要在管理后台「发布/编辑文章」处明确告知推荐比例与像素尺寸，减少误传与反复调整。

## What Changes

- 在管理后台文章发布页（`essay/publish`）的封面上传区域增加可见规格说明。
- 说明内容包括：宽高比 **16:10**、推荐尺寸 **1600×1000 px**、可选尺寸、支持格式（JPG/PNG/WebP）、压缩后大小上限（≤0.3MB，与现有校验一致）。
- 为封面上传表单项补充 `label`（如「文章封面」），与工作经历等页面的提示风格保持一致。
- 不改变上传 API、后端校验或前台展示逻辑（本变更仅 admin UX 提示）。

## Capabilities

### New Capabilities

- `admin-article-cover-spec-hint`: 管理后台文章发布/编辑时展示统一的封面比例与尺寸指引，并与博客前台 16:10 列表卡片对齐。

### Modified Capabilities

（无。不修改既有 OpenSpec 能力的行为要求。）

## Impact

- **前端（admin）**: `blog-frontend/kuailemao-admin/src/pages/blog/essay/publish/index.vue`（封面上传 UI）。
- **无后端 / 数据库 / API 变更**。
- **无博客前台变更**（用户统一规格后，现有 `object-fit: contain` 列表将自然减少留白；可选后续变更不在本 scope）。
