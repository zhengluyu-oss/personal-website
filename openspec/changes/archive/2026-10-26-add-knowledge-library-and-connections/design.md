## Context

用户已确认三项决策：仅站主维护、读者复用前台模块授权；知识库使用现有 PostgreSQL 实例并与日记隔离；首版为笔记、文件解析、关键词搜索、人工关系图及整理为文章草稿，不包含模型调用。

现有站点是 Vue/TypeScript + Spring Boot Java 单体，博客账号/文章在 MySQL，日记使用显式绑定的 PostgreSQL 连接和事务；RabbitMQ 已用于异步任务，前台已有 ECharts。当前部署环境为 4 核 4GB/40GB，不能以新增常驻搜索集群、本地模型或无限并发作为前提。

已核对的集成点：SiteModuleAccessService 管理导航模块授权，DiaryDataConfiguration 展示小连接池与限定事务的做法，ArticleServiceImpl.publish 和 ArticleDTO 要求分类、标签、封面且有封面授权生命周期。FileUploadUtils.upload 返回公开地址，不能直接承载受限资料。

## Goals / Non-Goals

**Goals:**
- 正式文章与积累中的笔记分开，知识库建立“记录/收集—检索—关联—整理为草稿”的完整路径。
- 授权读者可读，只有配置站主可写；原文件、搜索、关系边和来源快照服从同一边界。
- 保存可靠、任务可恢复、同一结果不重复生成，明确处理删除/替换与延迟消息的竞态。
- 在现有机器和技术栈内，提供可验收的功能、性能样本和失败恢复证据。

**Non-Goals:**
- 不做多用户各自知识库、逐条分享、独立访客白名单或多人协作。
- 不做 OCR、音视频、网页抓取、DOCX/压缩包解析、语义搜索、Embedding、自动关系、AI 问答、自动写文章或本地模型。
- 不迁移或删除既有博客文章，不读取日记正文，不修改本次无关的登录修复。
- 不更换主框架、数据库引擎或公共媒体域名，不预设增加服务器。

## Decisions

### 1. 页面与权限：一个知识库入口，站主管理、授权只读

导航新增 key=knowledge、路径 /knowledge。页面提供“笔记与资料”和“知识关联”两个主入口；搜索、条目详情沿用阅读布局，站主看到编辑、上传、任务和整理草稿操作，读者不渲染可写入口。关系图同步提供可键盘操作的关系列表，移动端优先列表，不把复杂图作为唯一入口。

后台继续在“角色管理→前台模块访问”分配知识库可读权限。新策略默认 restricted、无普通角色授权；缺少迁移或策略时知识库拒绝读取，原模块仍可用。与现有模块一样，可以明确设置公开；公开前提示整个有效知识库及原文件可能被访问，要求确认。

模块授权覆盖全部有效条目，没有新设逐条可见级别。ADMIN 只能按现有规则取得阅读资格，写操作还必须匹配 knowledge.owner-user-id；站主也必须是当前有效账号且有模块权限。拒绝客户端提供 owner 作为身份来源。

/knowledge/view/capabilities 只暴露 allowed/available/canManage；其余 /knowledge/view/** 每次执行实时授权，/knowledge/manage/** 要求站主；搜索建议、统计、原文件读取、图谱和导出任务同样受限。未授权保留模块外框，不显示真实数量、标题或结果。禁用/撤权后新请求即时拒绝，前端清空已加载内容并丢弃迟到响应，不在 localStorage/service worker 中缓存知识正文。已下载的文件无法远程收回，授权提示中说明这一边界。

### 2. 数据与配置隔离

knowledge.enabled 默认 false；knowledge.owner-user-id、knowledge.postgres.*、knowledge.storage.*、knowledge.processing.* 使用外置配置，不借用 diary.owner 或 AI 开关。允许同一 PG 实例，但 knowledge 使用独立 schema 与运行角色；运行角色仅能读写 knowledge，不能读 diary，迁移角色与运行角色分离。

knowledgeDataSource / knowledgeJdbcTemplate / knowledgeTransactionManager 全部显式限定、不成为 primary/defaultCandidate，保留现有 MySQL 默认与 diary 绑定。PG 池最大 2、最小空闲 0，连接/查询有限超时；禁用不建连接、不声明消费，不因配置缺失拖垮原博客。启用但不完整时能力返回不可用，禁止假保存或隐式写 MySQL。

建议表：
- entry：UUID、owner、kind(NOTE/DOCUMENT/CONCEPT)、标题、Markdown、来源文本、分类、revision、current_file_version、created/updated/deleted_at。
- entry_revision：不可变快照；mutation：owner+mutationId 唯一、请求摘要和提交结果。
- category/tag/entry_tag：仅知识库领域使用，删除分类/标签先检测引用，禁止级联删除正文。
- file_version：条目、版本、随机 object_key、hash、大小、受控 MIME、原始文件名、有效/清理状态；替换不覆盖旧 key。
- extracted_segment：文件版本、ordinal、页码或行范围、正文；只在完整解析并确认版本仍有效时切换当前结果。
- processing_job / outbox：任务、版本、状态、attempt、lease/fence、错误分类和事件。
- relation：两个类型化端点、relation_type、说明、revision 和删除标志。
- article_export：幂等 exportId、选中来源及版本快照、编排正文摘要、状态、articleId。

UUID 保留墓碑，删除/恢复递增 revision。内容保存使用 mutationId+请求摘要、乐观锁、当前行与历史快照同事务；不静默覆盖冲突。后台任务绑定 owner 和版本，数据库身份上下文通过事务 SET LOCAL 设置；相关表强制 RLS，应用角色无 BYPASSRLS/DDL 权限。公开/授权阅读的数据库上下文是服务端配置站主，不能误用访客 ID。

### 3. 文件保护与生命周期

原文件按站主最新决定存现有 OSS Bucket 的独立 knowledge/ 前缀，复用当前服务器凭据；不改变 Bucket 的 ACL/策略，不复用 publicUrl()。每次 PutObject 请求必须原子地指定 private Object ACL，不允许先按公开继承权限上传再修改。上传前以随机探针验证 Object ACL 为 private、OSS 原域名和现有媒体域名的匿名请求均无法读取；不能证明匿名不可读时拒绝开放文件能力。共用凭据的权限范围较大，作为已知风险记录。

首版上传由已鉴权后端流式接收至受限临时目录、校验并写 OSS；无需改现有浏览器直传协议。单个 PDF 默认上限 20 MiB、最多 300 页；Markdown/TXT 5 MiB、UTF-8；正文/提取结果默认每条最多 2 MiB，标题最多 200 字；知识库原文件有效/保留版本总配额默认 1 GiB。限制值从服务端返回用于前端提示，代理/Servlet/业务校验一致，不放大原图片接口上限。

校验扩展名、受控 MIME 与文件特征，拒绝加密/损坏 PDF、HTML伪装、压缩包、空文件和超限；普通 PDF 中嵌入动作/附件不执行，Markdown 原始 HTML 禁止执行。远程图片默认不自动加载，仅显示外链占位，避免阅读时意外外发请求。资料“来源链接”只保存与显示，不服务端抓取。

写 OSS 与 PG 不是原子事务：上传先分配临时键/登记；PG 成功后认领，超时或登记失败由仅扫描 knowledge 临时前缀的清理任务回收。清理幂等，失败显示待清理，不允许删除其他模块资源；禁止在事务持锁期间等待大文件网络传输。

GET /knowledge/view/files/{entryId}/{version}/content 必须重新验证当前条目、有效版本和模块权限，服务端读取私有对象并流式返回；不能接受任意 objectKey/URL，不返回永久/可复用外链。响应 private,no-store、nosniff、noindex，安全文件名、Content-Disposition: attachment。前端原文件下载通过带认证的请求，不把 token 放 URL。正文定位显示解析页码，不要求实现完整 PDF 在线标注器。

软删立刻撤下正文、原文件、搜索和图谱端点并取消任务；回收站仅站主可见、可恢复，默认保留 30 天，保留期间计入配额。超期显式清理对象和历史；对删除到恢复之间仍在执行的任务要求重新排队当前版本，旧 lease 不得恢复已删数据。

### 4. Java 异步解析与资源边界

上传登记的同一 PG 事务创建 job+outbox。定时 relay 使用 RabbitMQ publisher confirm，确认后标记已投递；confirm 丢失允许重复投递。队列为 knowledge 独立 exchange/queue/retry/dead-letter，不更改邮件队列策略。消息仅携 jobId/version 等标识，不含正文、签名 URL、账号秘密。

消费者默认 concurrency=1/prefetch=1；原子领取带租约与 fencing token 的 job，重复消息命中已完成任务直接 ack，过期 RUNNING 由恢复扫描重新调度；PG 是任务真相来源。消息 ack 发生在处理结果及任务状态持久化之后。可重试基础设施故障最多 3 次、延迟退避，永久格式错误不重试；失败进入可读 FAILED，站主可显式重试或停止，不做无限重入。

文本型 PDF 使用 Apache PDFBox 3.x 文件读取器按页提取；Markdown/TXT 使用 Java UTF-8 流按段/行处理。扫描 PDF 无可提取文字时明确为 NEEDS_OCR（首版不支持），不得显示已建立可检索正文。

PDF 解析在同一服务器按需启动的短生命周期 Java 子进程中执行，不是新增常驻 Web 服务，不使用 Python。子进程无 DB/OSS凭据、无网络调用，stdin/受限文件传输入，stdout 有大小上限的结构化结果；-Xmx256m、MaxDirectMemorySize64m、墙钟超时 60 秒，超时/异常退出/OOM 结束进程并记失败。临时目录默认总配额 256 MiB、任务独立目录，finally 与重启巡检仅回收自己登记的路径；实现须支持 Linux 生产及本地开发，不拼接 shell 执行用户文件名。Xmx 不是总 RSS 上限，验收必须额外量测原生内存和主进程影响。

任务完成前重查条目未删、当前文件版本匹配、lease fence 有效，旧版本/旧工作者结果不得覆盖新版本；失败不暴露残缺分段。原文件仍可按权限下载，页面明确“可检索结果暂不可用/正在处理”，不能把旧正文当新文件正文。

### 5. 关键词搜索：首版明确支持中文，不冒充语义检索

采用 PG 参数化字面子串检索，标题/标签优先、正文/当前有效提取片段其次，更新时间与 UUID 作稳定排序。对 %、_、转义符处理为字面量，查询不执行 SQL/正则，不直接把用户内容插入 HTML 高亮。第一版不依赖 PG 默认分词器对中文分词，不安装新全文检索服务或扩展。

搜索默认每页 20、最大 50、查询长度 1–100，匹配条目去重，片段有页码/行范围和 revision，全部限制在当前可见条目；分类标签/类型/更新时间可组合。权限在过滤与统计之前执行，搜索不碰 diary。以最多 1,000 条目、20,000 分段的固定中文测试集测量；报告真实 P50/P95、SQL计划及测试配置，目标 P95<=1秒（热缓存、单查询并发），达不到则先优化查询/分页，不擅自换架构。

### 6. 手动概念关系与图谱

NOTE、DOCUMENT、CONCEPT 均可作知识库端点；博客文章是受限外部端点（类型+MySQL文章ID），不复制其正文到概念表。关系为 EXPLAINS(解释)、DEPENDS_ON(依赖)、REFERENCES(参考)、RELATED_TO(相关)；前3类有方向，相关为无方向且规范端点顺序。拒绝自环、相同类型重复边、无效端点和跨 owner 引用，允许不同语义边；不强制图无环，依赖关系只是知识说明，不执行任务依赖。

图谱按中心条目展开默认一跳，最多两跳，默认 50 节点/100 边、硬上限 100/200；服务端返回 truncated/hasMore，列表能分页查看其余邻接，禁止载入全部知识库后由浏览器隐去私密节点。节点文字取实时可见标题，边两端都可见才返回；不可见节点不计入读者 total。文章端点还检查现有 blog 模块访问与文章公开状态，草稿/私密文章只对有权站主可见，文章下架/删除时实时隐藏该边。

复用已有 ECharts graph 懒加载，不增加图数据库。缩放、节点定位与关系类型说明提供最小探索体验；移动端及键盘提供等价列表。概念/笔记删除后读者看不到其边，恢复仅在端点仍有效时恢复展示。

### 7. 从知识库到博客草稿：显式复制、跨库幂等

站主从最多 10 个有权来源的固定 revision/文件版本选择笔记或片段，手工编辑最终草稿预览，保留来源说明。首版不调用 AI 自动编排；最终正文采用现有文章安全渲染与长度规则。需要本人同时具备现有 blog:publish:article 权限，在提交时选择现有有效分类、标签并完成封面上传授权，UI 明示这些字段；不能传空分类/伪造封面绕过 ArticleDTO 和封面生命周期。

流程：PG先记录 exportId+请求摘要+来源快照（包含最终预览内容及字段）→ 同进程调用带 MySQL 独立事务的草稿适配服务 → PG写回 articleId。不把跨库写包在一个 @Transactional 中，也不引入分布式事务。

MySQL新增 t_knowledge_article_export，(owner_user_id,export_id)唯一、保存摘要与article_id；该映射与 t_article(status强制3)、标签、封面认领在同一 MySQL 事务提交，成功返回 articleId。适配复用现有文章业务校验与封面生命周期，抽取必要公共内部方法，不改变现有 /article/publish 成功响应或既有公开流程。

相同 exportId/摘要重试返回同一 articleId；摘要改变报冲突。MySQL成功PG回写失败时用该唯一映射对账恢复，不再生成第二篇；PG成功MySQL失败时显示待重试，不虚报创建成功。每次人工重试/后台对账需检查账号仍有效、模块及文章写权限仍允许；系统不会自动公开。已经生成的草稿删除后不得因查询幂等结果悄悄重新创建。

来源快照只给站主，公开文章不得自动附上私有对象键、内网地址、日记内容或读者无权访问的来源链接。草稿是独立副本，后续笔记修改/删除不自动改写它；发布及修改继续走现有后台，由站主明确操作。现有文章不搬迁，未来迁移另行规划。

### 8. 交付边界与实施顺序

按依赖顺序：存储与权限→笔记→私有文件与解析→搜索→关系→草稿适配→联调与恢复验收。所有新增迁移为版本化脚本、默认不连生产；旧模块授权、7模块兼容校验/探针需改为接受新注册 knowledge 的安全白名单，不能只扩大任意数组长度。

关闭 knowledge.enabled 后不注册消费者/任务，也不可写知识库；公开博客和日记继续原行为。禁用不是删除，原文件和数据保留；回滚保留新增 schema 与 MySQL映射表，禁止自动DROP或清空。

## Risks / Trade-offs

- [同机文件解析抢占内存] → 单并发、短生命周期 Java 解析进程、限时限量、压力样本；禁止将全部PDF读成byte[]，不为演示提高并发。
- [私密文件绕过角色访问] → 同Bucket独立前缀、原子private Object ACL与匿名探针、每次鉴权代理读取，无永久地址；不能使用隐藏URL代替授权。共用现有密钥的权限范围大于最小权限原则。
- [新增PG数据源改变MySQL装配] → 显式限定+defaultCandidate=false，真实三数据源并发装配与回滚测试。
- [数据库已提交而消息未投递] → PG outbox、confirm、重发和幂等消费；不承诺消息恰好投递一次。
- [中文扫描搜索规模受限] → 明确数据规模与测量，字面匹配保证中文可用；向量/更复杂索引留后续。
- [图谱泄漏文章或删除节点] → 服务器逐端点实时过滤并限制局部展开，不在前端隐藏全图。
- [跨库草稿重复或半成功] → MySQL本地唯一映射与文章同事务，PG快照与可重试状态、对账恢复；不擅自自动发布。
- [新功能范围过大] → 首版限三文件类型和人工关系，未来AI不建占位开关、不产生外部调用；以端到端验收而非工具数量评估毕业设计成果。

## Migration Plan

1. 实施前刷新图谱（当前MCP检索存在旧索引/异常条目，不能依此作低风险判断），对实际改动符号执行impact并记录HIGH/UNKNOWN边界；保护未提交登录修复。
2. 隔离测试库运行 knowledge PG迁移、运行/迁移角色与RLS；MySQL新增模块和草稿映射，重复执行/失败回滚测试。
3. 在现有 OSS Bucket 配置独立 knowledge 前缀，使用现有服务器凭据原子写入 private Object ACL；用合成探针验证 OSS 原域名和媒体域名匿名拒绝、应用角色不能越界，不改变现有公共资源。
4. 本地/测试环境完成安全、可靠性、中文检索、图谱裁剪、三数据源和4GB预算测试，保存报告；准备数据库与对象清单备份/恢复脚本。
5. 生产发布另行说明并获用户授权：先备份并在隔离库恢复演练，再迁移，部署时保持 knowledge.enabled=false，校验原模块健康。
6. 配置独立运行角色/私有存储后小范围启用，站主本人验收、普通角色只读/撤权/匿名拒绝和公开开关确认；以并发1处理合成文件观察峰值。
7. 回滚时先停消费者并关闭功能，再恢复应用产物；保留数据库/对象，租约到期后可恢复处理，无自动补写公开文章。

## Open Questions

无阻塞性产品选择。资源限值为首版可配置默认值，实施时用固定样本验证并记录实测，不以猜测保证4GB上限。生产同Bucket私有对象探针和低权限PG角色需在部署步骤实际验证，在此之前文件能力必须失败关闭。

## References

- Apache PDFBox Java依赖与文件读取/内存注意事项：https://pdfbox.apache.org/3.0/getting-started.html 、https://pdfbox.apache.org/3.0/migration.html 。实施前复核选定3.x补丁及传递依赖。
- RabbitMQ publisher confirms与consumer acknowledgements：https://www.rabbitmq.com/docs/confirms ；重复投递与恢复：https://www.rabbitmq.com/docs/reliability 。
- PostgreSQL文本解析器边界：https://www.postgresql.org/docs/current/textsearch-parsers.html 。首版中文使用参数化子串查询，不宣称内置英文分词即可完成中文搜索。
