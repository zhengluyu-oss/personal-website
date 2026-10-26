## MODIFIED Requirements

### Requirement: Deterministic validation reasons

系统 SHALL 为问答、选篇复盘和期间复盘提供固定结构/证据失败分类及安全字段路径，同时保留既有外层失败码。MUST 不降低来源真实性或原文一致性校验。对使用引文标识的新任务，系统 SHALL 单独标识未知、跨任务、错来源及错类型的引用错误；历史任务没有此标识时 MUST 保持原有诊断语义，不追溯推断原因。

#### Scenario: Different invalid outputs

- **WHEN** 输出分别有 JSON 错误、缺失字段、类型错误、超限、未知来源、改写引文或错误证据类型
- **THEN** 产生各自可识别的固定分类与路径，无成功报告，日志不含输入值或解析异常正文

#### Scenario: Invalid quote identifiers

- **WHEN** 新任务输出未知、跨任务、错来源或错类型的引文标识
- **THEN** 本人诊断显示固定错误分类和安全字段路径；未通过的输出仍遵守七天私密留存，不在普通日志中显示原文或标识所指内容

## ADDED Requirements

### Requirement: Metadata-only generation observability

系统 SHALL 为问答和两类复盘记录白名单内的任务元数据，包括任务标识、种类、处理阶段、固定错误码、耗时、已知用量及可能计费状态。普通运行日志 MUST 不含日记正文、来源快照、引文文本、提示词、模型回答、密钥、供应商原始响应或异常堆栈；观测故障 MUST 不改变任务状态、诊断留存或触发额外模型请求。

#### Scenario: Quote validation fails in production

- **WHEN** 本人一次手动任务因引文标识不合法而失败
- **THEN** 运维可从固定分类和任务元数据判断失败阶段，本人仍可在七天内通过既有私密诊断核对原回答，普通日志不暴露私人内容

#### Scenario: Logging backend is unavailable

- **WHEN** 审计日志不可写或日志采集失败
- **THEN** 不补发模型请求，不把私密内容写入其他日志，任务依既有状态与诊断规则完成或失败
