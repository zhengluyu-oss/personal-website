# 广拓时代项目经历内容修订

本目录保存广拓时代经历的可审核、可维护正文。`content.json` 中的字段与现有工作经历、项目接口保持一致，项目正文分别在 `project-4.md` 至 `project-9.md` 中。每次更新线上正文时，需要先核对现有记录、备份数据库，再执行受限的文本字段更新与公开接口验证。

## 使用与范围

- 在仓库根目录执行 `node scripts/preview-guangtuo-experience.mjs`，打开输出的本地链接，可同时预览新排版和新正文。
- 预览仅匿名读取公开经历接口；不接收写请求、不传递浏览器身份，也不连接管理端或数据库。
- 新正文替换原叙述，但保留线上已有“功能示意图”部分、封面、名称、日期、角色、项目归属、排序和发布状态。
- 仅覆盖经历 2 和项目 4 至 9。其他经历继续使用原始公开内容；未新增数据库字段、未修改接口契约。
- 正文是基于代码能力与已有参与范围撰写，不意味着代码中的所有模块均由个人独立完成。没有新增开发日期、业绩数字或客户信息。

## 本地证据索引（不应上传到博客正文）

代码根目录位于 `D:/D_work`。下列资料为本次内容核查依据，不作为个人提交历史或独立完成的证明。

| 项目 | 核查位置 | 支持的内容 |
| --- | --- | --- |
| 4 验收 | `A_yanShouSystem/acceptance_system/acceptance_system_java/web-front/src/views/keyword-spot-check/KeywordSpotCheckCreateDialog.vue`、`KeywordSpotCheckDetailPage.vue` | 多平台配置、筛选草稿、分析开关、进度、筛选与导出 |
| 4 后端 | 同仓库 `java-backend/ujcms-core/src/main/java/com/ujcms/cms/ext/flow/service/FlowJobService.java` | 关键词任务校验、固定目标计算、步骤物化、继续执行与回写 |
| 4 消费者 | `A_yanShouSystem/monitorKeywords/monitor_keyword/gt_cron/process/flow_job_keyword_search_consumer.py` | 消息检查、能力处理器、工作者与流消费封装 |
| 4 登录助手 | `A_yanShouSystem/python_metiLogin/meiti_login/src/renderer/src/views/AccountListView.tsx` | 列表筛选、分页、账号维护与占用状态 |
| 5 号卡 | `C_haoKaLianMeng/admin_haoka_vue/mobile-vue/src/views/manage/product/commission.vue` | 结算规则、默认与指定合作方金额及约束 |
| 5 海报 | 同前端 `src/views/manage/product/poster/components/NewPosterModal.vue` 及字段编辑器 | 模板、卡片和详情分层、表单初始化与生成校验 |
| 5 后端 | `C_haoKaLianMeng/backend/app/Manage/Service/ManageOrderImportTaskService.php`、`Service/Order/OrderCommissionPaymentService.php`、`Controller/WithdrawalController.php` | 导入明细状态、出入账锁与流水、财务操作校验 |
| 6 官网 | `B_guangtuo_offecial_website/guangtuo-website/apps/web/lib/strapi.js`、`apps/web/app` | 实体关联与媒体标准化、页面路由和 metadata |
| 6 服务端页面 | 同仓库 `apps/seo-web/src/routes/pages/news-geo-handlers.js` | 新闻与地区内容的服务端页面、规范地址与分页；未混为 Next.js 的实现 |
| 7 内容工具 | `B_guangtuo_offecial_website/text_management/machineAndUpload/web/src/api/sse.ts`、`views/ArticleManageView.vue` | 流解析、取消、鉴权异常、文章管理与上传配置 |
| 7 后端 | 同仓库 `backend/app/schemas/stream_event.py`、`services/article_guard.py` | 事件结构、阶段枚举及发布前状态约束 |
| 8 展示页 | `D_GEO/GTark/ai_geo_system/admin-react-front/client/src/pages/Landing.tsx` | 区块、锚点与登录路由；无行为按钮不算已完成业务功能 |
| 9 查询 | `G wx_channels/wx_channels_download/internal/channels/search_paged.go`、`feed_comments_paged.go` 及同名测试 | 搜索聚合、评论去重、请求预算和截断元信息 |

## 补充范围与归属说明

1. GEO 的关键词、对话信源、引用导出与报告流程已依据实现补入项目 8，明确为配套工作台的业务和技术说明；不把未经证实的所有权或独立研发结论写入个人贡献清单。原项目名称、图片及已确认职责保持不变。
2. 直播录屏评论识别与截图归档已补入项目 4，并明确是配套的 Python 离线链路，不声称它已经通过 Java 验收后端统一调度。模拟测试素材与真实录屏识别结果分别说明。
3. 上线时仍需核对最新公开记录，防止覆盖本次撰写后在后台进行的其他内容更新。当前修订没有改动数据库结构或后台编辑字段。

### 新增源码依据

- `D_GEO/GTark/ai_geo_system/geo-front/client/src/pages/brand/brand-keyword-search.ts`：主题与关键词搜索、启用状态、筛选交集及空集合语义。
- 同应用 `client/src/lib/citation-markers.ts` 与 `pages/conversation/ConversationRecordDetail.tsx`：回答清洗、思考区、平台引用角标与信源跳转。
- 同应用 `client/src/pages/citation/citation-sources-export.ts`：导出字段、平台名称、进度与大数据量提示。
- 同应用 `client/src/pages/reports/Reports.tsx` 与 `server/modules/report/report.service.ts`：生成后列表刷新、报告状态、项目权限、主子账号归属与额度检查。被注释的重复生成检查没有写成已启用功能。
- `A_yanShouSystem/monitorKeywords/monitor_keyword/gt_video_processor/ocr_phase2/pipeline.py`、`phase2_parse_stage.py`：阶段调度、准备锁、解析核心指纹、产物验证与复用；配合源仓库 AGENTS 中的离线处理入口核对输出模式。

## 验收重点

桌面与手机布局、明暗主题、长内容目录、同公司项目切换、空字段、加载失败和旧版无标签职责文本；预览数据须保留日期、图片与项目身份。所有图片沿用已存在的功能示意图，不再生成不对应真实功能的新图。

## 本次验证记录

- `pnpm run build`：通过，包括压缩文件校验和项目现有初始资源预算。构建仍有既有 Sass 弃用和大分包提示，没有在本次内容任务中扩大范围处理。
- `pnpm test`：原有 45 项测试通过。
- 在仓库根目录执行 `node --experimental-strip-types --test blog-frontend/kuailemao-blog/src/utils/experience-presentation.test.ts scripts/guangtuo-experience-content.test.mjs scripts/prepare-guangtuo-update.test.mjs`：8 项测试通过，覆盖职责标签、旧段落兼容、数据范围、图片日期保留和更新计划身份校验。
- 本地 Edge 自动化：六个详情、原图区域、1440 / 768 / 390 宽度无横向溢出、章节跳转、手机目录、另一家公司、空封面与无目录旧记录、加载失败均通过；写请求及后台列表请求被本地预览拒绝。
- 目视检查：公司页、项目首屏、正文、手机布局和项目深色样式。全站页头已有的深色背景与文字对比问题不在这次经历模板修改中处理；没有运行 Lighthouse，不提供虚构的性能或无障碍评分。
- 上一轮内容与排版已于 2026-10-05 提交为 `b90de6581` 并上线；本节其余记录描述该轮验证，两项扩展范围按上节的归属边界补充完成。

## 内容更新准备

`node scripts/prepare-guangtuo-update.mjs` 只读取公开记录并输出更新计划，不会向服务器写入。本轮进一步展开各项目的具体操作路径、状态流转和异常核查，1 条公司记录及 6 条项目记录的合并正文（含原图库 Markdown）共 23,941 字符。计划限定可更新的文本字段，并记录前后内容摘要；名称、日期、封面、状态、排序及项目归属不在更新字段中。

正式发布时须先备份权威数据库记录，再逐项比较当前内容与计划中的 before 值；任何差异都应中止更新，避免覆盖后台新编辑。公开接口读取结果不等同于完整数据库备份。
