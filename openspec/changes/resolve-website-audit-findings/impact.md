# 修改前影响分析

GitNexus 索引比当前提交落后 3 个提交；本轮尝试更新索引未成功。因此以下结果结合静态文本核对使用，不把 UNKNOWN 或 0 个调用方解释为安全。后续编辑其他函数前继续按同一规则检查。

| 目标 | GitNexus 上游结果 | 源码补充核对与验证重点 |
| --- | --- | --- |
| 管理端 `createVitePlugins` | LOW；1 个直接调用，未关联执行流程 | `vite.config.ts` 调用它；移除全量预加载后验证登录及编辑页面的资源和功能 |
| 前台 `setSeoMeta` | MEDIUM；7 个直接调用、18 个总影响，未关联执行流程 | 文章、网站分享、经历等页面引用；只调整测试解析时不改变 SEO 行为 |
| 网站分享管理端 `save` | UNKNOWN；图未解析模板事件调用 | 页面保存按钮通过 `@click="save"` 调用；验证草稿、公开与失败提示 |
| 后端 `WebsiteShareServiceImpl.saveShare` | UNKNOWN；当前索引未找到该新近符号 | `WebsiteShareController` 新增和更新方法均调用，`WebsiteShareServiceTest` 覆盖；验证拒绝发布时不写入 |
| 主链 `SecurityConfiguration.filterChain` | UNKNOWN；图无静态调用方 | 通过 Spring `@Bean` 在运行时装配，影响主链所有路由；需做匿名、登录用户、管理员和专用链回归 |
| 通用路由脚本 | 顶层脚本无可独立分析的函数符号 | 前后台 `package.json` 的 `check:routes` 均调用，需验证无关改动和真实违规两种情况 |

未获得可用于放宽检查的 HIGH/CRITICAL 结果；主安全链和网站分享保存的 UNKNOWN 仍按高影响面安排回归测试。相关页面、控制器及脚本的文本搜索已确认真实引用。
