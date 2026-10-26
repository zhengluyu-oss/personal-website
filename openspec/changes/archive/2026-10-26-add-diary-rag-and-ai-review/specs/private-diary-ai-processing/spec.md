## ADDED Requirements

### Requirement: Independent feature configuration and cloud data disclosure
AI MUST 默认关闭，独立于日记开关；供应商、模型、维度、秘密和限额由后端外置配置提供。首次索引 SHALL 由本人主动启动，并说明发送给向量/生成服务的数据及已核实的供应商政策。复盘 MUST 始终手动发起。

#### Scenario: Missing model configuration
- **WHEN** AI 关闭或任一必要参数缺失
- **THEN** 系统不发送云端请求，清楚显示 AI 不可用，原日记 CRUD、历史、导出与公开博客仍可用

#### Scenario: Start the initial index
- **WHEN** 本人了解数据发送说明后启动首次索引
- **THEN** 系统有限回填当前已保存篇目并记录增量同步状态，后续可暂停，不自动触发行为复盘

### Requirement: Preserve private authorization and data boundaries
所有 AI 内容接口 MUST 位于本人日记认证边界内；本人检查、owner 派生、FORCE RLS、no-store/noindex 和日志脱敏 MUST 覆盖片段、向量、问题、报告及任务。客户端或模型 MUST 不能指定任意 owner、endpoint 或密钥。

#### Scenario: Anonymous or another administrator requests a report
- **WHEN** 匿名、其他普通账号或其他管理员直接请求索引、任务或报告
- **THEN** 匿名返回 401，非本人返回 403，不泄漏内容、任务存在性、数量或模型秘密

#### Scenario: Inspect observability and client storage
- **WHEN** 请求成功、参数错误、供应商异常或跨账号切换
- **THEN** 正文、问题、向量、模型回复、凭据及带参 SQL 不进入通用日志、公开审计队列、浏览器持久存储、URL 或公共表

### Requirement: Durable idempotent asynchronous jobs
系统 MUST 使用稳定 requestId 和完整请求摘要创建持久异步任务，相同请求复用任务、同 ID 不同参数拒绝。网络期间 MUST 不持有数据库事务或连接，运行并发 MUST 有持久约束。未知结果不得被自动重放为新付费请求。

#### Scenario: Retry after the accepted response is lost
- **WHEN** 客户端未收到 202 确认并用同一 requestId 重试
- **THEN** 系统返回原任务，不新建或重复生成

#### Scenario: Restart after a generation request was sent
- **WHEN** 后端在 RUNNING 状态重启且不能确定供应商是否完成
- **THEN** 任务转为 UNCERTAIN，显示可能已计费，不自动重发；本人可以明确另行重试

#### Scenario: Cancel a running task or switch accounts
- **WHEN** 本人取消任务或前台身份改变，随后旧模型响应到达
- **THEN** 旧响应不发布到当前会话，客户端不残留上一账号的私密结果

### Requirement: Enforce resource and invocation budgets
系统 MUST 使用有界 HTTP、JSON 大小、输入/输出、批次、队列和单运行任务限制，并在 PG 原子预留每日额度。首版默认每日生成尝试 20 次、embedding 输入 200 块，排队最多 5 个；额度和失败状态 MUST 真实展示，不把字符数或尝试次数冒充精确 token/金额。

#### Scenario: Concurrent requests exhaust the daily budget
- **WHEN** 多次并发请求竞争最后一个可用额度
- **THEN** 只有允许的数量取得预留，其余返回 429，不因竞争超发云端请求

#### Scenario: Slow or oversized provider response
- **WHEN** 服务连接超过 5 秒、单请求超过 30 秒或响应超过 4 MiB
- **THEN** 系统有界失败或标记未知，释放资源，原日记保存与 MySQL 博客保持可用

#### Scenario: Indexing reaches its daily limit
- **WHEN** 回填尚未完成但当天 embedding 额度已耗尽
- **THEN** 系统保存进度并显示暂停原因，不丢弃未处理篇目、不不断重试或假报全部完成

### Requirement: Isolated additive migrations and recoverable backups
AI 数据 MUST 位于独立 schema，扩展由管理员显式安装；应用启动不得迁移。新增结构 MUST 保留旧日记 schema 指纹与角色权限，完整加密备份/恢复 SHALL 包含派生结构、扩展依赖及 RLS。关闭 AI MUST 保留原文及历史。

#### Scenario: Repeat original and new migrations
- **WHEN** 在 PG16 应用 AI 迁移后再次执行旧日记结构复核及新迁移
- **THEN** 两者均通过；部分失败或结构漂移明确拒绝，不通过重写旧指纹掩盖问题

#### Scenario: Restore to an isolated database
- **WHEN** 在安装匹配 pgvector 的隔离环境解密并恢复完整备份
- **THEN** 原文、历史、幂等、向量 profile/维度、报告引用和 RLS 均可核对；未配置云端凭据不触发请求

#### Scenario: Disable AI after deployment
- **WHEN** 将 AI 独立开关关闭并重启
- **THEN** 后台云端处理停止，原日记与公开博客继续工作，不 drop 数据库、扩展或清理原文历史
