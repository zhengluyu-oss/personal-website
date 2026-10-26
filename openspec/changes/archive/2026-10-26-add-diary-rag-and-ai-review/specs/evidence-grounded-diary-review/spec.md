## ADDED Requirements

### Requirement: Owner-triggered grounded questions
系统 SHALL 提供由本人手动发起的单次日记问答。答案 MUST 限定于实际检索材料，对个体经历的陈述附合法证据，说明资料缺失及不确定性；系统 MUST 不因保存、打开页面或定时任务自动生成回答。

#### Scenario: Ask about a recurring situation
- **WHEN** 本人输入问题并选择可选时间范围
- **THEN** 系统按本人有效当前资料检索、生成有引用的回答，并显示实际使用资料的范围

#### Scenario: Ask about an undocumented event
- **WHEN** 虚构评测中的问题在资料中没有依据
- **THEN** 系统返回记录不足，不编造人物、事件、行为次数或因果关系

### Requirement: Complete and explicit review scope
复盘 MUST 支持明确篇目或日期范围两种互斥选择，生成前预览篇目、时间轴、字符数和 revision 快照。范围复盘 MUST 使用选定范围的完整正文，超限或来源快照变化时不得静默截断后生成。

#### Scenario: Preview a covered date range
- **WHEN** 本人选择内容日期范围预览
- **THEN** 系统按重叠规则列出全部符合范围的当前篇目，给出 snapshotDigest，并说明无覆盖日期记录的排除规则

#### Scenario: Reject a changed or oversized scope
- **WHEN** 预览后范围中新增、编辑或删除了篇目，或范围超过 40 篇/24,000 码点及实际模型预算
- **THEN** 来源变化返回需刷新范围的冲突，超限要求缩小范围；均不发起本次模型生成

#### Scenario: Review without a ready vector index
- **WHEN** 本人选择的完整当前篇目满足预算，但向量索引尚未建好
- **THEN** 手动复盘仍可使用这些原文执行，不能拿不完整向量检索集合替代选择范围

### Requirement: Separate observations hypotheses and suggestions
复盘 MUST 区分有引用的原文事实、带不确定性的推测、可尝试的建议和资料限制。建议 SHALL 说明依据、可执行动作及复查方式；结果 MUST 不宣称绝对客观、诊断疾病或以分数评价人格。

#### Scenario: A diary covers multiple days
- **WHEN** 输入包含一篇跨十几天的回顾，未逐日记录具体行为
- **THEN** 系统不据此推算每天都有记录、行为发生频率、坚持天数或未记载日的状态

#### Scenario: Return a practical review
- **WHEN** 模型返回复盘且通过结果校验
- **THEN** 界面分别展示观察、推测、建议和限制，个体化判断可回指证据，普遍性建议有明确标注

### Requirement: Validate and safely render model output
模型输出 MUST 经过字段/长度、引用白名单和精确引文校验；未知引用、错配引文或非法结构不得显示为成功结果。系统 MUST 将问题及日记视为资料而非可执行指令，并使用现有受限 Markdown 渲染。

#### Scenario: Reject fabricated sources
- **WHEN** 模型返回不存在的来源 ID 或声称引用的文字不在对应片段
- **THEN** 系统拒绝发布成功报告，返回安全的输出校验失败状态，不用相似日记替补引用

#### Scenario: Handle injected instructions and active content
- **WHEN** 虚构日记包含“忽略规则”指令，模型输出脚本、外部图片或工具调用内容
- **THEN** 日记不能取得系统权限，输出不能执行脚本、加载外部媒体、改写日记或发送外部消息

### Requirement: Private result history with source validity
系统 SHALL 为本人提供分页回看、取消任务和删除结果。每份结果 MUST 记录生成时间、模型/提示版本、来源快照及覆盖信息；读取、发布或跳转时来源失效 MUST 阻止旧正文和引文继续展示。

#### Scenario: Read a previously generated review
- **WHEN** 本人打开一份结果且其来源仍有效
- **THEN** 系统显示该次生成时的资料范围和模型说明，不把它描述为包含随后新增日记的最新全面结论

#### Scenario: Source changes while generation runs
- **WHEN** 模型请求运行期间原文修改、删除，或完整范围新增篇目导致快照变化
- **THEN** 迟到结果被标记为 STALE，不发布旧分析正文，并允许本人刷新后重新生成

#### Scenario: Delete a private report
- **WHEN** 本人明确删除某份分析结果
- **THEN** 系统清理对应本地派生结果，不删除日记原文或历史，也不承诺撤回云端已经接收的数据
