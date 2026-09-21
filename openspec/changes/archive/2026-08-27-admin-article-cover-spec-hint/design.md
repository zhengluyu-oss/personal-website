## Context

博客聚合页（`/blog`）文章卡片封面容器为 **16:10**（`aspect-ratio: 16 / 10`），主图使用 `object-fit: contain`，比例不一致时会出现 letterbox 留白。作者已在 explore 阶段选定 **方案 C**：统一封面上传规格，而非改前台裁切逻辑。

管理后台文章发布页 `kuailemao-admin/src/pages/blog/essay/publish/index.vue` 当前仅有「上传封面」按钮，无 label 与规格说明。工作经历编辑页已有「建议比例 16:10」的文案先例。后端封面上传限制：JPG/PNG/WebP，压缩后 ≤0.3MB（`UploadEnum.ARTICLE_COVER` + `beforeUpload`）。

## Goals / Non-Goals

**Goals:**

- 在发布/编辑文章时，作者一眼可见封面推荐比例与像素尺寸。
- 文案与博客前台 16:10 列表卡片对齐。
- 复用 Ant Design Vue 表单项惯例（`label` + `extra`），改动范围最小。

**Non-Goals:**

- 上传时强制校验或裁剪比例（后续可选增强）。
- 修改 OSS、后端 API 或博客前台 CSS。
- 统一首页「近期写作」等非 16:10 区域的展示策略。

## Decisions

### 1. 规格常量

在 admin 侧集中定义（常量或小型 `article-cover-spec.ts`），避免魔法字符串散落：

| 字段 | 值 |
|------|-----|
| 比例 | 16:10 |
| 推荐尺寸 | 1600 × 1000 px |
| 备选 | 1280 × 800、1920 × 1200 |
| 格式 | JPG、PNG、WebP |
| 大小 | 压缩后 ≤ 0.3MB |

**理由**: 与 `Category/index.vue` 卡片比例一致；1600×1000 兼顾清晰度与现有压缩上限。

### 2. UI 呈现方式

使用 `a-form-item` 的 `label="文章封面"` 与 `extra` 展示多行说明；上传按钮与预览逻辑保持不变。

**备选**: Tooltip 仅 hover 可见 —— 拒绝，作者可能忽略。

**备选**: 独立 Alert 区块 —— 过重，与顶部表单项密度不符。

### 3. 编辑回显

路由带 `id` 时（`getFormData` 回显）同样展示规格说明，便于更换封面时参照。

### 4. 与 experience 页对齐

文案风格参考 `blog/experience/index.vue` 的「建议比例 16:10」，但文章封面需补充像素尺寸与格式/大小，因列表展示更敏感。

## Risks / Trade-offs

- **[Risk] 移动端推荐位为 16:8** → 严格 16:10 在部分 viewport 仍有轻微差异；说明中可一句带过「列表以 16:10 为准」。
- **[Risk] 作者忽略 extra 文案** → 后续可加 `beforeUpload` 比例 warning，本变更不阻断上传。
- **[Trade-off] 仅提示不强制** → 实现快，依赖作者自觉；符合当前 scope。

## Migration Plan

- 仅 admin 静态资源发布，无数据库迁移。
- 部署：构建并发布 `kuailemao-admin`（或全量 deploy 脚本）。
- 回滚：还原 `publish/index.vue` 即可。

## Open Questions

- 是否在本变更中增加「上传后 16:10 线框预览」—— 建议留作 follow-up，不在首版 scope。
