# 主安全链接口盘点

依据 `xyz.luyu.controller` 控制器映射、`SecurityConst.AUTH_CHECK_ARRAY` 和前后台 API 调用整理。以下清单按 HTTP 方法区分；`/diary/**`、`/knowledge/**` 由更高优先级专用安全链处理。部署时 Nginx 将 `/api/` 前缀去掉后转给后端。

## 明确允许匿名的接口

| 方法 | 路径 |
| --- | --- |
| GET | `/article/search/init/title`, `/article/search/by/content`, `/article/hot`, `/article/list`, `/article/blog-feed`, `/article/recommend`, `/article/random`, `/article/detail/{id}`, `/article/related/{categoryId}/{articleId}`, `/article/where/list/{typeId}`, `/article/visit/{id}` |
| GET | `/banners/list`, `/category/list`, `/comment/getComment`, `/experience-overview`, `/experience/{experienceId}/projects`, `/experience/{experienceId}/projects/{projectId}` |
| GET | `/favorite/whether/favorite`, `/like/whether/like`, `/leaveWord/list`, `/link/list`, `/link/email/apply`, `/photo/list` |
| GET | `/oauth/gitee/render`, `/oauth/gitee/callback`, `/oauth/github/render`, `/oauth/github/callback`, `/public/ask-code`, `/public/email-delivery` |
| GET | `/site-modules/access`, `/site-modules/about`, `/tag/list`, `/treeHole/getTreeHoleList`, `/websiteInfo/front` |
| GET | `/website-share/list`, `/website-share/{id}`, `/website-share/from-article/{articleId}`, `/experience/list`, `/experience/{id}` |
| POST | `/oauth/exchange`, `/user/admin-login/verify`, `/user/admin-login/resend`, `/user/register`, `/user/reset-confirm`, `/user/reset-password`, `/user/login` |
| GET、POST | `/user/logout`（两个前端分别使用 GET、POST，均由 Spring Security 退出处理） |

`/favorite/whether/favorite` 和 `/like/whether/like` 在现有过滤链中无需登录即可进入业务层；保留这一入口兼容性，业务返回值仍由服务决定。OAuth 回调和旧文章迁移查询继续列入公开清单，以保留现有跳转。

## 需要登录或业务权限的接口

- 原先由 `SecurityConst.AUTH_CHECK_ARRAY` 宽泛匹配的接口，现按方法列出已有的普通用户操作：`GET /user/auth/info`、`GET /menu/router/list/{typeId}`；`POST /user/auth/update`、`/user/auth/upload/avatar`、`/user/auth/update/email`、`/user/auth/third/update/email`、`/comment/auth/add/comment`、`/favorite/auth/favorite`、`/like/auth/like`、`/leaveWord/auth/userLeaveWord`、`/link/auth/apply`、`/treeHole/auth/addTreeHole`；`DELETE /favorite/auth/favorite`、`/like/auth/like`。同一前缀下新增的未知路径不会自动放行。
- 其余主链中的管理方法均由 `@PreAuthorize` 声明业务权限，例如文章、网站分享、用户角色、站点信息和系统日志。过滤链需要允许这些**已声明权限的映射**进入方法级校验，并拒绝无公开声明、无登录规则、无方法级授权的新映射。
- `/oauth/reauth/start` 有方法级认证要求；`/website-share/back/**` 是管理方法，公开列表和详情不包含 `/back/`。

前台 `luyu-blog/src/apis` 实际调用公开文章、分类、标签、经历、网站分享、站点信息，以及用户登录注册/OAuth；管理端 `luyu-admin/src/api` 实际调用登录、验证码和带业务权限的管理接口。上线前用匿名、普通用户和管理员请求复核这些接口与 Nginx `/api` 前缀。
