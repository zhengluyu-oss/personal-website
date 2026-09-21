## Context

博客 `/blog` 页（`Pigeonhole/Category/index.vue`）有两处封面展示：

| 区域 | 当前容器比例 | 图片 fit |
|------|-------------|----------|
| 列表卡片 `.article-card__cover` | 固定 `16/10` | `contain` |
| 推荐位 `.featured-story__cover` | 无固定比例，`min-height: 20rem` + 网格列宽；移动端 `16/8` | `contain` |

作者按 admin 提示上传 16:10 封面后，列表正常，推荐位因容器更宽而出现左右留白。用户选择 **方案 B**：统一推荐位为 16:10，而非改用 `cover` 裁切。

## Goals / Non-Goals

**Goals:**

- 推荐位封面容器在所有断点使用 **16:10**，与列表卡片及封面上传规格一致。
- 16:10 图片在推荐位无左右（及上下）letterbox（在 `contain` 前提下）。
- 推荐位整体版式（封面 + 文案双栏）在各 viewport 仍可读、美观。

**Non-Goals:**

- 将 `object-fit` 改为 `cover`。
- 修改后台上传、API 或 admin 规格文案。
- 调整首页其他模块（如 `WritingArticleList` 已用 `cover` 的区域）。

## Decisions

### 1. 固定推荐位封面比例为 16:10

```css
.featured-story__cover {
  aspect-ratio: 16 / 10;
  min-height: 0; /* 移除 min-height: 20rem，避免比例被撑破 */
}
```

**理由**: 与 `.article-card__cover` 及 admin `ARTICLE_COVER_ASPECT_RATIO` 对齐。

**备选**: 保持 `min-height` 仅加 `aspect-ratio` —— 两者冲突时浏览器行为不确定，应移除 `min-height`。

### 2. 移动端统一 16:10，移除 16:8

删除 `@media (max-width: 900px)` 中 `.featured-story__cover { aspect-ratio: 16 / 8 }`，改为 `16 / 10` 或不重复声明（继承桌面规则）。

**理由**: 16:8 比 16:10 更宽，是移动端左右留白的主要来源之一。

### 3. 网格布局微调

桌面端当前 `grid-template-columns: minmax(0, 1.15fr) minmax(18rem, .85fr)` 在封面变「不那么宽」后，可酌情调整为更接近 **1:1** 或 **1.05fr : 1fr**，避免封面列过窄导致推荐位整体过高。

**备选**: 小屏改为单列堆叠（已有 `@media` 将 `featured-story` 设为 `1fr`），封面全宽 16:10 在上、文案在下。

### 4. 保持 contain 与 backdrop

`.story-cover__image` 继续 `object-fit: contain`；`.story-cover__backdrop` 继续 `cover` 作模糊底，无 16:10 图时 letterbox 区域仍有视觉填充。

## Risks / Trade-offs

- **[Risk] 桌面推荐位视觉变「竖」** → 验收后微调 `grid-template-columns` 或 `gap`。
- **[Risk] 非 16:10 旧封面仍可能留白** → 与列表行为一致，依赖作者按规格上传；不在本变更强制裁切。
- **[Trade-off] 推荐位高度可能增加** → 单列移动端封面全宽时更明显，可接受。

## Migration Plan

- 仅 blog 前端静态资源发布。
- 部署 `kuailemao-blog` 或全量 deploy。
- 回滚：还原 `Category/index.vue` 样式即可。

## Open Questions

- 桌面端网格列比例是否需 A/B（如 1:1 vs 1.15:0.85）—— 实现时在浏览器中目视选定，无产品额外输入则默认略收窄封面列（如 `1fr 1fr`）。
