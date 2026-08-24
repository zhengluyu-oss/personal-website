# 页面规范路由迁移

本次迁移仅调整两个 Vue 应用的页面地址和 `sys_menu` 页面路由字段。`blog-backend`、前台 `src/apis`、后台 `src/api` 的接口地址是冻结基线，不属于修改范围。

前台旧地址按以下关系被直接移除（不创建跳转）：`/category` → `/blog`，`/category/:id` → `/blog/categories/:id`，`/article/:id` → `/blog/articles/:id`，`/tags` → `/blog/tags`，`/timeline` → `/blog/archive`，`/message` → `/messages`，`/link` → `/links`，`/photo` → `/photos`，`/welcome`/`/login` → `/auth/login`，`/setting` → `/account`。

后台在 `/admin` 部署前缀下使用浅层资源路径。菜单仍分为“网站管理”和“系统管理”，但文章、分类、用户等资源不会为了视觉分组增加 URL 层级。数据库迁移使用 `sql/normalize-page-routes.sql`，首次执行会保存 `sys_menu_route_backup_20260824`；需要回滚时使用 `sql/rollback-page-routes.sql`。

上线顺序：备份菜单表、执行菜单迁移、刷新登录态/菜单缓存、发布后台资源、发布前台资源、检查规范路径与旧路径 404。
