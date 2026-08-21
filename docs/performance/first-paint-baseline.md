# 首屏性能基线（2026-08-21）

测试目标：`http://82.156.90.186/`，冷缓存访问，服务器公网带宽 3Mbps。

| 资源/接口 | 状态 | 大小/耗时 | 结论 |
| --- | --- | --- | --- |
| 首页 HTML | 200 | 首字节快速返回 | 入口 HTML 不是瓶颈 |
| `index-ebbe2bd7.js` | 200 | 约 163KB / 0.13s | 应用入口正常 |
| `js/.pnpm-c434dab9.js` | 200 | 2,552,284B / 6.4–7.5s | 无 `Content-Encoding`，首屏主瓶颈 |
| `css/.pnpm-8b43dc3e.css` | 200 | 约 419KB / 1.35s | 全量组件样式进入首屏 |
| Banner 图片 | 200 | 约 257KB / 0.19s | 不是主要瓶颈 |
| `/api/websiteInfo/front` | 200 | 约 36ms | 数据库/API 正常 |
| `/api/banners/list` | 200 | 约 35ms | 数据库/API 正常 |
| `/api/article/list` | 200 | 约 53ms | 数据库/API 正常 |
| `/api/article/recommend` | 200 | 约 30ms | 数据库/API 正常 |

`curl --compressed` 返回的关键脚本仍为 2,552,284B，证明线上未实际启用 gzip。理论 gzip 后 vendor 约 879KB，仅开启压缩仍不足以满足预算，因此需要同时拆分首页依赖。

## 2026-08-22 部署后复测

生产入口已降为约 130KB gzip JavaScript 与 10KB gzip CSS；关键脚本响应包含 `Content-Encoding: gzip`、`Vary: Accept-Encoding` 与一年期 `immutable` 缓存。Banner 仍由独立 OSS URL 加载，实际约 257KB，未做 base64 内联且没有重复请求；OSS 当前未返回长期 `Cache-Control`，后续应在对象存储侧补充元数据。

Lighthouse 使用 390×844 移动视口、冷缓存、150ms RTT、3Mbps 下行、1Mbps 上行复测。最终模拟结果为 FCP 2.769s、LCP 6.515s、CLS 0；资源预算、压缩、稳定性均通过，但 FCP ≤2.5s、LCP ≤4s 的性能硬门槛尚未通过。诊断中线上普通请求约 30–60ms，而 Lighthouse 模拟 TTFB 曾推演至约 3s；最终 LCP 元素为首页主标题，不是数据库接口或 Banner 图片。
