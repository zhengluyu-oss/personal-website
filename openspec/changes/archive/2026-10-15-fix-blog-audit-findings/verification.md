# 修复验证记录

## 实施基线

- 开始日期：2026-10-15（亚洲/上海）；分支 `bugFix`，HEAD `3b065edc29c755701612dfac873df1b4f1925244`。
- 开始时工作区仅有本变更的未跟踪规划文档，没有业务代码修改。
- 依据：2026-10-14 博客代码排查报告。所有运行验证使用虚构 token、模拟 Mapper/OSS，或独立子进程，不修改生产内容、不发送邮件。
- 以下结果是修复前证据，不能作为修复通过记录；实施后需替换为仓库内可重复的行为回归。

## F1—F11 触发条件与修复前证据

| 编号 | 当前入口 | 最小触发条件 | 本次结果及证据类型 | 对应任务 |
| --- | --- | --- | --- | --- |
| F1 | 博客 `src/utils/http.ts`，第三方一言调用 | 已登录后用共享客户端请求外域 | 真实客户端 + 模拟 Axios adapter 收到虚构 Authorization；未发送网络请求 | 2.1、2.4 |
| F2 | `ArticleServiceImpl.deleteArticleCover`，`FileUploadUtils.toObjectKey` | 将站长头像完整 URL 交给取消封面函数 | 模拟存储收到非封面目录删除请求，返回 200 | 4.1—4.5 |
| F3 | `SiteModuleAccessInterceptor`，`CommentServiceImpl.userComment` | 评论 type 仅在 JSON 中，模块权限服务本应拒绝 | 真实拦截器放行，模拟模块服务未被调用；源码确认服务层无目标补检 | 3.1—3.3 |
| F4 | `CommentServiceImpl.getComment` | 请求私人文章的已审核历史评论 | 模拟 Mapper 返回评论，服务没有读取目标可见性；实际对象均虚构 | 3.1、3.3 |
| F5 | `WebsiteInfoServiceImpl.uploadImageInsertOrUpdate` | 更换头像上传空文件 | 模拟存储先收到旧图删除，新图上传未执行，随后返回失败 | 5.1—5.3 |
| F6 | `scripts/deploy-to-server.ps1` 远程放置/重启段 | 新 jar 覆盖后健康超时，或只发布后端失败 | 当前源码无本次 jar 备份/恢复；独立 Git Bash `trap ... ERR; exit 1` 退出 1 且没有执行恢复标记 | 7.1—7.3、7.5 |
| F7 | `deploy/nginx/zhengluyu-blog.conf` | 后台请求仅在上一版存在的 JS | 本次源码仍含遮蔽资源正则的 `^~ /admin/`；报告有线上只读 HTML 200 证据，本次未重复生产请求，需隔离 Nginx 验收 | 7.4、7.6 |
| F8 | `PhotoServiceImpl.deletePhotoOrAlbum` | 删除父/子嵌套相册里的照片 | 模拟服务检查缺少上层路径的键，存储清理未成功而数据库删除及业务码仍为 200 | 6.1—6.3 |
| F9 | 博客 `src/store/modules/user.ts` | 有效虚构 token 下 getInfo 发生 Network Error | 真实 Pinia store 清除 token；无真实用户请求 | 2.2—2.4 |
| F10 | `AccessLimitInterceptor.preHandle`，`RedisCache.setCacheObject` | 窗口首次请求后停止访问 | 当前源码给计数设置 TTL，却用无 TTL 重载写 `expire_time_`；本次未访问真实 Redis，后续需并发及时间推进测试 | 8.1、8.2 |
| F11 | `scripts/security/email-settings.test.mjs`，`BlogBackendApplicationTests` | 默认运行邮箱设置/后端装配检查 | 邮箱测试构建缺 `@/apis/email`；单项 Maven 装配检查因默认 prod 缺配置报错，保护器正常失败关闭 | 9.1、9.2 |

运行命令：临时目录中的 `probe-auth.mjs`、`probe-external-token.mjs` 与 `java @run.args`；仓库 `node --test scripts/security/email-settings.test.mjs`；后端 `mvn -q -Dtest=BlogBackendApplicationTests test`。临时 Java 验证使用当前基线编译类和模拟基础设施；这些外部验证脚本不作为最终交付测试。

## 影响分析准备

- 仓库绑定：`personal-website`，当前工作区路径与 GitNexus 注册路径相同。
- 原索引 commit `7fcedd159ea67419b6c295ce19cd98ba59804019`，落后 5 提交，带 `incremental-in-progress`，不能充当修改许可。
- 第一次 `node .gitnexus/run.cjs analyze --index-only` 尝试恢复全图，但在 Route 的 COPY 阶段失败：LadybugDB 256 MiB 缓冲池耗尽。
- 第二次仅在工具进程设置 `GITNEXUS_LBUG_BUFFER_POOL_SIZE=1073741824`，使用同一已安装 CLI 全量重试；90 秒后成功，15,846 节点、32,148 边。资源元数据确认当前 HEAD、schemaVersion 4 及 `incomplete_reasons: []`。未修改博客运行配置或安装依赖。
- MCP 查询仍返回缓存的旧提交提示，因此编辑许可使用绑定当前工作区的 CLI impact。HTTP 文件 HIGH（21 个直接引用、71 个累计影响），getInfo HIGH（6 个直接引用，涉及登录、导航、设置）；风险已在编辑前说明。Header/getSoup 为 LOW，Menu/logout/store 的 UNKNOWN 已通过模板和 import/调用搜索确认真实调用。各组后续仍需单独影响分析。

## 请求与登录恢复验证（F1、F9）

- `node --test scripts/security/session-boundary.test.mjs scripts/security/frontend-login.test.mjs`：30/30 通过。真实 Axios、Pinia 和 token 存储代码通过隔离适配器执行，涵盖外部 URL/请求头剔除、认证失效、网络/超时/5xx/403、存储方式、恢复及旧响应竞态。
- `SESSION_TEST_BASELINE=1` 的同一新测试读取 HEAD 的修复前 HTTP/store 源码：25 项中 22 失败、3 通过，确认能区分原缺陷（部分失败是新增状态接口在旧代码不存在，不能全部描述为原安全缺陷）。测试不替换工作区文件。
- 正常密码登录和管理员二次验证仍需用户信息成功后才显示登录完成；新组件测试确认密码已接受而资料网络失败时保留会话、不宣称登录完成、不提前导航。
- 本组源码完成，构建/整体回归及导航栏实际恢复入口验收仍在交付阶段检查。没有部署。

## 重叠规划边界

临时封面任务复用旧安全规划 6.1、6.2、6.3 的对应机制；评论目标任务复用 7.1。旧规划的媒体解码预算、通知消费重新检查、站内通知、友链审批、CSP、依赖审计仍独立，不能因本变更验收而自动勾选。合法历史存储源及全部文章引用写入口仍需在影响分析阶段逐项核实。

## 2026-10-15 实施与检查续记（归档选择前）

- 评论目标/模块/回复校验已完成；CommentAccessBoundaryTest、CreationCountTest、ModerationTest 共 15 项通过。
- 封面目的地/用途、归属与 Redis 原子认领、事务回调和 DELETE 契约已实现。StoragePurposeBoundaryTest、ArticleCoverCancellationContractTest、既有发布校验共 9 项通过；ArticleCoverLifecycleIntegrationTest 在隔离真实 Redis 下 10 项通过（包含 12 轮发布/取消竞争）。
- WebsiteImageReplacementTest 7 项及 PhotoDeletionBoundaryTest 8 项通过：实际对象键、并发条件切换、事务回调、精确清理、失败和重试均有断言。
- AccessWindowIntegrationTest 5 项及 AccessLimitFailureTest 1 项通过：48 并发共享窗口、自然过期、独立长封禁、旧持久键修复、Redis 失败与明确 HTTP 503。窗口用真实 Redis/Lua，独立封禁仍沿用原服务。
- Redis 集成仅在 BLOG_AUDIT_QA_REDIS=true 启用；使用自建容器 luyu-blog-audit-redis-20261015、127.0.0.1:16380、DB15、UUID 测试键；未触及现有 6379 或生产。Windows JDK 的 AF_UNIX 选择器初始化失败，测试 JVM 加 -Djdk.net.unixdomain.tmpdir=C:/__codex_unavailable_unix_socket_dir__ 后按 JDK 自有机制回退 TCP，未改应用/JDK 全局配置。隔离容器在测试后已停止并自动移除。
- 默认 mvn -q test 返回 0（集成 opt-in 未设置，相关 15 项按声明跳过）。默认装配测试导入真实 CreateBean、test profile、断言真实 BCrypt 正误密码；生产缺配置失败关闭测试保持原有行为。
- node --test scripts/security/*.test.mjs：39/39；其中邮箱设置 6 项执行真实组件，覆盖队列已接收但仍等待、确认后成功、失败重置、卸载取消，依赖模拟不再缺模块。
- 博客 pnpm test：47/47；pnpm build 成功，压缩与性能预算通过（初始 JS gzip 129.3KB、CSS 9.5KB）。后台 pnpm build 含 vue-tsc 门禁通过。
- python scripts/security/nginx-assets.test.py：真实 nginx:latest 隔离容器配置检查和请求通过，验证当前/上一版精确字节、缺失资源 404、JS/CSS MIME、哈希缓存、入口禁缓存与 SPA 深链接；容器已清理。HTTP 多个 Cache-Control 合并检查，避免把第一个 expires 自动生成头当作全部响应头。
- 新增统一 scripts/deploy-release.sh 并接入主 PowerShell 脚本。故障注入测试已有备份/放置/后端重启/超时/配置检查/恢复失败/首次安装场景，但尚未全部通过：Git Bash 会将传给 Python 的 / 和 /admin/ 参数转换为 Windows 路径，导致成功发布的入口资源提取断言失败。需完成跨平台测试边界修正及重跑，不能标记部署验收完成；未连接服务器部署。
- 用户在上述检查期间改为执行归档流程，实施暂停，等待选择变更。规划文档完整不代表所有实施任务完成。全局博客 vue-tsc 的历史错误差异核对、最终图谱 detect_changes、完整交付证据和生产发布交接尚未完成；当前未提交、推送、部署。
