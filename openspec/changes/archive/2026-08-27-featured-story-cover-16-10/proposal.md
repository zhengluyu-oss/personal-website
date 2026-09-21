## Why

作者已按后台 16:10 规格上传封面后，博客列表卡片显示正常，但 `/blog` 顶部推荐位（Featured Story）封面区实际比例更宽（约 2:1），配合 `object-fit: contain` 导致 16:10 图片出现左右留白。用户选定方案 B：将推荐位封面容器也统一为 **16:10**，与封面上传规格及下方卡片一致，消除顶部左右 letterbox。

## What Changes

- 博客分类页 `Category/index.vue` 中 `.featured-story__cover` 固定 **aspect-ratio: 16 / 10**，移除依赖 `min-height` 的宽扁布局。
- 移除或替换移动端 `@media (max-width: 900px)` 下推荐位 **16:8** 比例，统一为 16:10。
- 按需微调 `.featured-story` 网格布局（封面列与文案列比例、间距），保证 16:10 封面与右侧内容在桌面/平板/手机仍协调可读。
- 保持 `.story-cover__image` 的 `object-fit: contain`（与列表卡片一致）；16:10 上传图在 16:10 容器内应无留白。
- 不改为 `cover` 裁切；不改后端 API 或上传流程。

## Capabilities

### New Capabilities

- `blog-featured-cover-layout`: 博客聚合页顶部推荐位封面容器使用与列表卡片一致的 16:10 比例，避免 16:10 封面的左右 letterboxing。

### Modified Capabilities

（无。admin 封面规格提示已声明列表与推荐位均为 16:10 展示；本变更使前台实现与既有文案对齐，不修改 admin spec 要求。）

## Impact

- **前端（blog）**: `blog-frontend/kuailemao-blog/src/views/Pigeonhole/Category/index.vue`（Featured Story 样式与响应式断点）。
- **无后端 / API / 数据库变更**。
- **视觉影响**: 桌面端推荐位封面变「不那么宽」，右侧文案区域相对占比可能略增；需验收各断点版式。
