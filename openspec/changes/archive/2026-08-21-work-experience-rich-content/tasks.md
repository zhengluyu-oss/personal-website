## 1. Database

- [x] 1.1 新增 `sql/work-experience-content.sql`：`ALTER TABLE t_work_experience ADD content MEDIUMTEXT NULL`（及必要注释）
- [x] 1.2 在目标库执行该 SQL 并确认列存在

## 2. Backend

- [x] 2.1 Entity / DTO / VO 增加 `content` 字段，后台 add/update 可写入
- [x] 2.2 公开 `GET /experience/list` 响应裁剪：不返回完整 `content`
- [x] 2.3 新增公开 `GET /experience/{id}`：仅启用记录返回含完整 `content`；否则失败/不可见
- [x] 2.4 确认后台查询/详情仍可读完整 `content` 供编辑回填

## 3. Admin

- [x] 3.1 经历表单增加 MdEditor（对齐发文页编辑器用法）绑定 `content`
- [x] 3.2 编辑器图片上传复用文章图片上传 API，插入 Markdown 图片语法
- [x] 3.3 列表/编辑回填与保存联调通过

## 4. Blog frontend

- [x] 4.1 增加 `getExperience(id)` API；列表类型可不依赖完整 `content`
- [x] 4.2 新增路由 `/experience/:id` 与 `Detail` 页，用 MdPreview 渲染正文
- [x] 4.3 `/experience` 时间线卡片可导航到详情；`content` 空时详情友好空状态
- [x] 4.4 空列表状态与停用条目不可达行为保持正确

## 5. Deploy & verify

- [x] 5.1 构建并部署 backend、admin、blog
- [x] 5.2 后台为一条启用经历写入含图 Markdown，验收列表→详情图文
- [x] 5.3 验证停用条目公开 list/detail 均不可见
