## ADDED Requirements

### Requirement: Article list prioritizes content identity
文章列表 SHALL 将封面与左对齐标题组合呈现，默认主要列为文章、分类、状态、访问量、发布时间及操作；编号、作者、类型、标签和置顶等辅助信息 MUST 仍可通过辅助区或显式更多入口访问。

#### Scenario: Operator scans articles
- **WHEN** 管理员打开文章列表
- **THEN** 标题作为主要阅读入口，访问量右对齐，日期显示完整年月日且完整时间可访问
- **AND** 页面提供明确的新建文章入口、行内编辑和更多操作，删除采用危险语义及中文确认

#### Scenario: Operator manages existing content
- **WHEN** 管理员筛选标题、分类、状态或置顶条件，切换分页或页大小，执行刷新、批量删除、单选编辑或状态/置顶更新
- **THEN** 操作使用现有接口和权限契约，并遵循现有分页重置、保留与有效页回退规则
- **AND** 查看前台入口保持使用现有域名和文章路径

#### Scenario: Operator uses narrow viewport
- **WHEN** 管理员在 390px 宽度访问文章列表
- **THEN** 筛选、分页、新建与行操作可触达，更多信息可通过点击访问，表格可内部横向滚动

### Requirement: Article editor separates writing and settings
文章编辑页 SHALL 以标题和正文为主要编辑区域，集中展示封面、分类、标签、类型、状态与置顶设置，并将 SEO 设置置于默认折叠区；提交和关闭操作 MUST 易于访问且不遮挡编辑内容。

#### Scenario: Author opens editor on desktop
- **WHEN** 作者在至少 1200px 宽的视口打开新建或编辑文章
- **THEN** 正文主体与发布设置侧栏分区显示，标题拥有充分输入宽度，SEO 可展开编辑并保留既有值
- **AND** 封面旁保留 16:10、1600×1000 px、JPG/PNG/WebP、压缩后不超过 0.3MB 的既有指导

#### Scenario: Author opens editor on narrow viewport
- **WHEN** 作者在小于 1200px 宽的视口编辑文章
- **THEN** 主体与设置改为上下布局，所有字段与提交操作可访问且页面无新增横向溢出

#### Scenario: Author submits or encounters an error
- **WHEN** 作者提交公开、私密或草稿文章，重复点击提交，或遇到校验、上传、发布失败
- **THEN** 页面遵循现有状态值、上传压缩和临时封面清理流程，阻止重复提交，失败保留内容并提示错误
- **AND** 成功后按既有规则返回文章列表并处理编辑标签，不新增自动保存或提交协议

### Requirement: Overview distinguishes unavailable data from zero
工作台 SHALL 沿用文章、评论、留言、经历指标及现有快捷路由，并区分加载中、真实零值和获取失败；MUST 为失败指标提供重试，且一个指标失败不得阻止其他指标显示成功数据。

#### Scenario: Overview partly fails
- **WHEN** 一个或多个统计请求失败，其他请求成功
- **THEN** 失败项显示不可用及重试入口，成功项显示实际数据，失败不得显示为零

#### Scenario: Overview returns empty data
- **WHEN** 请求成功且合法返回零条记录
- **THEN** 对应指标显示零，并保留新建文章、文章管理与互动管理等快捷入口

### Requirement: Content actions use consistent Chinese semantics
内容管理界面 SHALL 统一使用“置顶”“新建文章”“编辑”“查看前台”等清楚文案，确认及取消操作 SHALL 使用中文；普通修改和关闭操作 MUST 使用中性外观，尚未实现的评论导出入口 MUST 隐藏。

#### Scenario: Operator handles a comment
- **WHEN** 管理员打开评论管理并使用查看或删除操作
- **THEN** 页面不展示无效导出按钮，查看入口表意清楚，删除显示危险语义和中文确认

### Requirement: Workspace verification covers responsive and business regressions
交付前 MUST 在 1440、1280、768、390px 视口核验文章列表、编辑页、工作台与代表性管理页面，并验证键盘访问、加载、空数据及失败状态；MUST 记录真实验证结果及无法执行的检查。

#### Scenario: Implementation is accepted
- **WHEN** 实施者准备交付
- **THEN** 管理端类型检查、构建、分页测试及路由检查有可追溯结果，已核验关键文章操作与视口布局
- **AND** 若运行服务或登录态缺失，未完成的浏览器验证被明确列出，不宣称通过
