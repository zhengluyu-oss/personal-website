# homepage-professional-proof Specification

## Purpose
TBD - created by archiving change strengthen-homepage-personal-identity. Update Purpose after archive.
## Requirements
### Requirement: 首页使用真实内容呈现专业证明
系统 SHALL 仅使用现有公开文章、推荐文章、启用的工作经历和站点信息构成首页专业证明，MUST NOT 生成或暗示后台数据中不存在的项目、职责、客户、指标或成果。

#### Scenario: 现有内容足以形成专业证明
- **WHEN** 首页取得有效的公开文章和启用工作经历
- **THEN** 页面展示可识别的技术主题、职业角色或成果摘要，并为每项内容提供通向其既有公开页面的入口

#### Scenario: 可用内容数量不足
- **WHEN** 文章或工作经历少于设计展示数量
- **THEN** 页面缩减对应项目数量或隐藏无数据小节，且不使用虚构占位内容补足数量

### Requirement: 专业证明选择可预测且不重复
系统 SHALL 使用确定性规则从现有有效数据中选择代表内容，并 SHALL 避免同一文章同时占用推荐位和相邻最新内容位。

#### Scenario: 推荐文章也存在于最新文章中
- **WHEN** 同一有效文章同时由推荐接口和最新文章接口返回
- **THEN** 页面仅在推荐位置展示该文章，并继续从最新数据中选择其他文章

#### Scenario: 推荐内容不可用
- **WHEN** 推荐文章为空、失效或请求失败
- **THEN** 页面使用有效最新文章作为降级内容，且其他首页区块仍保持可用

### Requirement: 专业证明支持快速核验
系统 SHALL 为代表文章和经历展示足以判断内容性质的真实元数据，包括适用的标题、分类或技术主题、公司、职位、时间和摘要，并使用既有唯一详情路由。

#### Scenario: 招聘者查看代表内容
- **WHEN** 招聘者扫描首页专业证明区
- **THEN** 无需打开详情即可区分技术文章与职业经历，并可通过一个明确入口进入对应详情页

