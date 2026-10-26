## ADDED Requirements

### Requirement: Version-bound private diary indexing
系统 MUST 仅为本人已提交且未删除的当前日记建立索引，记录 entryId、owner、revision、profile、分块偏移及摘要。索引 MUST 不改变原文、历史或原文保存事务的成功条件。

#### Scenario: Index a multi-day entry
- **WHEN** 本人启动首次索引，某篇已保存日记覆盖多个日期
- **THEN** 系统按段落及 Unicode 码点分块，保留同一 entryId 和内容时间范围，不把它复制成每日多篇
- **AND** 未保存草稿和历史版本不发送到向量服务

#### Scenario: Indexing fails while saving
- **WHEN** 向量服务超时、缺少扩展或索引任务失败
- **THEN** 日记保存、历史与幂等确认仍独立完成，索引显示真实失败/未知状态

### Requirement: Profile and dimension isolation
系统 MUST 以供应商、模型、维度和预处理版本标识 embedding profile，校验响应维度、有限数值及非零范数。不同 profile 或维度的向量 MUST 不参与同一次相似度比较。

#### Scenario: Change the embedding model
- **WHEN** 外置配置变更 embedding 模型或维度
- **THEN** 旧 profile 不再参与当前检索，界面提示重建，而不是重命名旧向量或混用距离结果

#### Scenario: Reject an invalid embedding response
- **WHEN** 服务返回错误数量、错误维度、非有限数值或零向量
- **THEN** 本批结果不发布，保留可诊断的安全错误状态，不落半套可用索引

### Requirement: Current revision visibility and stale response rejection
检索 MUST 联合校验本人、当前 revision、未删除和已完整发布状态。编辑或删除后，旧片段 MUST 立即不可检索；网络调用后的短事务 MUST 重新核对源版本。失败版本 MUST 不被扫描循环无限重试。

#### Scenario: A late embedding arrives after an edit
- **WHEN** 版本 2 的向量请求返回时，日记已经保存为版本 3
- **THEN** 版本 2 结果被丢弃，不能标记版本 3 已索引或覆盖其索引状态

#### Scenario: Delete and restore an entry
- **WHEN** 日记被删除，随后按历史恢复为新版本
- **THEN** 删除时旧片段立即退出检索；恢复后按新 revision 建索引，不直接重新启用旧 profile 结果

#### Scenario: Pause during a batch
- **WHEN** 本人暂停增量索引，已经发出的一个批次随后返回
- **THEN** 系统不再发起后续批次，仅在源版本仍符合约束时处理当前返回，并显示剩余进度

### Requirement: Date-aware bounded retrieval with honest coverage
系统 MUST 支持与日记相同的 created/covered 时间轴，covered 按闭区间重叠、created 按上海日界筛选。首版 MUST 用有界精确向量检索和明确的关键词匹配融合结果，并显示当前索引覆盖状态；普通关键词结果 MUST 不标为完成 RAG。

#### Scenario: Overlapping content ranges
- **WHEN** 两篇跨天日记与所选 covered 范围重叠
- **THEN** 两篇均可贡献相关片段，每篇只计一次；未填 covered 的篇目不被默认为覆盖该日

#### Scenario: Incomplete index
- **WHEN** 所选范围还有当前篇目未索引或索引失败
- **THEN** 默认不生成回答，并显示覆盖情况
- **AND** 仅当本人明确选择基于已索引资料继续时才生成带不完整覆盖说明的回答

#### Scenario: No relevant evidence
- **WHEN** 候选未达到当前 profile 经校准的相关性要求或根本不存在证据
- **THEN** 系统说明记录不足，不补造经历，不把相似度当作正确概率

### Requirement: Verifiable source locations
每个可展示的引用 MUST 对应本人日记的精确 revision、码点范围和原文摘要；跳转 MUST 在本人权限下定位原文。引用 MUST 不通过 URL 携带问题、正文或引文。

#### Scenario: Open a valid citation
- **WHEN** 本人点击来源引用且该日记版本未变化
- **THEN** 系统打开对应日记并高亮一致的原文，展示内容日期与写作时间的区别

#### Scenario: Source changed before citation access
- **WHEN** 引用生成后日记被修改、删除或不再属于当前本人身份
- **THEN** 系统不把旧偏移应用到新正文，也不返回旧引文，提示来源已过期或拒绝访问
