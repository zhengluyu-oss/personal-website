## Why

博客聚合页的分类按钮目前会进入独立分类页面，打断连续浏览体验，而且以数据库 ID 组成的 `/blog/categories/:id` URL 缺少可读性。分类筛选应当留在聚合页内完成，同时让路由表达当前主题并可被刷新、收藏和分享。

## What Changes

- 点击聚合页分类后保留现有刊头、分类导航和文章区域，仅在原位置切换为该分类的全部文章。
- 分类切换时同步更新 URL、选中状态、加载状态、空状态与页面元数据，不再渲染独立分类详情版式。
- **BREAKING**：用 `/blog/:slug` 取代 `/blog/categories/:id`；不保留旧分类路由或兼容跳转。
- 为分类生成稳定的短 slug：中文名称取每个汉字拼音首字母，英文名称取第一个英文单词并小写；混合名称按中文首字母与首个英文词的出现顺序组合，并规范化为 URL 安全值。
- 当自动生成的 slug 冲突、为空或命中保留路径时，使用确定性的分类 ID 后缀消歧；URL 解析后仍使用既有分类 ID 调用后端接口。
- 更新桌面导航、移动导航、文章详情分类入口和其他内部链接，使其统一产生新的分类 URL。

## Capabilities

### New Capabilities
- `inline-blog-category-filter`: 定义聚合页内分类筛选、状态展示、路由驱动与浏览器前进后退行为。
- `category-semantic-slugs`: 定义分类名称到稳定短 slug 的转换、冲突处理和反向解析规则。

### Modified Capabilities
- `canonical-page-routing`: 将分类规范路由从 `/blog/categories` 与 `/blog/categories/:id` 调整为聚合入口 `/blog` 和分类筛选 `/blog/:slug`，并要求所有内部入口使用新路由。
- `dynamic-blog-navigation`: 将桌面与移动端动态分类子导航从数据库 ID 地址调整为共享生成的语义化 slug 地址。

## Impact

- 主要影响前台路由表、路由常量、博客聚合页、分类导航 composable、桌面与移动导航、文章详情分类入口及路由审计脚本。
- 不修改 Java 后端 API、数据库结构、分类数据结构或文章查询接口。
- 可能需要在前端实现轻量中文拼音首字母转换；应优先复用现有依赖，若项目无可用能力则采用项目内可测试的转换工具，避免引入大型运行时依赖。
- 旧 `/blog/categories` 和 `/blog/categories/:id` 按既有“不兼容旧路由”原则进入 404。
