## Why

生产日记问答先发生一次向量请求 `NETWORK_UNKNOWN`，随后连续三次生成任务以 `GENERATION_FORMAT` 失败；提交接口均可正常接收请求，16 篇日记索引正常。当前客户端把截断、空回答和其他响应异常合并处理，界面只提示检查配置或限额，无法指导排查；2000 token 输出预算与重复输出多组分析字段也存在不匹配风险，但尚无证据证明这三次一定是截断。

## What Changes

- 按向量检索请求、回答生成、结果校验三个阶段区分安全错误码和用户提示；细分输出截断、空响应、非法结构、拒绝/限流及网络异常。
- 对明确支持的供应商显式启用可配置 JSON 输出模式；保留现有非思考设置、模型和 2000 token 上限，通过按任务类型精简输出减少截断，不自动扩容或重试。
- 增加仅含白名单状态和数值的诊断信息，确保失败时仍可核对停止原因、实际可得用量和耗时，不记录私密输入或模型正文。
- 日记问答、日记复盘及共享客户端的周/月回顾统一采用分类规则；前端清楚说明失败阶段、能否手动重试及计费不确定性。
- 用受控 HTTP、隔离 PostgreSQL、浏览器和真实模型虚构语料验证，保留来源校验、幂等、取消和隐私保护。

## Capabilities

### New Capabilities

- `reliable-diary-ai-responses`: 在既有资源预算内生成紧凑结构化回答，明确处理截断、空响应和协议异常，并保留严格引用校验。
- `actionable-diary-ai-failures`: 可定位阶段且不泄露日记内容的任务诊断、费用提示及手动恢复体验。

### Modified Capabilities

无。本变更为现有日记 AI 增加补充契约；相关 AI 主规格尚位于 `add-diary-rag-and-ai-review` 和 `add-diary-goal-tracking-and-periodic-reviews` 的活动变更中，不改写它们的范围、任务进度或已有主规格。

## Impact

- 后端：`DiaryAiCloud`、`DiaryAiProperties`、`DiaryAiEvidence`、`DiaryAiWorker`、`DiaryAiStore`，以及共享生成适配器的 `CoachingPeriodAi`/期间提示和测试。
- 前端：日记 AI 与期间回顾的失败反馈、重试提示和相关回归脚本；沿用既有 Vue / Element Plus 页面。
- 保持 API 路径、任务状态、报告结构及认证边界兼容；优先复用现有 `error_code`、`message` 和私密诊断日志，无数据库结构迁移、新依赖或索引重建。
- 生产实施需后端和博客前端配套发布，必要时外置配置显式开启 JSON 模式；费用尝试限额、连接/总时限、响应大小、模型和 PostgreSQL 资源配置不增加。旧任务不重放、不伪造细分原因；提案阶段不修改代码或生产配置。
