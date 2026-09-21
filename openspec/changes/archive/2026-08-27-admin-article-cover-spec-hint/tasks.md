## 1. 规格常量

- [x] 1.1 在 `kuailemao-admin` 中新增 `article-cover-spec.ts`（或等价常量模块），集中定义比例 16:10、推荐尺寸 1600×1000、备选尺寸、格式与压缩上限文案
- [x] 1.2 导出供发布页引用的 `COVER_SPEC_EXTRA`（或 label + extra 组合）字符串，避免魔法字符串散落

## 2. 发布页 UI

- [x] 2.1 在 `pages/blog/essay/publish/index.vue` 封面上传区域外包 `a-form-item`，设置 `label="文章封面"`
- [x] 2.2 使用 `extra` 展示规格说明：16:10、1600×1000 px、备选尺寸、JPG/PNG/WebP、≤0.3MB，并说明列表以 16:10 展示以避免 letterbox
- [x] 2.3 确认新建与编辑（带 `id` 回显封面）两种场景下规格文案均可见，上传/预览/发布逻辑未改动

## 3. 验证与发布

- [x] 3.1 本地打开 admin 发布页与编辑页，目视确认 label 与 extra 文案完整、样式与表单项密度协调
- [x] 3.2 上传合法封面验证现有压缩与提交流程行为不变
- [x] 3.3 构建并部署 `kuailemao-admin`（或全量 deploy）至生产环境
