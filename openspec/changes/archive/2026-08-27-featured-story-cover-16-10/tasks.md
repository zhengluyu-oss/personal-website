## 1. 推荐位封面比例

- [x] 1.1 在 `Category/index.vue` 为 `.featured-story__cover` 设置 `aspect-ratio: 16 / 10`，移除或置零 `min-height: 20rem` 以避免与比例冲突
- [x] 1.2 删除移动端 `@media (max-width: 900px)` 中 `.featured-story__cover` 的 `aspect-ratio: 16 / 8`，确保全断点均为 16:10

## 2. 布局微调

- [x] 2.1 视需要调整 `.featured-story` 的 `grid-template-columns`（如接近 1:1），使 16:10 封面与右侧文案在桌面端协调
- [x] 2.2 确认 ≤900px 单列堆叠下封面全宽 16:10 与文案区间距正常

## 3. 验证与发布

- [x] 3.1 使用 16:10 测试封面在推荐位无左右留白，列表卡片行为未变
- [x] 3.2 在桌面 / 平板 / 手机宽度目视验收推荐位整体版式
- [x] 3.3 构建并部署 `kuailemao-blog` 至生产环境
