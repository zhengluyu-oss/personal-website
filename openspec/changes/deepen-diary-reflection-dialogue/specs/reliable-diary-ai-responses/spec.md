## MODIFIED Requirements

### Requirement: Optional provider JSON output without budget expansion
系统 SHALL 由外置配置选择生成JSON模式，缺省保持供应商兼容，向量请求 MUST 不携带生成专属字段。生成输出默认最大100000 token，配置 MUST 在1至100000内；最大额度不是目标长度。生成支持有界长超时与匹配长答案的响应/字段限制，连接和向量调用保持短超时，每日及队列次数预算保持。

#### Scenario: DeepSeek JSON mode is enabled
- **WHEN** 配置启用JSON和100000最大输出
- **THEN** 请求携带JSON模式、JSON提示和100000额度，长正文可进入业务与证据校验

#### Scenario: Another provider keeps its existing configuration
- **WHEN** JSON模式配置缺省或关闭
- **THEN** 生成不新增response_format，向量不携带生成专属字段，不自动另发请求

### Requirement: Compact task-specific grounded output
系统 SHALL 使用任务专用提示和新版本，按问题相关性和复杂度组织自然完整回答。问答 SHALL 优先answer且可补充观察、假设和建议；两类复盘保留观察、推测、建议和局限。系统 MUST 不截断完整范围、不伪造引用、不丢弃失败字段来制造成功；提示篇幅不被宣称为精确token保证。

#### Scenario: A question asks for a month summary and positive and negative experiences
- **WHEN** 用户从检索问答请求月度总结
- **THEN** 回答可展开有依据部分及解释建议，但明确片段不代表完整月报，并引导完整范围复盘

#### Scenario: A complete diary or period review is requested
- **WHEN** 完整范围预览和预算检查通过
- **THEN** 模型读取完整合法来源，可按相关性充分分析，不再受500字或2000 token固定提示限制，引用类型和来源有效性仍校验

## ADDED Requirements

### Requirement: 长生成持续保持任务生命周期
长HTTP生成 SHALL 在数据库事务外等待，周期检查账号、任务、来源并续租；取消、来源变化或心跳失败 MUST 停止等待，不发布迟到结果，不自动重试。

#### Scenario: 生成超过原有租约窗口
- **WHEN** 合法生成持续超过90秒
- **THEN** 心跳维持租约且可正常完成，不被其他worker当作失效任务接管

#### Scenario: 长生成期间取消或来源变化
- **WHEN** 用户取消或修改来源
- **THEN** 周期检查和完成检查拒绝发布，保留可能计费状态且不重发
