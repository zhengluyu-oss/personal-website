# 实施证据

## 基线

- 分支 `zhengluyu_feat_diary`；HEAD `1c78242231b4b67dfbcbc73c704eed070fdf6d86`。
- 第一阶段32/32已同步19条主规格并归档到 `2026-10-16-add-private-diary-postgres`。
- 第二阶段任务仍未全部完成，未选择真实模型供应商；不将受控HTTP或本地实现当作真实供应商验收。
- 保留原AI包、前台AI面板、代理、安全测试、生产备份文档和OpenWiki等既有未提交修改。
- 本次只使用虚构内容、受控HTTP和标记为 `add-diary-rag-and-ai-review` 的隔离QA容器。不读取真实日记、不访问生产或秘密。
- 基线：DiaryAiTextTest、DiaryAiEvidenceTest、DiaryAiCloudTest、DiaryAiSecurityTest、DiaryWiringTest、DiarySecurityTest执行成功；真实PG测试另行运行，不把默认跳过视为通过。
- 新增CoachingModels/Store/Configuration/Service/Controller及003迁移已先执行impact；UNKNOWN后核实这些路径均不存在，属于新增符号。

## 验证记录

### 存储与保存

- `coaching-verify-postgres.py`实际通过：001+003独立新库 `coaching_core_7556f4def12a`，以及含002的专属QA库；003首次／重复、失败整体回滚、漂移拒绝、旧迁移复核、12张FORCE RLS、内部owner复合FK、事务局部owner和不可变历史／mutation权限均已核对。
- 新的当前行、历史与mutation同事务写入；owner_control串行限额、expectedRevision CAS与完整请求摘要防覆盖／重放。补测提交失败时明确回滚，避免连接恢复autocommit时意外提交；真实已提交但ACK丢失仍由原mutation返回确认。
- 原日记AI报告删除后的来源查询曾触发嵌套事务rollback-only，已通过在本人链接读取事务结束后再解析来源修正。新增原报告CURRENT→删除→STALE回归，人工行动仍存在。

### 后端与模型链路

- 最终全量后端：274项，234通过、40按原有条件跳过、0失败／错误。日记、Coaching与审计相关的真实PG／MySQL测试明确启用了本地QA条件，不把默认跳过计为通过；40项其他设施／条件跳过单列保留。
- 新增CoachingContractTest、PostgresTest、FaultTest、CoreOnlyTest、SecurityTest、PerformanceTest，以及8项CoachingPeriodAiTest。覆盖无AI表／供应商的真实核心配置、三连接耗尽且MySQL可用、历史写失败、提交失败、无变化、旧版本、跨owner、重复ACK、反馈修正／删除和联动CAS。
- 期间任务与原问答共用owner_settings锁、排队与报告预留、每日预算及单worker租约；两种年龄顺序、抢最后名额、HTTP不占PG连接、取消、账号撤销和晚到结果均已验证。
- 在专属QA集群临时禁止运行角色新连接并终止其QA连接，模拟HTTP后的真实PG短断连，随后恢复LOGIN：任务恢复为UNCERTAIN，没有重复HTTP和报告。租约失效后的未知任务不自动重放。
- 有类型引用拒绝A计划作为EXECUTION／EFFECT的唯一依据；原日记AI报告和期间报告都必须由服务端解析建议、owner和来源。有效引文后的自然语言解释是否正确仍依赖真实模型人工验收，未宣称结构校验可解决语义真实性。

### 浏览器、构建与日志

- 最终本地真实浏览器再次通过8组流程：丢ACK去重、目标→行动→反馈→明确完成、409保留本地文字、期间预览／共享worker／安全文本引用、取消采纳不写入、修改后明确采纳、360／390／430px不横向溢出、其他ADMIN会话撤销清除面板与草稿。无pageerror、没有执行模型中的恶意img媒体请求。
- 前台47项常规测试、8项日记保存状态机与1项多表单保护测试通过。前台完整构建与压缩／性能预算通过：初始JS gzip129.8KB、CSS gzip9.6KB；后台类型检查＋构建通过；后端package打包通过。
- 前台全量vue-tsc仍有316个既有错误（音乐等原模块）；本次日记、Coaching及private-workspace文件0条。保留既有失败，不为使全仓检查变绿而扩大修改范围。构建仍有既有Sass弃用／包体积提示。
- 容器Nginx代理验收通过；OpenSpec严格验证通过；git diff --check无空白错误（仅既有LF/CRLF提示）。
- 应用、Vite／HTTP、本地测试和PG日志扫描未出现浏览器写入的独特虚构正文；审计投影测试不序列化私密DTO；没有读取生产日志或真实日记。生产日志验收属于后续部署。

### 资源与灾备

- 固定512MiB的隔离PG、原3连接上限，40篇完整月度日记＋20目标／80行动／100反馈，完整边界预览按预算明确拒绝生成，未截断数据。
- 最终40次预览测得p95 **39ms**；同时保存73次p95 **17ms**。早一次测量为58ms／18ms，均低于1秒目标。测试后PG观测73.89MiB／512MiB，未扩大配置。
- `coaching-recovery-qa.mjs`实际完成加密落盘、从文件解密、篡改拒绝、新库pg_restore、27张表逐表摘要一致、001／002／003复核、21张私密表FORCE RLS及多类型来源owner／实体关联；耗时19.82秒。恢复库 `diary_coaching_restore_1792086947424`。仅虚构QA AES-GCM往返，不冒充生产age灾备验收。
- 手册及关闭的外置开关例子见 `scripts/diary/COACHING.md`、`coaching-config.example.yml`。JSON导出有1000实体／20MiB硬限制；完整历史、幂等及AI引用走全库备份。

### 图谱与范围控制

- 已刷新并强制重建图谱，最后节点18770／边40015；FTS部分因工具链WAL重命名错误失败，修复重试也未解决。精确符号context／impact及调用关系查询仍可用；没有把FTS失败当作“无影响”。
- 普通scope=all不含未跟踪文件，另用临时Git index的intent条目覆盖87个未跟踪路径，真实index原始哈希验证未改变。完整变更为94文件／1622符号、225影响流程，风险CRITICAL；包括原有第二阶段和归档WIP，不能解释为全部属于本次新增。
- 工具硬限制最多列1000符号，故完整列表先被truncated。没有以截断结果通过检查：将同一份真实原始diff仅按文件边界分为10组，仍调用GitNexus scope=all，每组partial=false、truncated=false；并集1622符号／225流程与完整汇总严格相等。最终完整审计输出 `diary-coaching-graph-complete.json`，包含分组和diff摘要。原Git index、分支和HEAD未改变，没有提交或暂存用户代码。
- 核对了共享本人权限、PG事务、保存工具、AI执行、配额、引用与私密组件调用。HIGH／CRITICAL提前说明；UNKNOWN补查真实调用，不把空图谱当作安全结论。

## 规格到证据

| 规格 | 主要实现与验证 |
|---|---|
| private-diary-goal-tracking | Models／Store／Origins／Security／CoreOnly；目标状态与上限、owner隔离、ACK／CAS、来源采纳与删除、独立迁移／导出及浏览器守卫 |
| diary-action-feedback | Store／Periods及真实PG／Fault／浏览器：手工执行与评价、未知发生日期、纠正／软删除、历史、联动CAS和真实可评价分母 |
| evidence-grounded-periodic-review | Periods／Evidence／PeriodAi与原worker适配：自然周月、完整范围与摘要、实际预算、D/G/A/F、共享额度／租约、未知状态、来源过期和安全文本 |

## 保留的未完成项

- 6.1：第二阶段真实供应商契约与接入依赖验收未完成。只通过本地受控HTTP，原第二阶段任务没有代勾。
- 8.5：供应商、生成／Embedding模型未选择；真实模型事实、反馈、推测、引用语义与限制需用虚构资料人工验收。
- 8.8：没有本次生产部署授权；未连接生产、未迁移003、未开启开关，也未宣称线上可用。后续部署需要先备份、关闭功能发布，再由本人分别启用验收。

收尾已停止本次浏览器验收服务和Vite，将两个明确标记的QA容器恢复为原先的停止状态；其他服务、真实Git index、HEAD及既有WIP保持不变。
