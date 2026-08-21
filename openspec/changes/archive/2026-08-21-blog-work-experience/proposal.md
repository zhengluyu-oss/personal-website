## Why

个人求职站顶栏「归档」（分类/标签/时间轴）对展示履历帮助有限。需要把入口换成「工作经历」，并提供可后台维护的结构化经历与美观时间线页，方便 HR 快速扫读。

## What Changes

- 前台顶栏：去掉「归档」下拉；新增一级「工作经历」进入独立页 `/experience`。
- 分类、标签（及可选时间轴）挪到「其他」下拉，避免丢失入口。
- 新增结构化工作经历数据模型与公开列表 API、后台 CRUD（增删改、排序、启停）。
- 前台时间线页展示启用中的经历（公司、岗位、时间、要点）。
- 管理端「网站管理」下增加「工作经历」菜单与列表/表单页。
- 不改 Java 包名与 `kuailemao-*` 目录名。

## Capabilities

### New Capabilities

- `work-experience`: 工作经历的数据、后台配置与前台时间线展示

### Modified Capabilities

- `blog-header-layout`: 顶栏导航结构从「归档」下拉改为「工作经历」一级入口，并把分类/标签迁入「其他」

## Impact

- 后端：新表/实体/Controller/Service；`sys_menu`/`sys_permission` 增量 SQL
- 管理端：新 API 与页面
- 前台：路由、Experience 页、Header Menu / MoveMenu
- 部署：执行 SQL，构建并发布 backend + blog + admin
