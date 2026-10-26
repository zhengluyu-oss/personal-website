## MODIFIED Requirements

### Requirement: Compact task-specific grounded output

系统 SHALL 为问答、日记复盘及期间回顾使用任务专用紧凑提示，并更新提示版本。问答 MUST 优先用answer回答问题并避免在其他数组重复分析；两类复盘 MUST 保留观察、推测、建议和局限。系统 MUST 不通过截断完整范围输入、伪造引用、丢弃失败字段或放宽校验来制造成功；字符指导 MUST 不被宣称为精确token保证。对新生成任务，系统 SHALL 从已授权的来源快照构造有界、带任务内标识的逐字引文目录；模型 SHALL 仅选择引文标识，服务端 SHALL 根据可信目录恢复原文、来源与类型。无合适证据时回答 MUST 缩小结论或说明资料不足，不能把没有证据的推断呈现为已验证事实。引文目录 MUST 受既有输入预算约束，且不能代替完整复盘来源。

#### Scenario: A question asks for a month summary and positive and negative experiences

- **WHEN** 固定虚构资料包含跨日记录，本人提出月度经历及满意/不满意的问题
- **THEN** 生成请求要求精简且直答、有真实短引文和资料局限的answer，限制重复字段；回答不宣称Top-K材料等于完整月报，不虚构逐日次数

#### Scenario: A complete diary or period review is requested

- **WHEN** 完整范围预览和预算检查通过
- **THEN** 模型仍获得该范围的完整合法来源，输出数量/篇幅受提示约束，保留原证据类型与完整来源有效性检查

#### Scenario: A quote identifier is selected

- **WHEN** 模型为问答或复盘结论选择当次目录内合法标识
- **THEN** 服务端从绑定来源恢复逐字原文并再次校验来源版本、类型及任务范围，不信任模型提供的引文正文

#### Scenario: Sources cannot support an answer

- **WHEN** 当前合法资料没有足以支撑某项结论的引文
- **THEN** 系统要求说明资料缺口或收窄结论，不由服务端替模型挑选证据，也不发布无证据的确定性结论

#### Scenario: Quote catalog would exceed the prompt budget

- **WHEN** 完整来源与有界引文目录合计超出既有输入预算
- **THEN** 请求在调用模型前受控失败或调整目录规模，完整复盘来源不被静默截断，不增加供应商上下文或输出预算

### Requirement: Explicit generation response failure categories

系统 MUST 在响应边界区别输出截断、空正文、非法响应结构、非正常停止、实际工具调用以及证据验证失败。`finish_reason=length` MUST 判为截断，不发布部分结果。无工具调用的null字段或空tool_calls数组 MUST 不被误判为实际调用；非空工具调用或畸形字段 MUST 仍被拒绝。对于新契约，未知、跨任务、错来源、错类型或未绑定的引文标识 MUST 拒绝发布，并提供固定的安全诊断原因；模型自行编写的引文正文 MUST 不作为可信证据，系统 MUST 不通过模糊匹配替换引用。

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

- **WHEN** 正常终止的结构化响应包含未知来源、非法引文标识或不匹配的逐字引文
- **THEN** 任务保留证据校验失败原因且没有可读成功报告，不通过模糊匹配替换引用

#### Scenario: A stale or foreign quote identifier is returned

- **WHEN** 模型返回其他任务、其他来源或已失效来源的引文标识
- **THEN** 服务端拒绝该结果并保留来源失效及任务生命周期保护，不发布回答或自动重调模型
