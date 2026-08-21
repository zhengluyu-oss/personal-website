## Why
固定页面目前只修改浏览器标题，没有 description 与 keywords；文章页也无法在后台独立配置 TDK，影响搜索结果表达和长期 SEO 管理。
## What Changes
- 为所有公开固定路由设置明确的默认 TDK。
- 增加统一的路由元信息同步器，维护 title、description、keywords。
- 文章增加可选 SEO 标题、描述和关键词，后台发布/编辑时可填写。
- 文章 SEO 留空时由标题、正文摘要和标签安全回退。
## Capabilities
### New Capabilities
- `page-article-tdk`: 固定页面默认 TDK 与文章后台可配置 TDK。
### Modified Capabilities

## Impact
- 博客路由与文章详情、文章数据表及接口模型、后台文章编辑表单。
