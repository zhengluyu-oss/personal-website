## ADDED Requirements

### Requirement: Default regression checks are executable without production secrets
默认回归检查 SHALL 在本地或隔离环境执行，不需要真实邮件、OSS、数据库或 Redis 秘密，不产生生产副作用。后端装配检查 SHALL 使用明确的隔离配置和受控外部边界，实际验证必要 Bean 或启动行为，不能用空测试替代。邮箱设置测试 SHALL 模拟组件真实使用的发送确认等依赖，并验证成功、失败和取消行为。真实基础设施集成检查 SHALL 显式 opt-in，未执行时记录条件与跳过原因。

#### Scenario: Backend default checks with no production credentials
- **WHEN** 开发者在没有生产秘密的环境运行默认后端检查
- **THEN** 隔离装配检查及行为回归可以实际执行
- **AND** 生产 profile 缺少必需配置时的失败关闭行为仍有独立断言

#### Scenario: Email settings dependency and delivery failure
- **WHEN** 邮箱设置组件通过模拟发送确认依赖测试成功、失败或用户取消
- **THEN** 测试正常解析组件依赖并验证对应界面行为
- **AND** 失败或取消不被断言为成功发送

#### Scenario: External integration environment is unavailable
- **WHEN** 未显式启用真实基础设施集成检查或所需隔离设施不可用
- **THEN** 输出记录未执行检查及其原因
- **AND** 不把跳过计为已完成的端到端验收

### Requirement: Audit regressions assert observable failure boundaries
回归证据 SHALL 对 F1—F11 记录触发条件、实际执行方式和可观察结果；涉及安全或数据损失的验证 MUST 断言无越权凭据、无非法读取/写入/删除，或失败后仍可恢复。部署验证 SHALL 先使用临时目录和服务替身覆盖备份、放置、超时及恢复失败，再用可用的隔离 Nginx 验证匹配。既有管理员二次验证、审核、Markdown 防护及正常上传检查 MUST 保持通过。

#### Scenario: Storage and comment rejection
- **WHEN** 回归模拟越权对象删除或不可见目标评论请求
- **THEN** 除失败响应外，还断言对象/数据库/通知边界没有执行禁止的副作用

#### Scenario: Recovery and resource fallback
- **WHEN** 隔离回归注入部署超时、服务重启失败或请求上一版后台资源
- **THEN** 断言具体备份恢复结果、非零失败状态或正确资源字节和 MIME
- **AND** 不仅检查脚本文本包含某个关键词

#### Scenario: Verification summary distinguishes evidence
- **WHEN** 实施结束生成验证记录
- **THEN** F1—F11 各项对应可执行回归及结果，明确区分通过、失败、跳过和待生产核验
- **AND** 未执行生产部署或破坏性清理不会被描述为已完成
