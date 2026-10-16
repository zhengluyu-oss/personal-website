# 私密日记生产配置记录

2026-10-15 已在现有博客服务器启用日记。这里只记录非秘密配置、验收证据和操作位置；数据库密码、登录令牌、备份私钥不进入仓库。

## 访问与数据范围

- 前台以站主账号登录后，从用户菜单进入“我的日记”，也可访问 `https://www.zhengluyu.com/diary`。后台登录与前台登录仍沿用各自入口。
- 站主通过现有 MySQL 中的账号记录核实，绑定用户 ID 为 1。仅本人可用，不依据 ADMIN 角色向其他账号放权。
- PostgreSQL 只保存日记、不可变历史和幂等结果；原有账号、文章、菜单及权限继续使用 MySQL。
- 日记按篇保存，可同日多篇；内容起止日期成对选填，一篇可以记录多天。没有开启模型调用、向量库或 RAG。

## 数据库和应用

- Ubuntu 软件源 PostgreSQL 16.15；独立实例服务 `postgresql@16-main`，持久数据目录 `/var/lib/postgresql/16/main`，数据库 `diary_db`。
- 仅监听 `127.0.0.1:5432`。运行连接使用 SCRAM-SHA-256；本地 postgres 管理使用系统账号 peer 验证，其他连接拒绝。
- `diary_migrator` 保持 NOLOGIN，迁移由本机 postgres 管理连接以该角色执行。`diary_runtime` 使用独立随机密码，没有超级用户、建库、建角色、角色成员或 BYPASSRLS 权限，也不拥有日记表。
- 运行账号不能创建 schema 对象，不能修改或删除历史、幂等结果；entry、entry_revision、mutation 全部启用且强制 RLS。
- 资源配置 `/etc/postgresql/16/main/conf.d/60-private-diary.conf`：shared_buffers 64 MB、work_mem 2 MB、max_connections 20；应用池最多 3 个连接。systemd MemoryHigh 384 MiB、MemoryMax 512 MiB。这是本次单人文字日记的起始配置，不是未来向量检索或多人并发容量承诺。
- fsync、synchronous_commit、full_page_writes 均开启；PG 不记录 SQL 语句及绑定参数。
- 应用外置配置 `/home/wwwroot/zhengluyu-website/config/application-prod.yml` 中新增 `diary` 节，文件权限为 root 0600。使用明确 YAML 键 `enabled`、`owner-user-id`、`url`、`username`、`password`。维护时只改日记节，不覆盖 `spring.datasource`。
- 运行连接秘密保存在 root 0700 目录 `/etc/luyu-diary/` 中，不能复制到公开目录、工单或 Git。

## 代理隐私保护

`/api/diary/` 使用独立代理块，禁止代理缓存、访问日志和请求/响应缓冲；请求转发到后端 `/diary/`。API 自身返回 private/no-store、noindex。前台 `/diary` HTML 同样返回 private/no-store 和 noindex/noarchive，且关闭访问日志。

已同步 `deploy/nginx/zhengluyu-blog.conf`，后续部署应保留这些规则。Nginx 不采集请求正文。应用和 PG 服务日志中，本次唯一的测试正文标记匹配为零；这不能替代未来新增 WAF、APM 或第三方代理时的日志审查。

## 自动备份、离机副本与恢复

- 备份脚本 `/usr/local/sbin/luyu-diary-backup`，仓库对应 `scripts/diary/backup-production.sh`。
- `luyu-diary-backup.timer` 每天北京时间 03:30 触发，随机延迟最多 10 分钟，错过运行时补执行。`luyu-diary-backup.service` 的成功/失败通过 systemd 状态和日志查看。
- pg_dump custom 格式通过管道直接交给 age 加密，不在磁盘落明文 dump。服务器仅保存 age 加密公钥，私钥不常驻服务器。
- 服务器目录 `/root/luyu-diary-backups/` 权限 0700，加密文件及 SHA256 受限存放，自动保留 14 天。仅删除该目录中本脚本命名规则的过期加密备份，不清理数据库或其他备份。
- 本次加密副本和验收报告另存站主 Windows 电脑的“文档/私密日记备份”。备份身份文件在站主本机 `.ssh/luyu-diary-backup-age.key`，NTFS 权限限当前用户及 SYSTEM。应单独妥善备份此密钥；丢失后不能解密已有备份。
- **每日自动任务目前只生成服务器上的加密备份；离机复制只完成了本次快照，尚未自动化后续离机同步。** 不应把同机多代备份视为持续离机备份。
- 恢复必须先创建隔离新库、重建必要角色，再用 age 解密管道输入 `pg_restore --exit-on-error --single-transaction`。恢复后执行原迁移的结构指纹检查，并验证 RLS、完整历史和幂等结果。不得覆盖在线 `diary_db`。

手动备份：`sudo systemctl start luyu-diary-backup.service`。查看结果：`sudo systemctl status luyu-diary-backup.service`；查看调度：`systemctl list-timers luyu-diary-backup.timer`。

## 本次真实验收

1. 初次迁移与重复执行结构指纹验证通过；运行角色无 owner 时不可见，owner 1 可见，切换 owner 2 后不可见，事务结束身份不残留。
2. 经正式 HTTPS API 验证本人资格、创建/修改、原请求重试、409 版本冲突、中文及 emoji、跨天记录范围、重叠日期筛选、历史、删除与恢复。
3. 重启 PostgreSQL、再重启实际后端服务，原文、日期、UUID、时间戳及版本均保持一致。
4. 两次真实 age 加密、解密、新库恢复及结构复核。第二次对比应用写入的 entry、4 个 revision 和 mutation 完整行内容摘要一致，恢复库隔离仍有效。
5. 本次测试 UUID 对应的临时数据已经按精确 ID 清理，未批量删除日记；复制的验收会话令牌已删除。只保留加密备份和不含秘密的验收结果。
6. 生产数据库不开放公网，站点原有 MySQL 配置未变，后端与前后台健康检查通过。Nginx 真实隔离容器回归通过，包含 `/diary` 隐私响应头检查。

## 关闭与故障恢复

仅将外置配置的 `diary.enabled` 设为 false，再重启 `zhengluyu-blog`，即可关闭入口。保留 PG 数据和备份，不 drop 库、不删除历史。重新启用前复核账号 ID、表结构、运行权限和备份。

本次原应用/代理配置与 PG 配置备份在服务器 root 专用验收目录中，权限 0700。恢复应用配置时保留 root 0600 权限；不要将包含其他生产秘密的原配置复制到仓库。
