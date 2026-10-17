# reliable-diary-ai-responses Specification

## Purpose
定义日记 AI 生成的可选 JSON 模式、紧凑任务提示、响应分类和共享生命周期保护，在保持预算与证据校验约束的前提下提高回答可靠性。

## Requirements

### Requirement: Optional provider JSON output without budget expansion
系统 SHALL 通过后端外置配置显式选择生成JSON模式；未配置时保持原请求兼容。启用时 SHALL 发送 `response_format.type=json_object` 并在提示中要求JSON；向量请求 MUST 不携带生成专属字段。现有模型、非思考设置、2000 token输出上限、5秒连接、30秒总时限、4 MiB响应及每日/队列预算 MUST 保持不增加。

#### Scenario: DeepSeek JSON mode is enabled
- **WHEN** 当前受支持生成供应商配置启用JSON模式
- **THEN** 请求包含JSON模式、明确JSON提示、原模型及输出预算，并继续使用禁用思考配置；结果仍通过本地业务和证据校验

#### Scenario: Another provider keeps its existing configuration
- **WHEN** JSON模式配置缺省或关闭
- **THEN** 生成请求不新增response_format字段，向量请求始终不携带该字段，不因兼容失败自动另发请求

### Requirement: Compact task-specific grounded output
系统 SHALL 为问答、日记复盘及期间回顾使用任务专用紧凑提示，并更新提示版本。问答 MUST 优先用answer回答问题并避免在其他数组重复分析；两类复盘 MUST 保留观察、推测、建议和局限。系统 MUST 不通过截断完整范围输入、伪造引用、丢弃失败字段或放宽校验来制造成功；字符指导 MUST 不被宣称为精确token保证。

#### Scenario: A question asks for a month summary and positive and negative experiences
- **WHEN** 固定虚构资料包含跨日记录，本人提出月度经历及满意/不满意的问题
- **THEN** 生成请求要求精简且直答、有真实短引文和资料局限的answer，限制重复字段；回答不宣称Top-K材料等于完整月报，不虚构逐日次数

#### Scenario: A complete diary or period review is requested
- **WHEN** 完整范围预览和预算检查通过
- **THEN** 模型仍获得该范围的完整合法来源，输出数量/篇幅受提示约束，保留原证据类型与完整来源有效性检查

### Requirement: Explicit generation response failure categories
系统 MUST 在响应边界区别输出截断、空正文、非法响应结构、非正常停止、实际工具调用以及证据验证失败。`finish_reason=length` MUST 判为截断，不发布部分结果。无工具调用的null字段或空tool_calls数组 MUST 不被误判为实际调用；非空工具调用或畸形字段 MUST 仍被拒绝。

#### Scenario: The provider reaches its output limit
- **WHEN** HTTP成功返回但finish_reason为length，即使content看似完整JSON
- **THEN** 任务以明确的生成截断原因失败，不创建成功报告，不尝试续写或再次生成

#### Scenario: The provider returns empty text or a malformed envelope
- **WHEN** stop响应没有有效非空字符串正文，或choices/usage等协议字段畸形
- **THEN** 系统分别返回空响应或协议异常原因，不输出供应商正文，不混称为引用校验失败

#### Scenario: Tool fields are nullable or contain real calls
- **WHEN** 一份合法文本响应仅带null工具字段或空tool_calls数组，另一份响应带实际工具调用
- **THEN** 前者可继续内容校验，后者安全失败，均不执行工具或外部操作

#### Scenario: The model invents a quotation
- **WHEN** 正常终止的结构化响应包含未知来源或不匹配的逐字引文
- **THEN** 任务保留证据校验失败原因且没有可读成功报告，不通过模糊匹配替换引用

### Requirement: Shared lifecycle protections apply to all generation kinds
问答、原文复盘及期间回顾 MUST 共享现有租约、幂等、认证和预算保护。诊断或格式修复 MUST 不自动重试云端、放宽源版本检查、占用数据库连接等待网络，或发布已取消/过期任务的迟到结果。AI失败 MUST 不影响原日记可靠保存。

#### Scenario: A request fails after being accepted
- **WHEN** 已返回202的任一生成任务被供应商拒绝、截断或在网络中断后终止
- **THEN** 查询任务仅返回状态，后台不再次发起同一付费调用，预留尝试不自动返还

#### Scenario: A source changes or a task is canceled
- **WHEN** 生成期间本人修改来源或取消任务，随后收到有效响应
- **THEN** 仍执行原有STALE/CANCELED处理，不发布结果，日记草稿与保存功能继续可用
