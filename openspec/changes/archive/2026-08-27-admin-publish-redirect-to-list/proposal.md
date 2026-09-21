## Why

管理后台「发布文章」成功后仍停留在当前页：新建封面路径会清空标题/正文等字段但保留文章 `id`，再次点击发布会用空白内容覆盖刚保存的文章。作者需要发布成功后回到文章列表，避免误操作覆盖。

## What Changes

- 发布成功后导航到文章列表（`/articles`），并关闭当前发布/编辑页签。
- 移除发布成功后清空表单字段的逻辑（离开页面后不再需要）。
- 新建文章与编辑已有文章两条成功路径行为一致；发布失败仍留在当前页。
- 不改变发布 API、封面上传或校验规则。

## Capabilities

### New Capabilities

- `admin-article-publish-navigation`: 管理后台文章发布成功后离开编辑页并进入文章列表，防止二次发布覆盖已保存内容。

### Modified Capabilities

（无。）

## Impact

- **前端（admin）**: `blog-frontend/kuailemao-admin/src/pages/blog/essay/publish/index.vue` 的 `onFinish` 成功回调。
- 可能调用已有 `multiTab.close` 与路由 `paths.articles`（`/articles`）。
- **无后端 / API / 数据库变更**。
