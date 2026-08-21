## Context
Vue SPA 当前仅在路由守卫设置 document.title，文章详情再覆盖标题，缺少 description/keywords。
## Goals / Non-Goals
**Goals:** 固定路由完整 TDK；文章可配置且有自动回退；路由切换不遗留旧 meta。
**Non-Goals:** 本次不引入 SSR、站点地图或结构化数据。
## Decisions
1. TDK 默认值写入路由 meta，集中、可审查且无需新增固定页面后台表。
2. 统一工具以 name 选择并更新 meta，避免重复标签。
3. 文章表新增 seo_title、seo_description、seo_keywords 三列，DTO 校验长度，详情 VO 返回。
4. 文章详情加载后覆盖路由默认 TDK；空值分别回退文章标题、正文纯文本摘要和标签。
## Risks / Trade-offs
- [SPA 爬虫兼容有限] → 保证运行时 TDK 正确，后续可独立引入 SSR/预渲染。
- [旧文章无字段] → 使用自动回退且迁移列可空。
