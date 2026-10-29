# 实施与验证

- 2026-10-29，本地实现完整复盘可选问题、全范围追问与有界用户前文、相关引用目录、开放复盘提示词v5、100000默认/最大输出、长生成续租及完成后发布检查、跨日用量行补齐，以及Vue预览/展示。
- 本地实施阶段无数据库迁移、无新增依赖、未提交或部署，未更新生产配置、读取真实日记或调用付费模型；随后获授权提交与发布的记录见下文。

## 后端

使用新建、固定镜像的隔离pgvector容器（127.0.0.1:15432），仅虚构凭据与合成日记。迁移001、000-vector、002、003、004、005；无生产连接。20个测试类共95项通过，失败0、错误0、跳过0。

```powershell
$env:BLOG_DIARY_AI_QA='true'
mvn -q "-Dtest=DiaryAi*Test,DiaryConversationTextTest,DiaryDeepReflectionPostgresTest,DiaryLongGenerationPostgresTest,DiaryQuoteCatalogTest,DiaryReflection*Test,DiaryValidationTest,DiaryDiagnostics*Test,CoachingPeriodAiTest" "-DargLine=-Djdk.net.unixdomain.tmpdir=D:/nonexistent-luyu-qa-socket-dir" test
```

argLine使用仓库既有Windows JDK loopback测试兼容办法，未更改全局环境。新增场景包括大于旧32000字符的合法结果、100000配置边界与绑定、20建议可采纳兼容/21建议明确拒绝、保持逐字引用拒绝、11轮完整多篇复盘无检索替换、首轮目标与近期纠正、两种worker回调实际续写租约、HTTP等待片段与取消、完成响应用量、无当天行仍累计token。租约测试将真实租约缩至20秒再执行捕获心跳，验证延至70秒以上，无需等待90秒。

## 前端

`node --test scripts/diary/reflection-dialogue.test.mjs scripts/diary/review-preview.test.mjs scripts/diary/validation-diagnostic.test.mjs`：12项通过。验证自定义问题/准确范围提交和恢复、日期改选使预览失效、明确七天范围、完整篇目日期显示、新视角展开、一般建议标识、模型HTML惰性文本显示及既有失败/隐私交互。

`pnpm build`通过：Vite构建、224个gzip校验、资源体积检查通过。既有Sass弃用和其他组件CSS嵌套警告仍出现。本次没有运行真人浏览器或真实模型语义验收，JSDOM交互和构建不等同于这些验收。

## 变更与边界

- `openspec validate deepen-diary-reflection-dialogue --strict`通过；9项实施任务完成。
- `git diff --check`通过。
- GitNexus detect_changes(all)：80个已索引符号、24条受影响流程；partial=false、truncated=false。risk_level=critical，主要为共享configured入口及问答/复盘调用链；预编辑已报告CRITICAL/HIGH，验证包含配置上下界及两类真实数据库worker。图结果不被当作低风险或语义正确证明；新增文件另由源码与测试核对。
- 生成等待期检查/续租，响应到达后先记可得usage，再由worker与最终事务fence检查发布资格；不会自动重发未知/取消任务。
- 发布配置及回退见 `docs/diary/deep-reflection-dialogue.md`。发布前完整YAML层级核对确认800属于知识库助手，日记原使用2000默认值；已更正最初的跨模块配置误判。用户真实资料上的建议质量仍待本人验收。

## 2026-10-29 授权发布记录

- 本地代码提交：`68a3e74`，中文说明“增强日记深入复盘与连续对话并放宽输出额度”。无远程Git推送，既有output/未提交。
- `mvn -q -Pprod -DskipTests package`成功，后端JAR与前端dist均通过发布资源/秘密门禁；沿用前述95后端测试、12界面测试及前端构建结果。
- 仅发布后端和博客前端。发布前日记AI及期间任务RUNNING/QUEUED均为0；旧JAR、前端及受限配置先全部备份。原发布脚本的单次副本加入配置恢复和备份保留；回滚在旧JAR重启前恢复旧配置。新后端健康后才切换前端。
- 仅在diary.ai添加/更新max-output-tokens=100000、generation-timeout-seconds=1800、max-response-bytes=16777216；完整YAML语义比较确认其他设置未变，知识库助手仍800、日记思考开关仍false。没有数据库迁移或历史任务重放。
- 新JAR SHA256：`523f79a624af5b9fd8a83558158be6ee39945dcdcee735d65187cf60b4dda625`；线上文件与本地产物一致。
- 前端index.html SHA256：`e8632cd31bc6a2894b3d593ef540c4a65d8944f38212ebf5c3f111d1cb8f23a2`；线上文件与本地产物一致。
- 使用新JAR内真实DiaryAiProperties及Spring Binder在服务器绑定受限YAML，configured=true，三个值均符合要求；探针不启动应用上下文、不调用数据库/模型，只输出非秘密验证结果。另核对systemd启动配置及进程环境无这三项覆盖。
- 服务active，主健康接口及前端静态资源检查通过；公开首页与/diary返回200，匿名/diary/auth/ai/capabilities（含公网/api前缀）返回401。配置与配置备份仍0600 root，未向聊天或仓库导出配置。
- 公网日记懒加载资源`/js/index-a13685cb.js`返回200，SHA256与本地一致（`58275ec83cd4f040aa9772de5073a6763749d9b12d3bb0bce3256c266b2b2ba6`），包含新的完整复盘预览界面；运行进程命令确认使用预期后端JAR。
- 持久回滚材料：`/root/luyu-releases/diary-reflection-68a3e74-20261029/luyu-release-backup-oHlgmf/`，包含manifest、旧JAR、前端及diary.config。恢复时需三者一致，并在旧JAR重启前恢复配置。
- 本次未调用付费生成、未读取本人日记正文；实际建议质量仍需本人在新界面选择资料验收。
