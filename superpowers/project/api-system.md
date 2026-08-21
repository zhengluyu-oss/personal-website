# Ruyu-Blog 接口清单

> 扫描全部 `@RestController` 与 Spring Security 表单端点。路径为应用内路径（前台开发需加 `/api` 前缀）。  
> 统计：Controller 方法 **152** + Security 登录/登出 **2** = **154**。`UploadController` 无方法，不计入。  
> 分析日期：2026-08-18。未直连运行时 Swagger。

## 1. 统一响应格式

来源：`xyz.kuailemao.domain.response.ResponseResult`。

```json
{ "code": 200, "msg": "success", "data": {} }
```

`@JsonInclude(NON_NULL)`：data 为 null 时可能省略。登录成功会自定义 msg（`RespConst.SUCCESS_LOGIN_MSG`）。

## 2. 全局状态码（`RespEnum.java`）

| code | 常量 | msg |
|---|---|---|
| 200 | SUCCESS | success |
| 500 | FAILURE | failure |
| 1001 | USERNAME_OR_PASSWORD_ERROR | 用户名或密码错误 |
| 1002 | NOT_LOGIN | 请先登录 |
| 1003 | NO_PERMISSION | 没有权限 |
| 1004 | REQUEST_FREQUENTLY | 请求频繁 |
| 1005 | VERIFY_CODE_ERROR | 验证码错误 |
| 1006 | USERNAME_OR_EMAIL_EXIST | 用户名或邮箱已存在 |
| 1007 | PARAM_ERROR | 参数错误 |
| 1008 | OTHER_ERROR | 其他故障 |
| 1009 | SESSION_LIMIT | 会话数量已达上限 |
| 1010 | NO_DELETE_CHILD_MENU | 请先删除子菜单 |
| 1011 | FILE_UPLOAD_ERROR | 文件上传错误 |
| 1012 | BLACK_LIST_ERROR | 账号被封禁 |

前台对 **1012** 特殊提示；后台对 **1002** 清 token 跳登录。

## 3. 全局异常处理

`GlobalExceptionControllerHandler`（`@RestControllerAdvice`）：

- `ConstraintViolationException` / `MethodArgumentNotValidException` → 参数错误
- `FileUploadException` → 1011
- `BlackListException` → 1012
- 通用 `Exception` **已注释**，未捕获异常不一定走统一 JSON

`SecurityHandler`：未认证 / 拒绝访问写 JSON（1002 / 1003）。

## 4. 鉴权说明

| 机制 | 规则 |
|---|---|
| Header | `Authorization: Bearer <jwt>` |
| 客户端 | `X-Client-Type: Frontend \| Backend` |
| OAuth 二次登录 | `Login-Type` + `Access-Token` |
| 过滤器必登 | `SecurityConst.AUTH_CHECK_ARRAY`（见 architecture.md） |
| 方法权限 | `@PreAuthorize("hasAnyAuthority('…')")` |
| 公开 | 列表/详情/OAuth/注册/验证码/Knife4j 等 |

登录/登出由 Spring Security 处理，**无** `@RequestMapping` 方法体。

## 5. 权限矩阵（后台 PreAuthorize 权限字）

按 Controller 注解汇总（种子数据在 `sys_permission`，以代码为准）：

| 权限字 | 接口组 |
|---|---|
| `blog:publish:article` | 发布/封面/插图 |
| `blog:article:list/search/update/echo/delete` | 文章后台 |
| `blog:banner:list/delete/add/update` | 轮播 |
| `blog:black:add/update/select/delete` | 黑名单 |
| `blog:category:*` / `blog:tag:*` | 分类标签 |
| `blog:comment:*` / `blog:favorite:*` / `blog:leaveword:*` / `blog:treeHole:*` / `blog:link:*` | 互动审核 |
| `blog:photo:*` / `blog:album:*` | 相册（种子需手工补） |
| `blog:get:websiteInfo` / `blog:update:websiteInfo` | 站点 |
| `system:user:*` / `system:role:*` / `system:menu:*` / `system:permission:*` | RBAC |
| `system:log:*` / `system:log:login:*` | 日志 |
| `monitor:server:list` | 监控 |

前台写接口多数 **无权限字、只要求登录**。

## 6. 代理映射

| 环境 | 前缀 | 目标 |
|---|---|---|
| 前台 Vite | `/api` rewrite 去掉 | `VITE_SERVE` `http://localhost:8088` |
| 前台 Vite | `/wapi` rewrite 去掉 | `VITE_MUSIC_SERVE`（音乐，非本 API） |
| 前台 nginx | `/api/` | `http://kuailemao.xyz:8088/` |
| 后台 Vite | `[VITE_APP_BASE_API]` | `VITE_APP_BASE_URL`（仓库为空） |
| 后台 nginx | `/api/` | `http://kuailemao.xyz:8088/` |

---

## 7. 接口明细

调用链通式：`Controller.method` → 同名 `ServiceImpl` → `Mapper` / Redis / MinIO。下列「调用链」只写入口方法。

### 7.1 Security 表单（非 Controller）

| 方法 | 路径 | 参数 | 响应 | 调用链 |
|---|---|---|---|---|
| POST | `/user/login` | form username, password；头 X-Client-Type；可选 Login-Type, Access-Token | `ResponseResult<AuthorizeVO>` | `SecurityHandler.onAuthenticationSuccess` |
| POST | `/user/logout` | JWT | 成功 JSON | `SecurityHandler.onLogoutSuccess` + 删白名单 |

### 7.2 PublicController `/public`

| 方法 | 路径 | 权限 | 说明 | 调用链 |
|---|---|---|---|---|
| GET | `/public/ask-code` | 公开 | query email, type | `PublicService.askVerifyCode` |

### 7.3 UserController `/user`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/user/auth/info` | 登录 AUTH | 当前用户 |
| POST | `/user/auth/update` | 登录 | 改资料 |
| POST | `/user/auth/upload/avatar` | 登录 | 头像 MinIO |
| POST | `/user/auth/update/email` | 登录 | 改邮箱 |
| POST | `/user/auth/third/update/email` | 登录 | 第三方绑邮箱 |
| POST | `/user/register` | 公开 | 注册 |
| POST | `/user/reset-confirm` | 公开 | 重置确认 |
| POST | `/user/reset-password` | 公开 | 重置密码 |
| GET | `/user/list` | `system:user:list` | 用户分页 |
| POST | `/user/search` | `system:user:search` | 搜索 |
| POST | `/user/update/status` | `system:user:status:update` | 启用/禁用 |
| GET | `/user/details/{id}` | `system:user:details` | 详情 |
| DELETE | `/user/delete` | `system:user:delete` | 删除 |

调用链：`UserController` → `UserServiceImpl` → `UserMapper`。

### 7.4 OauthController `/oauth`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/oauth/gitee/render` | 公开 | 跳转授权 |
| GET | `/oauth/gitee/callback` | 公开 | 回调 |
| GET | `/oauth/github/render` | 公开 | |
| GET | `/oauth/github/callback` | 公开 | |

→ `OauthServiceImpl`（JustAuth）。响应为 **重定向**，非 JSON。

### 7.5 ArticleController `/article`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/article/search/init/title` | 公开 |
| GET | `/article/search/by/content` | 公开 |
| GET | `/article/hot` | 公开 |
| GET | `/article/list` | 公开 |
| GET | `/article/recommend` | 公开 |
| GET | `/article/random` | 公开 |
| GET | `/article/detail/{id}` | 公开 |
| GET | `/article/related/{categoryId}/{articleId}` | 公开 |
| GET | `/article/timeLine` | 公开 |
| GET | `/article/where/list/{typeId}` | 公开 |
| GET | `/article/visit/{id}` | 公开 |
| POST | `/article/upload/articleCover` | `blog:publish:article` |
| POST | `/article/publish` | `blog:publish:article` |
| GET | `/article/delete/articleCover` | `blog:publish:article` |
| POST | `/article/upload/articleImage` | `blog:publish:article` |
| GET | `/article/back/list` | `blog:article:list` |
| POST | `/article/back/search` | `blog:article:search` |
| POST | `/article/back/update/status` | `blog:article:update` |
| POST | `/article/back/update/isTop` | `blog:article:update` |
| GET | `/article/back/echo/{id}` | `blog:article:echo` |
| DELETE | `/article/back/delete` | `blog:article:delete` |

→ `ArticleServiceImpl`。发布体为文章 DTO（标题/内容/分类/标签/封面/状态）。

### 7.6 CategoryController `/category`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/category/list` | 公开 |
| PUT | `/category/` | `blog:category:add` |
| GET | `/category/back/list` | `blog:category:list` |
| POST | `/category/back/search` | `blog:category:search` |
| GET | `/category/back/get/{id}` | `blog:category:search` |
| PUT | `/category/back/add` | `blog:category:add` |
| POST | `/category/back/update` | `blog:category:update` |
| DELETE | `/category/back/delete` | `blog:category:delete` |

### 7.7 TagController `/tag`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/tag/list` | 公开 |
| PUT | `/tag/` | `blog:tag:add` |
| GET | `/tag/back/list` | `blog:tag:list` |
| POST | `/tag/back/search` | `blog:tag:search` |
| GET | `/tag/back/get/{id}` | `blog:tag:search` |
| PUT | `/tag/back/add` | `blog:tag:add` |
| POST | `/tag/back/update` | `blog:tag:update` |
| DELETE | `/tag/back/delete` | `blog:tag:delete` |

### 7.8 CommentController `/comment`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/comment/getComment` | 公开 |
| POST | `/comment/auth/add/comment` | 登录 |
| GET | `/comment/back/list` | `blog:comment:list` |
| POST | `/comment/back/search` | `blog:comment:search` |
| POST | `/comment/back/isCheck` | `blog:comment:isCheck` |
| DELETE | `/comment/back/delete/{id}` | `blog:comment:delete` |

### 7.9 LikeController `/like`

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/like/auth/like` | 登录 |
| DELETE | `/like/auth/like` | 登录 |
| GET | `/like/whether/like` | 公开（可带 token 判断） |

注意路径是 `whether/like`（无前导斜杠拼接后仍为 `/like/whether/like`）。

### 7.10 FavoriteController `/favorite`

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/favorite/auth/favorite` | 登录 |
| DELETE | `/favorite/auth/favorite` | 登录 |
| GET | `/favorite/whether/favorite` | 公开 |
| GET | `/favorite/back/list` | `blog:favorite:list` |
| POST | `/favorite/back/search` | `blog:favorite:search` |
| POST | `/favorite/back/isCheck` | `blog:favorite:isCheck` |
| DELETE | `/favorite/back/delete` | `blog:favorite:delete` |

### 7.11 LeaveWordController `leaveWord`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/leaveWord/list` | 公开 |
| POST | `/leaveWord/auth/userLeaveWord` | 登录 |
| GET | `/leaveWord/back/list` | `blog:leaveword:list` |
| POST | `/leaveWord/back/search` | `blog:leaveword:search` |
| POST | `/leaveWord/back/isCheck` | `blog:leaveword:isCheck` |
| DELETE | `/leaveWord/back/delete` | `blog:leaveword:delete` |

### 7.12 TreeHoleController `/treeHole`

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/treeHole/auth/addTreeHole` | 登录 |
| GET | `/treeHole/getTreeHoleList` | 公开 |
| GET | `/treeHole/back/list` | `blog:treeHole:list` |
| POST | `/treeHole/back/search` | `blog:treeHole:search` |
| POST | `/treeHole/back/isCheck` | `blog:treeHole:isCheck` |
| DELETE | `/treeHole/back/delete` | `blog:treeHole:delete` |

### 7.13 LinkController `link`

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/link/auth/apply` | 登录 |
| GET | `/link/list` | 公开 |
| GET | `/link/back/list` | `blog:link:list` |
| POST | `/link/back/search` | `blog:link:search` |
| POST | `/link/back/isCheck` | `blog:link:isCheck` |
| DELETE | `/link/back/delete` | `blog:link:delete` |
| GET | `/link/email/apply` | 公开（邮件 token） |

### 7.14 BannersController `banners`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/banners/list` | 公开 |
| GET | `/banners/back/list` | `blog:banner:list` |
| DELETE | `/banners/{id}` | `blog:banner:delete` |
| POST | `/banners/upload/banner` | `blog:banner:add` |
| PUT | `/banners/update/sort/order` | `blog:banner:update` |

### 7.15 PhotoController `photo`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/photo/list` | 公开 |
| GET | `/photo/back/list` | `blog:photo:list` |
| POST | `/photo/album/create` | `blog:album:create` |
| POST | `/photo/upload` | `blog:photo:upload` |
| POST | `/photo/album/update` | `blog:album:update` |
| DELETE | `/photo/delete` | `blog:photo:delete` |

### 7.16 WebsiteInfoController `websiteInfo`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/websiteInfo/front` | 公开 |
| GET | `/websiteInfo` | `blog:get:websiteInfo` |
| POST | `/websiteInfo/stationmaster` | `blog:update:websiteInfo` |
| POST | `/websiteInfo/webInfo` | `blog:update:websiteInfo` |
| POST | `/websiteInfo/upload/avatar` | `blog:update:websiteInfo` |
| POST | `/websiteInfo/upload/background` | `blog:update:websiteInfo` |

后台前端有两处 POST 路径 **缺少前导 `/`**（`api/blog/webInfo/index.ts`），依赖 axios baseURL 拼接，生产需确认最终 URL。

### 7.17 BlackListController `blackList`

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/blackList/add` | `blog:black:add` |
| PUT | `/blackList/update` | `blog:black:update` |
| POST | `/blackList/getBlackListing` | `blog:black:select` |
| DELETE | `/blackList/delete` | `blog:black:delete` |

### 7.18 MenuController `menu`（整棵 `/menu/**` 需登录）

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/menu/list/{typeId}` | `system:menu:list` |
| GET | `/menu/search/list/{typeId}` | `system:search:menu:list` |
| GET | `/menu/role/list` | `system:menu:role:list` |
| GET | `/menu/router/list/{typeId}` | 仅登录（动态路由） |
| POST | `/menu` | `system:menu:add` |
| GET | `/menu/{id}` | `system:menu:select` |
| PUT | `/menu` | `system:menu:update` |
| DELETE | `/menu/{id}` | `system:menu:delete` |

### 7.19 RoleController `role`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/role/list` | `system:role:list` |
| POST | `/role/update/status` | `system:role:status:update` |
| GET | `/role/get/{id}` | `system:role:get` |
| PUT | `/role/update` | `system:role:update` |
| PUT | `/role/add` | `system:role:add` |
| DELETE | `/role/delete` | `system:role:delete` |
| POST | `/role/search` | `system:role:search` |

### 7.20 PermissionController `permission`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/permission/list` | `system:permission:list` |
| GET | `/permission/search` | `system:permission:search` |
| GET | `/permission/menu` | `system:permission:menu:list` |
| POST | `/permission/add` | `system:permission:add` |
| POST | `/permission/update` | `system:permission:update` |
| GET | `/permission/get/{id}` | `system:permission:get` |
| DELETE | `/permission/delete/{id}` | `system:permission:delete` |

### 7.21 RolePermissionController `role/permission`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/role/permission/role/list` | `system:permission:role:list` |
| GET | `/role/permission/not/role/list` | `system:permission:role:not:list` |
| POST | `/role/permission/add` | `system:permission:role:add` |
| DELETE | `/role/permission/delete` | `system:permission:role:delete` |

### 7.22 UserRoleController `user/role`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/user/role/user/list` | `system:user:role:list` |
| GET | `/user/role/not/user/list` | `system:not:role:user:list` |
| POST | `/user/role/add` | `system:user:role:add` |
| DELETE | `/user/role/delete` | `system:user:role:delete` |
| GET | `/user/role/role/list` | `system:role:user:list` |
| GET | `/user/role/not/role/list` | `system:user:role:not:list` |
| POST | `/user/role/user/add` | `system:user:role:add` |
| DELETE | `/user/role/user/delete` | `system:user:role:delete` |

### 7.23 LogController `log`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/log/list/{current}/{pageSize}` | `system:log:list` |
| POST | `/log/search` | `system:log:search` |
| DELETE | `/log/delete` | `system:log:delete` |

### 7.24 LoginLogController `loginLog`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/loginLog/list` | `system:log:login:list` |
| POST | `/loginLog/search` | `system:log:login:search` |
| DELETE | `/loginLog/delete` | `system:log:login:delete` |

注意后台登录 API 写的是 `GET /user/logout`，与 Security `POST /user/logout` 可能不一致（`api/common/login.ts`）。**以 Spring Security POST 为准。**

### 7.25 ServerController `/monitor/server`

| 方法 | 路径 | 权限 |
|---|---|---|
| GET | `/monitor/server/` | `monitor:server:list` |

### 7.26 UploadController `/upload`

无方法。上传分散在 Article / Banners / Photo / User / WebsiteInfo。

### 7.27 声明但未实现

| 项 | 说明 |
|---|---|
| `/chatGpt/auth/**` | `SecurityConst` 列入鉴权；无 Controller |
| `ChatGptMapper` | 无对外 HTTP |

### 7.28 前台非后端接口（音乐）

`kuailemao-blog/src/apis/music/index.ts`，前缀 `/wapi`：

- GET `/wapi/toplist/detail`
- GET `/wapi/playlist/track/all`
- GET `/wapi/song/url/v1`
- GET `/wapi//song/detail`（源码双斜杠）
- GET `/wapi/search/suggest`
- GET `/wapi/artist/top/song`
- GET `/wapi/lyric`

不计入 Java 接口总数。

---

## 8. 数量核对

| 来源 | 方法数 |
|---|---|
| Security login/logout | 2 |
| Public | 1 |
| User | 13 |
| Oauth | 4 |
| Article | 21 |
| Category | 8 |
| Tag | 8 |
| Comment | 6 |
| Like | 3 |
| Favorite | 7 |
| LeaveWord | 6 |
| TreeHole | 6 |
| Link | 7 |
| Banners | 5 |
| Photo | 6 |
| WebsiteInfo | 6 |
| BlackList | 4 |
| Menu | 8 |
| Role | 7 |
| Permission | 7 |
| RolePermission | 4 |
| UserRole | 8 |
| Log | 3 |
| LoginLog | 3 |
| Server | 1 |
| **合计** | **154** |

与 `controller` 目录 25 个类扫描结果一致（含空 Upload）。
