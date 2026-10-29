# 修复前基线

- 2026-10-29，树洞引用的 `https://zhengluyu.oss-cn-beijing.aliyuncs.com/blog/TreeHole/TreeHole-back_compressed.webp` 响应 `404 Not Found`。
- 线上 `/tree-hole` 在 320px 与 1440px 视口均显示浅色空白背景；标题和输入框占位文字为白色，难以辨认。前一轮浏览器检查已分别查看这两种视口的页面截图。
- 320px 输入框计算样式：文字与占位文字均为 `rgb(255, 255, 255)`，背景为 `rgba(255, 255, 255, 0.2)`；页面未发生整页横向溢出。
- GitNexus 对 `blog-frontend/luyu-blog/src/views/Amusement/TreeHole/index.vue` 的 upstream impact 返回 `risk: UNKNOWN`、已解析调用者 0、受影响流程 0。源码搜索确认该页面由 `/tree-hole` 路由懒加载；菜单、页脚存在访问入口。图谱索引较 HEAD 落后两个提交，不能用零调用者推断页面未使用。
