# 实施验证

- `pnpm --dir blog-frontend/luyu-blog build:app`：通过。构建输出包含现有的 Sass 弃用和大包体积提示。
- 本地构建预览 `/tree-hole`：在 320、375、768、1440px 视口截图核对。标题、输入文字、占位文字和提交按钮可读；320px 与 375px 输入框独占一行，提交按钮在下方；焦点轮廓可见。
- 四种视口的 `document.documentElement.scrollWidth` 分别为 320、375、768、1440px，与视口宽度一致。聚焦输入框后按钮均在视口内。
- 本地页面未请求旧的 `TreeHole-back_compressed.webp` 对象。线上尚未发布本次修改，因此线上页面仍需在部署后核对。
- 本地预览未连接后端，页面出现接口错误提示；本次核对的是树洞布局、对比度和资源请求，未提交留言。
- GitNexus `detect_changes --scope all` 返回 `risk_level: low`、受影响流程 0，未标记 `partial` 或 `truncated`。当前工作区另有与本变更无关的未提交文件；本次不创建 Git 提交。
