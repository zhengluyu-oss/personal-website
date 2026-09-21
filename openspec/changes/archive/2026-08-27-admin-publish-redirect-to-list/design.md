## Context

发布页 `kuailemao-admin/src/pages/blog/essay/publish/index.vue` 的 `onFinish` 在 `publishArticle` 成功后：

- **已有封面、未重新选文件**：只 `message.success`，留在当前页。
- **新上传封面**：成功后清空 `articleTitle` / `articleContent` 等，但编辑态 `formData.id` 仍在。再次发布会把空内容写回同一篇文章。

后台使用多页签；「关闭」按钮调用 `multiTab.close(route.fullPath)`，关闭后激活的是相邻页签，不一定是文章列表。列表路径为 `paths.articles`（`/articles`）。

`multiTab.close` 在只剩一个页签时会拒绝关闭。

## Goals / Non-Goals

**Goals:**

- 发布成功后进入文章列表，作者不再停留在可再次提交的编辑页。
- 新建与编辑两条成功路径行为一致。
- 失败（校验、封面上传、发布接口）仍留在当前页并提示。

**Non-Goals:**

- 不改后端 `publish` API 或封面上传。
- 不改「关闭」按钮语义（仍为关页签，不强制去列表）。
- 不做发布确认弹窗或草稿自动保存。

## Decisions

### 1. 成功后先跳列表再关编辑页签

```ts
message.success('发布成功')
const editingPath = route.fullPath
await router.push(paths.articles)
multiTab.close(editingPath)
```

**理由**：先 `push` 保证列表页签存在，再关编辑页签，避免 `close` 落到无关页签，也避免「最后一个页签不能关」。

**备选**：只 `router.push` 不关页签 —— 编辑页签仍在，用户可能点回去再发一次。拒绝。

**备选**：只 `multiTab.close` —— 不一定落到 `/articles`。拒绝。

### 2. 抽公共成功处理，覆盖两条 publish 分支

已有封面与新上传封面成功后都调用同一函数，去掉清空表单逻辑。

### 3. 提交中禁用「发布」按钮

用 `publishing` 标志，请求进行中禁止再次点击，避免跳转前的双击覆盖。

## Risks / Trade-offs

- **[Risk] close 在竞态下失败** → 仍已 `push` 到列表，作者不在编辑页；close 失败可忽略。
- **[Risk] 列表 keep-alive 未刷新** → 列表进入时若已有拉取逻辑则可见新文；否则需确认 `onActivated`/`onMounted`。实现时检查列表页，必要时 `multiTab.refresh` 列表路径。
- **[Trade-off] 不能在同一页连续发下一篇** → 符合用户要求；再发需点「发布文章」进 `/articles/new`。

## Migration Plan

- 仅 admin 前端发布。
- 回滚：还原 `onFinish` 成功回调即可。

## Open Questions

- 无。列表未自动刷新时在实现阶段补一次列表数据拉取，不阻塞本设计。
