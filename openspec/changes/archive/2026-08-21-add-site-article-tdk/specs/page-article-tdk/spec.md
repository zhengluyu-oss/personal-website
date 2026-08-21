## ADDED Requirements
### Requirement: 固定页面 TDK
系统 SHALL 在每次公开固定路由切换时设置独立的 title、description 和 keywords。
#### Scenario: 访问固定页面
- **WHEN** 用户进入首页、归档、分类、标签、经历、相册、友链或关于页面
- **THEN** 文档 SHALL 包含与该页面用途匹配的完整 TDK
### Requirement: 文章 TDK 可配置
系统 SHALL 允许管理员在文章发布和编辑时填写 SEO 标题、描述与关键词。
#### Scenario: 文章配置 TDK
- **WHEN** 文章详情加载且存在自定义 TDK
- **THEN** 文档 SHALL 使用文章自定义 TDK
#### Scenario: 文章未配置 TDK
- **WHEN** 任一文章 SEO 字段为空
- **THEN** 系统 SHALL 从文章标题、正文摘要或标签生成对应回退值
