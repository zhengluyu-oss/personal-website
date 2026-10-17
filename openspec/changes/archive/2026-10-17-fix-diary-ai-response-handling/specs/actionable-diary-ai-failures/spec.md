## ADDED Requirements

### Requirement: Stage-specific safe and backward-compatible failure feedback
系统 SHALL 在现有任务error_code/message通路中区分向量请求、回答生成和证据校验阶段，并区分网络、超时、认证拒绝、限流及响应异常。前端 MUST 显示安全可操作提示，不把202受理当作生成成功；未知错误 MUST 保留通用后备说明。历史通用错误码 MUST 不被回填为未经证实的新原因。

#### Scenario: Query embedding fails before answer generation
- **WHEN** 问题向量调用异常且尚未进入生成阶段
- **THEN** 页面说明检索准备阶段失败及可手动重试的动作，不声称DeepSeek回答已经生成或日记索引损坏

#### Scenario: An authentication failure differs from truncated output
- **WHEN** 一次请求收到供应商认证拒绝，另一次生成响应因length结束
- **THEN** 前者提示检查服务配置，后者明确回答被截断及缩小问题/范围的动作；均不返回原始错误体或密钥

#### Scenario: A historical generic generation failure is read
- **WHEN** 读取旧GENERATION_FORMAT或NETWORK_UNKNOWN任务
- **THEN** 显示其已知阶段/通用异常和信息不足的说明，不伪称已确定截断、断连或超时

### Requirement: Private allowlisted diagnostics
系统 SHALL 只记录白名单诊断元数据：内部阶段、固定分类码、归一化停止状态、HTTP状态、合法用量数值、输出上限、响应字节数、耗时及内部任务标识。问题、原文、向量、模型正文、密钥、headers、URL和任意异常message/堆栈 MUST 不进入日志、API错误、URL、公开审计或浏览器持久存储。未知供应商状态字符串 MUST 归一为固定值而非直接记录。

#### Scenario: A response contains secret and private-content probes
- **WHEN** 受控供应商在错误体、停止原因、响应正文和嵌套异常中放入私密探针
- **THEN** 收集所有客户端错误与相关日志均找不到探针，只能看到允许的类别/数值，诊断仍可分辨截断与空响应

#### Scenario: A supplier omits usage metadata
- **WHEN** 响应或失败无法提供可信的token用量
- **THEN** 系统保持用量未知，不用字符、预留尝试或估算金额冒充实际token或账单

### Requirement: Conservative billing indications and exact-once usage recording
系统 MUST 区分本地调用尝试预留、供应商返回的实际usage及无法确定的账单。向量或生成请求已发出或不能排除已发出时 SHALL 保守提示可能计费，不能由生成阶段尚未开始推断整个请求免费。合法usage在成功或截断/校验失败路径均 MUST 至多记录一次，失败不自动退还预留。

#### Scenario: Embedding succeeds but generation is truncated
- **WHEN** 向量请求成功，生成返回合法usage但被截断拒绝
- **THEN** 任务失败且无成功报告，用量按实际可得值记录一次，费用提示不宣称失败免费或额度已退回

#### Scenario: A network outcome is unknown
- **WHEN** 请求发出后连接中断且无法确认供应商是否处理
- **THEN** 任务保留未知结果及可能计费说明，不自动重放；明确的发送前拒绝不被伪报为已生成回答

### Requirement: Explicit manual recovery preserves request identity rules
前端 SHALL 分开处理提交确认丢失与已确认任务终态失败。未收到202时 MUST 使用原请求体和requestId找回原任务；终态后再次提交 MUST 由本人手动触发，使用当前表单和新requestId，并明确可能形成新费用。轮询、刷新或切换分区 MUST 不触发新模型调用。

#### Scenario: The accepted response is lost
- **WHEN** 后台已受理，但浏览器未收到202并点击重试原请求
- **THEN** 原requestId和参数不变，仅返回原任务，不创建第二次生成

#### Scenario: A user resubmits after a terminal failure
- **WHEN** 已知失败原因展示后，本人修改问题并再次明确提交
- **THEN** 创建反映当前输入的新任务，提示可能再次计费，保留上次失败记录，不把旧答案标成新问题的答案

#### Scenario: Other users or a rejected session request diagnostics
- **WHEN** 匿名、其他管理员/账号读取任务，或本人会话失效
- **THEN** 保持401/403与私密no-store/noindex边界，失效会话卸载隐藏分区内的私密状态，不泄露任务、原因或正文
