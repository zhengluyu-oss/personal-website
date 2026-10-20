## ADDED Requirements

### Requirement: Deterministic validation reasons
系统 SHALL 为问答、选篇复盘和期间复盘提供固定结构/证据失败分类及安全字段路径，同时保留既有外层失败码。MUST 不降低来源真实性或原文一致性校验。

#### Scenario: Different invalid outputs
- **WHEN** 输出分别有 JSON 错误、缺失字段、类型错误、超限、未知来源、改写引文或错误证据类型
- **THEN** 产生各自可识别的固定分类与路径，无成功报告，日志不含输入值或解析异常正文

### Requirement: Private seven day retention
系统 SHALL 仅在有效任务校验失败时将受限模型回答与来源快照存入本人 RLS 隔离的 PG 诊断资源，七天到期，最多100份，raw最大128KiB且来源最大512KiB；超限或持久化失败 MUST 明确未完整保存，不通过日志兜底。

#### Scenario: Failed review is retained
- **WHEN** 有效复盘收到不合格回答
- **THEN** 任务保持失败，诊断与状态受同一事务及租约约束，仅保存七天且不重调模型

#### Scenario: Late response or unavailable storage
- **WHEN** 任务已取消/失效或保存诊断的数据库不可用
- **THEN** 不发布回答、不复活任务、不自动重发，不泄露正文且不谎称留存成功

### Requirement: Owner only diagnostic inspection
系统 SHALL 仅允许本人按需查看或幂等删除诊断正文，普通阅读权限、其他管理员或匿名 MUST 无权访问。响应 MUST no-store/noindex，正文作为不可信纯文本展示，不存浏览器持久缓存。

#### Scenario: Viewer requests diagnostic
- **WHEN** 非本人请求有效任务诊断
- **THEN** 返回受控拒绝且不暴露内容、原因或存在性细节

#### Scenario: Owner opens failed output
- **WHEN** 本人展开诊断
- **THEN** 清楚区分固定失败原因、未通过校验的输出、对应来源及到期时间，离开或会话失效清空私密状态

### Requirement: Expiration deletion and source invalidation
系统 MUST 在七天到期时立即停止提供正文，并独立于模型启用状态限量清理。本人删除、来源删除或版本变化 MUST 使关联输出和快照不可读取且清理，不可由迟到任务重新写入。

#### Scenario: Expired or deleted diagnostic
- **WHEN** 诊断到期、本人提前删除或其来源变更
- **THEN** 不再返回 raw 或快照，返回安全可区分状态；反复清理/删除不改变成功报告或重复计费

### Requirement: Backward compatibility and billing
系统 MUST 将历史缺失诊断标为未记录，不推断具体原因。轮询、查看、删除和清理 MUST 不调用模型、不改变已有费用记录或幂等任务规则。

#### Scenario: Historical task is read
- **WHEN** 查看旧 OUTPUT_EVIDENCE_INVALID 任务
- **THEN** 显示历史未记录具体原因，不补造回答，不自动重试或声称费用退回
