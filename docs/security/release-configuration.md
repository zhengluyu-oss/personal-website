# 发布配置隔离

后端 JAR 默认使用 `prod`，不再包含 `application-dev.yml`、`application-prod.yml` 或其他私有 profile。资源和最终 JAR 均使用允许列表，避免旧 `target/classes` 残留配置重新进入发布包。测试依赖仅用于 test scope。

## 开发环境

保留原 gitignored 本地配置，不删除、不提交。开发者显式选择 `dev`，并通过 `--spring.config.additional-location=file:<本地配置目录>/` 加载包外 `application-dev.yml`。IDE 同样需要这两个参数。不要将生产配置复制到 `src/main/resources/application.yml`。

## 生产环境（需另行授权后操作）

1. 在服务器受限目录准备 `application-prod.yml`，只有服务账号/管理员可读。可从现有外置配置核对迁移，禁止从公开模板推测或填写真实秘密。
2. 启动命令明确包含 `--spring.profiles.active=prod` 与 `--spring.config.additional-location=file:/受限绝对路径/`。只允许 prod，不与 dev 同时激活。
3. 必需外置值由 `ProductionConfigurationGuard.REQUIRED` 定义：数据库、Redis、队列、邮件、OAuth、OSS 和 JWT。JWT 密钥至少 32 字符。开发密码没有内置回退；自定义 Quartz JDBC 数据源也需要独立密码。
4. 保留既有非秘密业务设置：队列/交换机/路由键、mapper 与 MyBatis、邮件开关、HTTP 客户端池、文件大小、OAuth 回调、网站地址。新管理员会话继续使用 `spring.security.jwt.admin-expire-minutes: 1440`。
5. 启动前检查最终 JAR 与两个前端 dist；缺失配置会在创建数据库等业务组件之前失败，错误只报告属性名，不报告属性值。

部署脚本只接受已经显式配置 prod 与外置目录的服务单元；不自动修改服务配置。首次安全发布前必须准备并验证外置配置。旧 JAR 可能携带秘密，备份要受限保存，不能公开或默认回退到有已知漏洞的旧包。

本变更不运行部署脚本，不重启服务器、不修改生产凭据。模板和扫描通过不等于实际生产凭据已经轮换。
