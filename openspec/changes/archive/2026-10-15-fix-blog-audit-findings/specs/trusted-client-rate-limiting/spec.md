## ADDED Requirements

### Requirement: Rate-limit window state has bounded atomic lifetime
限流器 SHALL 原子初始化计数、窗口开始时间及其 TTL，辅助时间键 MUST 与对应窗口一起具有有界生命周期，不能永久留存。独立封禁状态 MUST 保持完整封禁期限，不受短计数窗口结束影响。合法访问旧的持久辅助键时 SHALL 安全补充窗口 TTL，不把未经授权的批量生产清理作为迁移步骤。存储异常 MUST 保持明确失败和保护语义，不返回空白成功。

#### Scenario: Window ends without another request
- **WHEN** 一个客户端触发限流窗口后不再请求
- **THEN** 计数和辅助开始时间键均在该窗口的有界期限内过期

#### Scenario: Concurrent first requests
- **WHEN** 同一限流身份的多个请求同时初始化窗口
- **THEN** 它们共享一个一致的开始时间和计数窗口
- **AND** 不产生缺少 TTL 的辅助键或重复重置窗口而放宽限制

#### Scenario: Cooldown outlives a counting window
- **WHEN** 封禁期限长于计数窗口且计数窗口结束
- **THEN** 客户端仍在完整封禁期限内被拒绝

#### Scenario: Legacy auxiliary key is encountered
- **WHEN** 新限流逻辑合法访问一个没有 TTL 的旧辅助键
- **THEN** 它按有效窗口状态补 TTL 或重新初始化一致窗口
- **AND** 不延长既有窗口来无限惩罚客户端，也不清除有效封禁

#### Scenario: Redis initialization fails
- **WHEN** 原子窗口操作遇到 Redis 故障
- **THEN** 请求得到明确错误并遵循既定保护策略
- **AND** 不伪造一次成功操作或无正文的成功响应
