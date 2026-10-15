## ADDED Requirements

### Requirement: Each release records only its affected components
主部署流程 SHALL 在任何文件替换前记录本次发布选择、实际目标路径、备份和校验值，包括所选后端 jar、前端以及实际修改的 Nginx 配置。恢复 MUST 只使用本次清单中已经修改的组件，不消费以前发布的备份来恢复未选择的组件；自定义目标路径 MUST 被放置和恢复共同遵守。

#### Scenario: Backend-only release
- **WHEN** 本次仅选择后端发布而系统存在以前的前端备份
- **THEN** 流程在覆盖前备份当前 jar，失败时恢复本次 jar
- **AND** 不修改前端当前版、上一版或无关 Nginx 配置

#### Scenario: Frontend-only release with custom paths
- **WHEN** 本次仅选择前端并指定自定义部署目录
- **THEN** 备份、放置与恢复使用该实际目录
- **AND** 后端 jar 和服务保持不变

#### Scenario: Backup cannot be created
- **WHEN** 任一待修改组件无法完成所需备份或校验
- **THEN** 发布在覆盖该组件前停止，已修改组件按本次清单恢复
- **AND** 失败结果及备份状态明确可见

### Requirement: Deployment failures perform verified component recovery
文件放置、服务重启、配置检查和发布健康验证失败，包括显式超时，MUST 进入统一的有界恢复流程。后端失败 SHALL 恢复本次旧 jar、重启旧服务并核验健康；前端和配置 SHALL 恢复各自本次状态。首次安装无旧版本时 SHALL 恢复发布前的不存在状态并明确报告没有可启动旧版本。失败发布 MUST 以非零状态结束，区分恢复成功与恢复失败，并保留备份路径和不含秘密的恢复说明。

#### Scenario: Readiness timeout after replacement
- **WHEN** 新 jar 已放置但健康检查超时
- **THEN** 即使流程使用显式失败退出也执行恢复，不仅依赖 ERR trap
- **AND** 恢复旧 jar 后重新启动并验证旧服务

#### Scenario: Frontend placement or Nginx validation fails
- **WHEN** 所选前端放置或 Nginx 配置检查失败
- **THEN** 所有本次已经修改的组件恢复到发布前状态
- **AND** 失败发布不显示成功提示

#### Scenario: Rollback itself fails
- **WHEN** 恢复文件、重启旧服务或恢复后的探针也失败
- **THEN** 流程返回非零并明确区分原始发布失败和恢复失败
- **AND** 保留本次备份及人工恢复所需路径，不无限等待

### Requirement: Readiness verifies business output without private content assumptions
后端探针 SHALL 使用已有匿名 `/site-modules/access`，同时检查 HTTP 成功、业务 code=200 和完整六模块访问结果。前端探针 SHALL 核验入口以及实际资源的响应类型，不能把 HTML 200 当作 JS/CSS 成功。探针 MUST 有时间上限且不得把 token 或秘密写入日志。

#### Scenario: Blog content module is private
- **WHEN** 后端可用但博客内容模块被配置为私人
- **THEN** 匿名模块访问探针按其合法业务响应确认健康
- **AND** 不因私人文章列表被拒绝而误触发回滚

#### Scenario: Superficial success response
- **WHEN** 探针取得 HTTP 200 但业务码失败、模块结果不完整或 JS 请求返回 HTML
- **THEN** 对应发布验证失败并触发本次组件恢复
