# 实施与验证

- 2026-10-29，本地实现完整复盘可选问题、全范围追问与有界用户前文、相关引用目录、开放复盘提示词v5、100000默认/最大输出、长生成续租及完成后发布检查、跨日用量行补齐，以及Vue预览/展示。
- 无数据库迁移，无新增依赖；无提交、部署、生产配置更新、真实日记读取或付费模型调用。

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
- 发布配置及回退见 `docs/diary/deep-reflection-dialogue.md`。线上显式800仍需未来发布时改为100000；用户真实资料上的建议质量仍待本人验收。
