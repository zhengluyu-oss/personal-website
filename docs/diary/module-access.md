# 日记接入统一模块权限

2026-10-18已完成生产受限初始化及前台、后台和后端发布。历史生产记录中的“仅本人”适用于旧版；本人账号与授权角色的完整实机操作验收仍待完成。

## 配置和数据范围

后台「用户与权限 → 角色管理 → 前台模块访问」新增「我的日记」，复用公开/角色设置、有效ADMIN规则、实时角色成员及revision并发检查。给用户授角色仍用“授权用户”。日记默认不公开、不授普通角色；缺失diary策略仅让日记失败关闭。

获授权用户查看站主当前日记及可展示的已有分析，不是独立日记空间。公开允许匿名阅读；后台须确认其影响。编辑、删除、历史恢复、批量导出、索引、模型调用及目标行动变更仍仅站主可执行，其他ADMIN也不能执行。撤权不能收回外部已复制内容。

## 接口矩阵

| 路径 | 行为 | 边界 |
| --- | --- | --- |
| GET `/diary/view/capabilities` | 可匿名探测allowed/available/canManage | 不返回owner |
| POST `/diary/view/entries/search` | 当前记录筛选/分页 | 模块授权；拒绝deleted=true |
| GET `/diary/view/entries/{id}` | 正文 | 模块授权；删除记录404 |
| GET `/diary/view/calendar` | 写作日期计数 | 模块授权；排除删除 |
| GET `/diary/view/reports/{ai或period}` | 报告分页 | SUCCEEDED且所有来源当前有效 |
| GET `/diary/view/reports/{ai或period}/{id}` | 报告和原文 | 同PG快照复核版本、删除、Unicode范围和hash |
| GET `/diary/view/coaching/{goals或actions或feedback}` | 当前人工记录 | 无origin、任务、历史载荷 |
| `/diary/auth/**` | 原日记、AI和目标管理 | 原站主限制叠加模块权限 |

原`/diary/auth/capabilities`兼容旧编辑器，新外壳使用`/diary/view/capabilities`。只读控制器明确列举接口，安全链在正文绑定前判权，控制器再次校验。搜索是只读POST，生成是管理POST，不按HTTP方法粗分权限。

共享读取从服务端配置派生站主归属，不取客户端owner或访客ID。只读报告事务不初始化embedding profile、不写失效标记、不轮询任务、不调用模型；复用受限diary_runtime、原连接池、强制RLS和事务局部身份。来源修改/删除时整份报告隐藏，不清空站主历史结果。

响应继续private/no-store和noindex/nofollow/noarchive；只读页面也设置noindex。不加入公共搜索、推荐和站点地图，不持久化正文或采集正文日志，公开策略不改变这些保护。

## 发布与回退

1. 备份JAR、blog/admin目录及MySQL的sys_site_module/sys_site_module_role，保护备份；保留原PG备份流程。
2. 已有模块表执行`sql/diary-module-access.sql`，全新环境用`sql/site-module-access.sql`。INSERT IGNORE可重复执行，不覆盖公开状态、revision或角色。确认diary为public_access=0且无普通授权。不修改PG结构、owner、日记或模型密钥。
3. 发布后端，再发布前后台。健康探针同时接受旧六模块和新七模块，以支持旧版回退；新版本另验diary存在且受限。新前端收到旧六模块时仅关闭日记访问，其他模块继续可用。
4. 匿名应可见导航和受限框架，正文401；使用本人会话验证管理，测试角色和合成内容验证只读、403管理和撤权。不要为验收公开真实日记，不伪造登录令牌，不调用真实模型。未实际发布/验收前，任务5.5不完成。
5. 回退先恢复受限策略，再恢复应用和前后台。旧版恢复站主专属语义；保留PG日记、报告、备份及新增策略行，不删库、不改秘密。

## 本地证据

- 42项后端针对测试通过，无跳过：模块规则和并发更新、共享归属/伪造owner、匿名和角色权限、撤权、管理/计费拒绝、真实PG的D/G/A/F报告源与连接复用、原可靠保存，以及原模块拦截/元信息/管理权限回归。包含首轮30项、补充1项PG来源测试及11项原模块回归，不将重复执行累计成新增测试。
- 隔离MySQL验证默认受限、7模块及重复初始化保留策略/revision/角色。
- 路由与旧六模块兼容测试3项通过；前后台构建及资源/秘密门禁通过。
- `scripts/diary/module-access-ui-qa.mjs`：桌面及360/390px只读日记、已有问答/期间报告/目标、原文定位、匿名公开、撤权清空和迟到响应丢弃、作者原工作区。
- 原`workspace-ui-qa.mjs`回归：草稿和原问答/复盘/目标交互，360/390/768/1440px明暗主题，异常和身份失效清理。
- 发布恢复测试在一次性Linux Python容器11项通过，含旧六模块回退。Windows Git Bash路径转换会影响测试；shell发布文件须保持LF行尾。
- GitNexus修改前核查确认requireOwner为CRITICAL影响，因此保留其原实现；新只读链路独立。增量刷新曾提示FTS不可用，随后完整重建成功，19,508节点、41,860关系、1,172流程。重建后最终detect_changes覆盖24个已跟踪文件、57个符号及19条流程，列表57项、无partial/truncated，风险CRITICAL，主要来自统一policy/canView涉及共享权限核心路径；这些入口已由模块/管理/元信息及日记测试覆盖。新增未跟踪源码另逐文件核查，图谱流程提取存在预算上限，不能将前次旧索引的低影响结果或未列出的路径当作无风险证明。
- 后台真实模块权限组件在独立Vue/Ant Design测试壳中通过：7模块、日记范围文案、取消公开无写请求、角色保存、明确确认公开、排除停用角色、冲突提示。测试壳补齐ConfigProvider，未使用真实后台会话或生产API。
- 浏览器补充验证：日记页面noindex；站主遇到临时权限探针故障时，未保存的目标草稿保持在内存，不因重新挂载丢失。外部资源被主动阻断，与业务请求失败区分记录。
- 前端可靠保存单元测试8项通过；隔离PG/MySQL测试容器已按创建时ID及专属标签核对后清理，仅清理本轮合成数据。后台测试壳服务已停止。
- 本地测试仅使用合成样例。此次部署仅在服务器进程中读取既有外置数据库配置以备份和初始化模块策略，未将秘密回传聊天、写入仓库或发布目录，未读取真实日记、创建登录令牌或调用真实模型。

## 2026-10-18 生产发布记录

- 功能提交：`47718e6e1`（后端和受限初始化）、`0edb890ff`（前后台工作区）、`8d640ac15`（部署兼容和文档），均为本地提交。
- 发布目录：`/root/luyu-releases/diary-module-access-20261018-0edb890ff`，含受限的`receipt.json`、`manifest.json`、`previous-artifacts.tar.gz`和`module-policies-before.sql`。旧JAR和前后台资源完整备份，策略备份保留在root专属目录。
- MySQL原有6模块策略和全部角色授权逐项未变；新增diary为`public_access=0`、`revision=0`，无普通角色授权。未迁移PG、修改数据归属、日记内容或密钥。
- 后端JAR SHA256：`85eaa1d3c5ab2eb067684096a6e60854ffc3d556e484339c36126cbfa9450817`；前台入口：`e485a1433be140c017537731abff205878051e4f1ca5d3428d0a40f34a6a82b3`；后台入口：`025ac44bfc6f59723758596df2dbf2e503b98e13a7c5daa53dc4c5118a15ee3c`。线上与本地构建一致，外置生产配置全文摘要前后相同。
- 后端重启和Nginx检查通过，服务active；`/api/site-modules/access`返回7模块且匿名diary.allowed=false。只读capabilities返回allowed/available/canManage均为false。
- 匿名正文、POST搜索、AI报告、期间报告、目标读取、AI生成和旧资格接口共7项均401，保护响应含no-store/noindex；`/diary`和`/admin/`入口200且字节摘要匹配，日记页面维持no-store/noindex。
- 发布脚本确认健康后完成切换，旧版本可由持久备份恢复。未将匿名与部署检查当作真实用户授权、撤权、站主读写验收，任务5.5继续保留未完成。
