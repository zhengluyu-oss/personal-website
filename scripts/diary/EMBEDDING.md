# DeepSeek 与百炼 Embedding 接入记录

记录日期：2026-10-16。本文只记录公开参数与验证状态，不保存任何密钥或真实日记。

## 已选组合与配置位置

- 生产配置：`/home/wwwroot/zhengluyu-website/config/application-prod.yml`，权限 `0600`。
- 生成：DeepSeek 官方 `deepseek-flash`，完整地址 `https://api.deepseek.com/chat/completions`。
- 向量：阿里云百炼华北2（北京）`text-embedding-v4`，1024维（模型默认值）。完整地址 `https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings`。
- 白名单只包含两个实际主机名；不能使用通配域名。若百炼控制台要求业务空间专属地址，应同时按控制台更新完整 endpoint 和对应精确主机名。
- 百炼需要独立的北京地域 API Key，填入 `diary.ai.embedding-key`；不能复用 DeepSeek 的 `generation-key`。

参考：[百炼 API Key 与北京 Base URL](https://help.aliyun.com/zh/model-studio/get-api-key)、[向量接口](https://help.aliyun.com/zh/model-studio/text-embedding-synchronous-api)、[模型及定价](https://help.aliyun.com/zh/model-studio/text-embedding-v4)。模型调用费用与 OSS、向量数据库存储费用相互独立。

## DeepSeek 请求兼容

`diary.ai.generation-thinking: false` 会在生成请求中明确发送 `thinking: {type: disabled}`。原客户端省略该字段，可能让默认思考消耗2000 token输出预算，导致答案截断或30秒超时。

配置缺省为 `null`，对未支持该参数的其他供应商不发送额外字段；设置 `true` 才明确启用。该开关不放宽原输出上限、引用校验、超时、响应大小或私密保护。针对实际HTTP请求体与Spring外置绑定增加4项测试；连同原云端／文本／证据／契约测试，18项针对性测试通过。

## pgvector 准备与迁移状态

原生产环境是 Ubuntu PostgreSQL 16.15，512MiB限制与3连接应用池保持不变。软件源只提供旧版pgvector，直接安装开发包还会升级其他系统库，因此没有执行系统包安装或升级。

已从官方仓库的 `v0.8.3` 固定提交 `586e7515bafe6912c425164d186d56550657c349` 构建。下载并仅解包与生产同版本的开发头文件，由无特权用户编译，关闭LLVM及本机CPU专用优化；只安装到PG16的扩展文件目录。源码归档SHA256：`a821827ee4653a201e174519ce9e283928a0e26d56d2247d6f4712630fc5e2cb`。

在服务器新建的临时隔离PG16.15实例中，验证了000／001／002／003、重复迁移、27张表、24张强制RLS表、1024维及cosine运算。该实例只通过专用Unix socket访问，验证后已停止；没有读取真实日记。

首次正式迁移曾被自动审批检查拒绝，未执行。2026-10-16 用户再次明确要求启动并完成剩余操作后，经正常执行流程完成正式库迁移，没有改换通道绕过拒绝。

正式库现已创建 `diary_vector`（pgvector 0.8.3）与 `diary_ai`。重复002通过，原001和003的结构指纹复核通过；迁移前后 `diary.entry`、`entry_revision`、`mutation` 的行数及数据摘要完全一致。AI的9张业务表均强制RLS，连同原日记与行为跟踪，共24张私密业务表强制RLS。`diary_runtime` 没有超级用户、BYPASSRLS、继承或角色成员权限；未绑定本人上下文和其他用户上下文的可见日记行数均为0。

## 真实模型校准与限制

两枚密钥均已由本人填入外置配置。百炼的真实批量调用检查了返回index、数量、1024维、有限值和非零范数；没有输出密钥。另用项目的20篇虚构日记和28个问题生成48个向量，共897个输入token。

依据部署代码的标题加正文、cosine阈值、词面分支和RRF规则进行校准。8个无依据问题的最高相似度为0.5396791354；20个有依据问题的预期来源最低相似度为0.5440574332。初始 `minimum-similarity` 取两者中点 **0.5418682843329728**，在这份语料上来源recall@8为20/20、无依据问题拒绝8/8。它是同一校准集上的结果，并非独立测试集或真实日记准确率；差距较小，实际使用仍需核对引用，不能保证对所有未知问题正确拒绝。

真实生成验收使用生产JAR中的云端适配器、原始提示词和引用校验器，不连接正式日记数据。首轮20个有依据问答：18个成功、1个被输出引用校验拒绝、1个返回资料不足；首轮复盘也返回资料不足。针对两道问题和复盘各进行一次独立人工复测，均通过格式及逐字引用校验。原始首轮与复测收据分开保存，没有把复测结果覆盖成“首轮全部通过”，正式系统也未增加自动重试。

这些测试证明实际供应商可以按协议返回引用和复盘，但模型有随机性，合法引用不保证解释正确。失败时仍按原机制停止，用户可以查看状态后决定是否手动重试，重试可能再次计费。

## 数据政策记录

外置 `data-policy` 已写明实际数据流：百炼北京服务接收索引标题与正文片段；DeepSeek接收所选问题、日记上下文或目标／行动／反馈资料。百炼声明不使用用户数据训练，但会依法存储调用产生的数据，没有据此承诺零保留；本项目没有开启完整推理日志。

DeepSeek通用隐私政策说明输入可能用于改进服务、在中国处理存储，保留期限取决于目的和法律义务；它同时区分开发者下游应用的责任。本账号没有另外取得API专属不训练、固定保留期限或逐次删除承诺，因此明确记录“尚未确认，按可能保留处理”，没有把无状态接口误解成无日志、零保留。暂停或删除本站报告不等于撤回供应商已处理的数据。

参考：[百炼隐私说明](https://help.aliyun.com/zh/model-studio/privacy-notice)、[DeepSeek隐私政策](https://cdn.deepseek.com/policies/en-US/deepseek-privacy-policy.html)。

## 生产启用与待本人操作

生产外置配置已启用 `diary.ai.enabled`、`diary.coaching.enabled`、`diary.coaching.ai-enabled`，并修正原来的 `enable` 拼写。只修改AI与coaching两个子段，其他生产配置及日记数据库连接保持一致。配置权限0600，服务重启后原发布健康接口200；部署JAR的Spring配置绑定和实际runtime数据库可用性检查通过。

每日额度保持20次生成、200个向量输入，队列5；PG连接池与512MiB内存限制保持原值。未登录的日记、AI和coaching能力接口均返回401，并保留 `private, no-store` 与禁止索引标记。首次健康检查误用了不存在的路由，保护逻辑还原配置；改用原发布脚本既有的 `/site-modules/access` 后重新启用并通过检查。

**2026-10-16 本人在前台确认并点击启动后，首次历史日记索引已完成：16/16篇当前日记READY，29/29个片段处理完成，无FAILED／UNCERTAIN／QUOTA异常，INDEX_START任务SUCCEEDED。** 当日应用预算记录为29个向量输入、0次生成；`syncing=true`，后续已保存的新版本继续按既有额度增量处理。浏览器控制连接未响应，因此启动确认由本人完成，随后只读取服务器汇总状态核验；没有伪造登录凭据、改库启动或输出真实日记内容。

登录后的正式问答、复盘与目标行动操作仍待实际使用验收。不能把索引完成或以上虚构语料验收写成正式本人全流程均已验收。

## 备份与排错收据

迁移前后均执行完整数据库加密备份，下载到本人电脑并校验SHA256。离机目录：`C:/Users/Administrator/Documents/私密日记备份/`。

- 迁移前：`diary-20261016T064946-3318590.dump.age`。
- 迁移后：`diary-20261016T070604-3324770.dump.age`，包含新增schema的完整数据库备份。
- 服务器迁移、配置和runtime收据：`/root/luyu-releases/rag-activation-20261016T0650/`。
- 虚构语料校准及真实生成收据：`/root/luyu-releases/rag-calibration-20261016/`。

收据与配置备份仅root可读，不提交真实配置、密钥或日记到Git。运行时回退仍为关闭AI/coaching相关开关并重启，保留数据库、扩展和备份，不删除原文或迁移版本。
