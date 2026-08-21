# Ruyu-Blog 功能模块与文件对照

> 按功能子系统划分。来源均为仓库代码。分析日期：2026-08-18。

## 1. 用户与认证

**职责边界：** 注册/登录/登出/重置密码、个人信息、头像、改邮箱、第三方 OAuth。不含 RBAC 授权配置（见模块 2）。

**数据：** `sys_user`；Redis `jwt:white:list:`、`verifyCode:`。

**入口：**

| 端 | 文件 |
|---|---|
| 后端 | `controller/UserController.java`、`controller/OauthController.java`、`controller/PublicController.java` |
| 安全 | `config/SecurityConfiguration.java`、`handler/SecurityHandler.java`、`filter/JwtAuthorizeFilter.java` |
| 前台 | `views/Welcome/{Login,Register,Reset}`、`apis/user/index.ts`、`apis/email/index.ts`、`apis/thirdParty` |
| 后台 | `pages/common/login.vue`、`api/common/login.ts`、`api/common/user.ts` |

**核心类：**

| 类/函数 | 作用 |
|---|---|
| `UserServiceImpl`（兼 `UserDetailsService`） | `loadUserByUsername`、注册、重置、资料更新、登录后状态 |
| `OauthServiceImpl` | Gitee/GitHub 授权码 → 用户 upsert → 重定向前台带 `access_token` |
| `JwtUtils` | 签发/解析/白名单 |
| `PublicServiceImpl.askVerifyCode` | 发验证码入 MQ |

**依赖：** 邮件队列、JustAuth、MinIO（头像）、`UserRoleService`（默认角色）。

**风险：** `DEFAULT_ROLE = 2` 与种子「Test / USER」错位（见 architecture.md）。OAuth 第二步仍走 `/user/login` + `Login-Type` + `Access-Token`。

---

## 2. RBAC（用户 / 角色 / 菜单 / 权限）

**职责边界：** 后台对用户列表、角色、菜单树、权限字、三者授权关系的 CRUD；登录后下发路由。

**数据：** `sys_role`、`sys_user_role`、`sys_menu`、`sys_role_menu`、`sys_permission`、`sys_role_permission`。

**入口：**

| 控制器 | 前缀 |
|---|---|
| `UserController` 管理接口 | `/user/list` 等 |
| `UserRoleController` | `user/role` |
| `RoleController` | `role` |
| `MenuController` | `menu` |
| `PermissionController` | `permission` |
| `RolePermissionController` | `role/permission` |

后台页面：`pages/system/{user,role,menu,permission}` 及隐藏授权页 `user-role`、`role-user`、`role-permission`。

**核心：** `MenuServiceImpl.getMenuList(typeId==0)` 返回当前用户菜单（角色拥有 **或** 未绑定角色的菜单）。`kuailemao-admin/src/stores/user.ts` `generateDynamicRoutes` → `generateTreeRoutes` / `generateFlatRoutes`。组件解析 `router/router-modules.ts`：`import.meta.glob('~/pages/**/*.vue')`。

**风险：** 未绑定角色的菜单对所有已登录用户可见。改菜单后需重新登录。`sys_user_role.role_id` 为 varchar。

---

## 3. 文章 / 分类 / 标签

**职责边界：** 前台列表/详情/搜索/时间轴/推荐/随机/相关；后台发布 Markdown、上下架、置顶、封面与插图上传。

**数据：** `t_article`、`t_article_tag`、`t_category`、`t_tag`；Redis 访问/点赞/收藏/评论计数。

**入口：** `ArticleController` `/article`、`CategoryController` `/category`、`TagController` `/tag`。

前台：`views/Home`、`views/Article`、`views/Pigeonhole/*`、`apis/article|category|tag|home`。  
后台：`pages/blog/essay/publish`、`essay/list`、`tag`、`category`。

**核心：** `ArticleServiceImpl` 发布/回显/搜索；`FileUploadUtils` + MinIO；`md-editor-v3`。

**风险：** 访问量回写依赖 Quartz（配置类未启用）。文章 `status`：1 公开 2 私密 3 草稿。

---

## 4. 评论 / 点赞 / 收藏

**职责边界：** 多态互动。`type`：评论 1 文章 / 2 留言板；点赞 1 文章 / 2 评论 / 3 留言板；收藏 1 文章 / 2 留言板。

**数据：** `t_comment`、`t_like`、`t_favorite`。评论/收藏有 `is_check`；`t_like` 无逻辑删除字段。

**入口：** `CommentController`、`LikeController`、`FavoriteController`。写接口均在 `/auth/**`，需登录。

前台组件：`components/Comment/*`。后台审核：`pages/blog/comment`、`pages/blog/collect`。

**风险：** 点赞取消靠物理删行。评论支持 Markdown + 表情（Heo）。

---

## 5. 留言板 / 树洞

**职责：** 留言板带详情与评论嵌套；树洞短文本弹幕风（`vue3-danmaku`）。

**数据：** `t_leave_word`、`t_tree_hole`。

**入口：** `LeaveWordController` `leaveWord`、`TreeHoleController` `/treeHole`。

前台：`views/Amusement/Message/*`、`views/Amusement/TreeHole`。后台：`pages/blog/message`、`tree-hole`。

写操作需登录；后台可审核 `isCheck`。

---

## 6. 友链

**职责：** 用户申请 → 邮件通知站长 → 可通过邮件链接或后台审核 → 通知申请者。

**数据：** `t_link`；Redis `email:verification:link:`。

**入口：** `LinkController`：`POST /link/auth/apply`、`GET /link/list`、`GET /link/email/apply`（邮件回调，无 PreAuthorize）。

配置开关：`mail.apply-notice`、`mail.pass-notice`、`mail.link.apply.redirect-uri`（`application.yml` 模板）。

---

## 7. 站点信息 / Banner / 相册

**站点：** `WebsiteInfoController` `websiteInfo`；表 `sys_website_info`（单行种子 id=1）。前台 `GET /websiteInfo/front`。后台 `pages/blog/info`。

**Banner：** `BannersController` `banners`；表 `t_banners`（无逻辑删除）。前台首页轮播。

**相册：** `PhotoController` `photo`；表 `t_photo`（type 1 相册 / 2 照片，`parent_id` 邻接表）。前台 `views/Photo`；后台 `pages/blog/photo` **种子菜单未包含**，需 v1.6.0 手工配置。

---

## 8. 黑名单与限流

**职责：** IP/用户封禁；接口 `@AccessLimit` 计数，超额按 `BlackListPolicy` 写入黑名单。

**数据：** `t_black_list`；Redis `blackList:uid:` / `blackList:ip:`、`limit:*`。

**入口：** `BlackListController` `blackList`；拦截器 `AccessLimitInterceptor`（全局 `/**`，仅注解方法限流）。

文档：`blog-backend/src/test/java/xyz/kuailemao/黑名单功能文档.md`。前台 axios 对 code `1012` 提示封禁。

**风险：** `@CheckBlacklist` 未实现。

---

## 9. 日志与服务监控

**操作日志：** `@LogAnnotation` → `LogAspect` → MQ `log_system_queue` → `sys_log`。`LogController` `log`。

**登录日志：** 登录 Handler → `log_login_queue` → `sys_login_log`（含前后台 type）。`LoginLogController` `loginLog`。

**监控：** `ServerController` `/monitor/server`，OSHI 采集。页面 `pages/system/server-monitoring`。

---

## 10. 前台体验模块（无独立后端域）

| 功能 | 实现位置 | 后端 |
|---|---|---|
| 音乐播放器 | `components/Music/*`、`store/modules/music.ts`、`views/Music` iframe | 第三方 `/wapi`，非本 Java 服务 |
| 暗色模式 | `App.vue` `useDark`、`DayNightToggle` | 无 |
| 搜索 | `components/Search` + `/article/search/*` | 有 |
| 每日一言 | `apis/thirdParty` | 外部 hitokoto |
| 电子时钟 / 粒子 / 鼠标拖尾 | 对应 components | 无 |

---

## 11. 基础设施胶水（非业务模块）

| 组件 | 文件 |
|---|---|
| MinIO 客户端 | `config/MinioConfig.java` |
| Redis 序列化 | `config/RedisConfig.java` |
| Knife4j | `config/Knife4jConfig.java` |
| MP 分页 | `config/MybatisPlusConfig.java` |
| CORS | `filter/CorsFilter.java` |
| 启动缓存 | `tasks/AutomaticTasks.java` |
| 空上传控制器 | `controller/UploadController.java` |
| 未完成 ChatGPT | `entity/ChatGpt.java`、`mapper/ChatGptMapper.java` |

---

## 附录：模块—文件对照总表

| 模块 | 后端 Controller | Service | 主要表 | 前台 | 后台 pages |
|---|---|---|---|---|---|
| 认证用户 | User, Oauth, Public | User, Oauth, Public | sys_user | Welcome/*, Setting | common/login, account/* |
| RBAC | User, Role, Menu, Permission, UserRole, RolePermission | 同名 | sys_* | — | system/* |
| 文章 | Article, Category, Tag | Article, Category, Tag, ArticleTag | t_article, t_category, t_tag, t_article_tag | Home, Article, Pigeonhole | blog/essay, tag, category |
| 评论点赞收藏 | Comment, Like, Favorite | 同名 | t_comment, t_like, t_favorite | Comment 组件 | blog/comment, collect |
| 留言树洞 | LeaveWord, TreeHole | 同名 | t_leave_word, t_tree_hole | Amusement/* | blog/message, tree-hole |
| 友链 | Link | Link | t_link | views/Link | blog/link |
| 站点/轮播/相册 | WebsiteInfo, Banners, Photo | 同名 | sys_website_info, t_banners, t_photo | Home Banner, Photo, About | blog/info, photo |
| 黑名单 | BlackList | BlackList | t_black_list | — | blog/black-list |
| 日志监控 | Log, LoginLog, Server | Log, LoginLog | sys_log, sys_login_log | — | system/log, server-monitoring |
| 上传 | Upload（空） | FileUploadUtils | MinIO | — | 各上传表单 |
| ChatGPT | 无 | 无 | 实体映射 t_chat_gpt，**无 DDL** | 无 | 无（旧 sql 曾有菜单） |
