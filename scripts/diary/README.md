# 私密日记：配置、验收、备份与回退

本阶段仅实现文字日记。现有账号、文章和后台继续用 MySQL；日记的当前记录、不可变历史和幂等结果一起存放在独立 PostgreSQL 16 数据库。不安装向量扩展、不调用模型。默认 `diary.enabled=false`，不会自动创建表、迁移生产或开放入口。

## 生产前的边界

先盘点服务器实际剩余内存、磁盘、已有 MySQL/Redis/Java 的峰值和备份空间，再决定 PG 是否同机运行；本地验收的384MiB数据库容器上限不是生产容量承诺。单应用PG连接池上限3、最小空闲0，获取连接1.5秒、语句5秒、事务8秒，需结合并发与磁盘实测。端口仅绑定回环或专用网络，禁止公网开放5432。容器必须使用持久卷；备份不能只放在该卷或同一块磁盘。启用前安排真实恢复演练和磁盘/连接/备份告警。

浏览器只使用现有前台身份。先从真实MySQL账号数据确认站主的用户数字ID，不能凭用户名猜ID，更不能拿管理员角色代替本人资格。其他管理员也不能访问日记。数据库RLS是第二道隔离，不替代认证；同一宿主机的系统管理员、数据库超级用户、被攻陷的后端仍属于高信任边界。HTTPS传输及磁盘/备份加密需要运维单独落实，本实现不宣称应用层端到端加密。

## 初始化数据库（手动）

1. 选择并固定经过审核的PostgreSQL 16镜像摘要/软件版本，创建持久目录并限制文件权限。验收镜像摘要记录在 `verify-postgres.py`，使用的是已有镜像，未安装vector扩展；生产可选标准PG16镜像，但需重新执行兼容验收。
2. 在专用PG实例用管理连接执行 `000-roles.sql`。角色默认NOLOGIN；迁移角色与运行角色分别设置LOGIN和独立随机密码，通过交互式 `psql` 的 `\password diary_migrator` / `\password diary_runtime` 设置，禁止把真实密码写进脚本、命令历史或Git。运行角色不得有角色成员身份、超级用户或BYPASSRLS权限。
3. 用管理连接创建数据库：`CREATE DATABASE diary_db OWNER diary_migrator;`，并撤销PUBLIC默认连接：`REVOKE ALL ON DATABASE diary_db FROM PUBLIC; GRANT CONNECT ON DATABASE diary_db TO diary_migrator, diary_runtime;`。使用专用数据库，不在MySQL或原有共享PG库中执行。
4. 使用迁移连接执行 `psql -X -v ON_ERROR_STOP=1 -f 001-private-diary.sql`。脚本单事务建表并强制RLS；重复执行检查版本和结构指纹，异常结构直接拒绝。保留首次迁移输出，出错先查原因，不手工删除版本表绕过检查。
5. 在实际运行角色连接中，不设置事务owner时三表不可读写。三表必须开启并强制RLS，且不由运行角色拥有；运行角色没有DDL、历史更新/删除、mutation更新/删除权限。应用每个事务通过绑定参数设置事务局部owner，提交或回滚后不能沿用上个请求的身份。

## 应用配置（外置）

通过受限环境文件或现有秘密管理设置：

```text
DIARY_ENABLED=true
DIARY_OWNER_USER_ID=<已核实的MySQL站主账号ID>
DIARY_URL=jdbc:postgresql://127.0.0.1:5432/diary_db
DIARY_USERNAME=diary_runtime
DIARY_PASSWORD=<独立随机密码>
```

外置文件只允许运行账号读取，不进入仓库或公开站点目录。跨机器连接应使用PG TLS校验（例如 `sslmode=verify-full`，正确配置CA与主机名）。不得使用超级用户或迁移账号启动日记。应用启动不必立即连接PG；本人资格、存储检查失败时入口不可用，不会返回“已保存”。新增具名DataSource/JDBC/事务Bean使用 `defaultCandidate=false`，保留Boot4现有MySQL自动配置和MyBatis MYSQL分页，不能改成覆盖默认数据源。

启用后只在本人用户菜单显示“我的日记”。`/diary`不出现在公开导航、文章搜索、推荐和站点地图。反向代理/API网关/WAF/APM须关闭日记的请求/响应正文采集，禁止日志格式包含 `$request_body`、Authorization、正文、搜索关键字或导出数据；日记路径使用 `proxy_no_cache 1; proxy_cache_bypass 1;`（按现有代理配置合并），不得被CDN或Service Worker缓存。搜索走POST，URL只含条目UUID等选择信息。应用响应设置private/no-store和noindex；这些头不能替代访问控制。

不要给私密包开启DEBUG/TRACE或覆盖应用提供的 `xyz.kuailemao.diary`、JDBC参数记录、PostgreSQL日志禁用设置。PG服务端避免 `log_statement=all` 和绑定参数采集。日志中仅记录必要的状态/耗时等元数据，不转发日记对象到RabbitMQ审计。

## 保存与使用

每篇是独立UUID；不以日期作唯一键。创建时间/修改时间与可选的成对内容起止日期分开保存，一篇可补写十几天，也可同日多篇。停顿约2秒自动保存，持续输入最多约30秒触发；只有服务端提交确认后显示“已保存”。网络未知结果使用原UUID、mutationId与原请求重试，不能重新创建。409代表其他标签页已修改：先比较，下载本地草稿或明确选择服务器/本地版本，不循环覆盖。

正文只在页面内存和数据库中，不保证断电或强制关浏览器后的未保存内容恢复。卸载提醒是尽力提醒；离线期间请下载本地草稿。会话失效锁定编辑，同账号重新确认可继续；切换账号会清空内存。删除为可恢复墓碑，历史与幂等结果仍保留，不是永久销毁。

JSON/Markdown ZIP导出只包含已提交、未删除的当前记录；需选起止日期，最多366天/1000篇/20MiB，超限明确拒绝，不静默截断。文件包含私密原文，请自行保护。Markdown文件名含UUID，不会因同一天多篇而覆盖。导出不是数据库备份，不包含完整历史、墓碑和幂等凭据。

## 备份与恢复（人工执行，不安装定时任务）

1. 用迁移/专用备份连接，在可信主机通过受限 `.pgpass` 或秘密注入运行 `pg_dump --format=custom --file=<受限临时路径>/diary.dump diary_db`。必须备份整个diary schema（包含entry、entry_revision、mutation、schema_version、约束及策略），不可只备份当前正文。备份账号按必要范围授权，不给应用运行角色提权。
2. 将dump立即交给组织批准的带完整性认证的加密工具，例如age公钥加密。加密公钥可随备份任务提供，解密私钥应在另一受控位置，不能与备份同盘。保护/及时清理明文临时文件；禁止把密钥放进备份文件名、日志或仓库。本地QA使用AES-256-GCM并验证篡改拒绝，仅用于验证加密往返，不是生产密钥管理方案。
3. 新隔离PG实例先用角色脚本重建角色与权限；`pg_dump`不包含集群角色，不能直接假定已有运行角色。创建全新恢复库由diary_migrator拥有，使用迁移权限执行 `pg_restore --exit-on-error --single-transaction --dbname=<新库> <解密后的dump>`。禁止覆盖正在提供服务的数据库。PG主版本须兼容。
4. 再执行版本化迁移的结构核对；使用运行角色检查无owner拒绝、正确owner可见和跨owner拒绝。逐项比较UUID、原文、起止日期、created/updated/deleted、revision、历史与mutation；重新提交已成功的原请求，必须返回原确认且不覆盖较新内容。真实应用重启后再读一次，不以dump文件生成成功代替恢复成功。
5. 记录备份时间、完整性校验、恢复耗时及验证结果。生产切换恢复库前另行安排维护窗口与授权。保存足够多代备份；需要加密离机副本并定期演练。

## 回退

将 `DIARY_ENABLED=false` 并按现有方式重启应用；入口关闭，MySQL博客继续工作。保留PG数据/持久卷、加密备份及外置配置；不要drop数据库、删除历史或回退已写入的数据结构。后续重新启用前先确认结构与权限。发布前仍需核实真实资源、站主ID、HTTPS/代理不缓存不记正文、备份私钥保管和生产恢复演练；本次代码交付没有代替这些上线步骤。

## 本地验收

默认 `mvn test` 不会自动连接测试设施。显式测试用虚构账号和独立库：

- `python scripts/diary/verify-postgres.py`：自行创建带任务标签的隔离容器，真实RLS、迁移重复/失败/漂移、重启和恢复检查；只清理其创建且ID/标签核对一致的容器。
- `BLOG_DIARY_QA=true` 后在backend运行 `mvn -B -q -Dtest="Diary*Test" test`：须先手动准备专属loopback PG15432和MySQL15433及脚本中的虚构凭据，**禁止将端口转发到生产**。真实库测试名Integration/DualDatabase/ExportLimits/Fault，其他是无设施装配/安全边界测试。
- 前台 `npm test`、`npm run test:diary`、`npm run build`。`scripts/diary/browser-qa.mjs` 用本机test-classes入口DiaryBrowserFixture与Vite验证真实页面，执行前校验fixture站点标记，拒绝向非fixture服务写入。配置 `PLAYWRIGHT_MODULE`、必要的 `PLAYWRIGHT_CHROMIUM_EXECUTABLE` 和独立 `DIARY_QA_OUTPUT`；所有截图只有虚构内容。
- `scripts/rebuild-code-index.ps1` 恢复索引缓冲池，不修改机器全局内存配置。完成后完整检查图谱变更，不能把partial/truncated输出视为通过。

源码装配依据：[Spring Boot 4 数据源配置](https://docs.spring.io/spring-boot/4.0/how-to/data-access.html)。测试结果与剩余限制见本变更的implementation-evidence.md，不能以构建成功代替交互/恢复/生产验收。
