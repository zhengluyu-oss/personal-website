## ADDED Requirements

### Requirement: Comment access and replies respect target visibility
公开评论读取、计数和提交服务 MUST 根据解析后的目标类型与 ID 校验支持的类型、目标存在性、对应模块访问规则和目标公开状态。JSON 提交 MUST 使用解析后的 DTO 执行相同检查，不依赖 query 参数或在拦截器中再次消费请求体。回复 MUST 指向同一目标内已审核、未删除且关系有效的评论，收件人 MUST 从真实评论记录派生并核对调用者提供的关联字段。拒绝请求 MUST 在持久化、计数变更和通知调用之前终止，不泄露不可见目标的评论或真实计数；合法新评论仍 SHALL 等待审核。

#### Scenario: JSON submission cannot bypass a restricted module
- **WHEN** 无模块访问权限的用户通过 JSON 请求体提交该模块的评论，且 query 中没有 type
- **THEN** 服务根据 DTO 拒绝提交
- **AND** 不插入评论、不变更计数、不调用通知发送入口

#### Scenario: Approved comments on a private target remain private
- **WHEN** 已有审核评论的文章变为私人或请求者失去对应模块访问权限
- **THEN** 公开列表、计数和提交均遵循相同的不可见结果
- **AND** 不返回已审核评论、真实评论数量或不可见目标详情

#### Scenario: Unknown target or invalid type
- **WHEN** 请求使用未知类型、非法目标 ID 或不存在的文章目标
- **THEN** 服务返回现有错误契约下的明确失败，不执行写入或通知
- **AND** 留言板等无文章目标的合法类型仍按其真实模型检查，不强行要求文章记录

#### Scenario: Reply relationship is invalid
- **WHEN** 回复指向另一目标、未审核、已删除或不属于指定线程的评论，或者收件人与真实记录冲突
- **THEN** 系统拒绝该回复且不执行写入、计数更新或通知

#### Scenario: Authorized public-target submission
- **WHEN** 用户可访问目标模块且提交有效公开目标的评论或合法回复
- **THEN** 系统按现有契约保存未审核评论并提示等待审核
- **AND** 公共列表与计数仍不包含未审核评论
