# Ruyu-Blog 项目全景与启动手册

> 信息来源：仓库代码与配置只读分析。未直连数据库。  
> 分析日期：2026-08-18

## 1. 项目基本信息

| 项 | 内容 |
|---|---|
| 名称 | Ruyu-Blog（如鱼博客 / 快乐猫博客） |
| 仓库路径 | `f:\guochuang\my_project\myOwnWebsite\Ruyu-Blog` |
| 一句话描述 | 前后端分离的个人博客系统：前台展示文章/评论/相册/树洞/友链，后台做 RBAC 内容与系统管理 |
| 目标用户 | 个人站长（部署自用）；前台访客；后台管理员 / 测试账号 |
| 上游开源 | Gitee `kuailemao/ruyu-blog`；GitHub `kuailemao/Ruyu-Blog`（`README.md` L71–73） |
| 本克隆 remote | `origin` = `https://github.com/zhengluyu-oss/personal-website.git`，分支 `master` |
| License | 根目录 Apache-2.0（`LICENSE`）；后台脚手架 Antdv Pro 为 MIT（`blog-frontend/kuailemao-admin/LICENSE`） |
| 成熟度 | 可运行的完整开源博客（作者自称独立开发约 3 个月）。线上预览与仓库可能不同步。未完成项见 README：后台导入导出、图片资源管理、数据大屏、App/小程序。本地 **缺少 gitignored 的 `application-dev.yml` / `application-prod.yml`**，后端无法直接启动 |

## 2. 技术栈全景（含版本）

### 后端 `blog-backend/pom.xml`

| 技术 | 版本 |
|---|---|
| JDK | 17 |
| Spring Boot | 3.1.4 |
| Spring Security | 随 Boot 3.1.4 |
| MyBatis | `mybatis-spring-boot-starter` 3.0.2 |
| MyBatis-Plus | 3.5.3.1 |
| MySQL Connector/J | 随 Boot（runtime） |
| Redis | `spring-boot-starter-data-redis` |
| RabbitMQ | `spring-boot-starter-amqp` |
| Quartz | `spring-boot-starter-quartz` + c3p0 0.9.5.4 |
| MinIO | 8.5.7 |
| JWT | Auth0 `java-jwt` 4.3.0 |
| Knife4j OpenAPI3 | 4.3.0 |
| Fastjson | 2.0.25 |
| Hutool | 5.8.16 |
| JustAuth（OAuth） | 1.16.6 |
| OSHI（服务监控） | 6.4.0 |
| OkHttp | 4.10.0-RC1 |
| Lombok | 随 Boot |
| Thymeleaf + Mail | 邮件模板 |

### 前台博客 `blog-frontend/kuailemao-blog/package.json`

| 技术 | 版本 |
|---|---|
| Vue | 3.3.4 |
| Vue Router | 4.2.4 |
| Pinia | 2.1.6 |
| Vite | 4.4.5 |
| TypeScript | 5.0.2 |
| Axios | 1.5.1 |
| Element Plus | 2.8.6 |
| Tailwind CSS | 3.4.1 |
| md-editor-v3 | 4.18.1 |
| Echarts | 5.4.3 |
| Swiper | 11.1.1 |
| GSAP | 3.12.5 |
| vue3-danmaku | 1.6.0 |

### 后台管理 `blog-frontend/kuailemao-admin/package.json`

| 技术 | 版本 |
|---|---|
| 脚手架 | Antdv Pro（`antdv-pro` 1.0.0-beta.3），CLI `@mistjs/cli` 0.0.1-beta.5 |
| Vue | 3.3.8 |
| Vue Router | 4.2.5 |
| Pinia | 2.1.7 |
| Ant Design Vue | 4.0.7 |
| UnoCSS | 0.56.5 |
| md-editor-v3 | 4.10.0 |
| packageManager | pnpm@8.10.0 |

### 运行时依赖（`README.md` L89–106）

| 名称 | 推荐环境 |
|---|---|
| MySQL | 8.0 |
| Redis | 7.2.3 |
| RabbitMQ | 最新 |
| MinIO | 最新 |
| JDK | 17 |
| Node | 16.17.0（README）；后台 lock 以 pnpm 8.10 为准 |
| pnpm | 8.12.0（README） |

## 3. 仓库目录结构（至少 3 层）

```
Ruyu-Blog/
├── README.md                         # 项目介绍、在线地址、技术栈、部署入口
├── LICENSE                           # Apache-2.0
├── .gitignore                        # 忽略 node_modules/dist/.idea，以及 ruyu-blog-doc/
├── .sh/
│   └── docker-restart.sh             # 重建并启动后端容器 ruyu-blog-hd:8088
├── img/                              # README 截图（含 new/ 目录）
├── sql/
│   ├── README.md                     # 导入顺序 + 默认管理员密码说明
│   ├── blog(部署).sql                # 旧完整库（无 banners/blacklist/photo）
│   ├── blog-banners.sql              # 旧增量 t_banners
│   ├── v1.5.0/Ruyu-Blog_v1.5.0.sql   # ★ 当前完整库（33 表）
│   └── v1.6.0/
│       ├── t_photo.sql               # ★ 升级：相册表
│       └── README.md                 # 相册菜单/权限需后台手工配置
├── blog-backend/                     # Spring Boot API（职责：业务、鉴权、存储、邮件、日志）
│   ├── pom.xml
│   ├── Dockerfile                    # openjdk:17，拷贝预构建 jar，暴露 8088
│   ├── README.md                     # 登录失败原因、X-Client-Type、Gitee 登录步骤
│   └── src/main/
│       ├── java/xyz/kuailemao/       # 见 architecture.md
│       └── resources/
│           ├── application.yml       # 仅 port + profile 占位；其余配置被注释且真实 yml gitignore
│           ├── mapper/*.xml
│           └── templates/*-email-template.html
└── blog-frontend/
    ├── kuailemao-blog/               # 访客前台（职责：展示与互动）
    │   ├── src/{apis,views,components,router,store,utils}
    │   ├── vite.config.ts            # 开发端口 99，/api → 8088，/wapi → 音乐服务
    │   ├── Dockerfile + default.conf # nginx:80，反代 /api 与 /wapi
    │   └── .env.development / .env.production
    └── kuailemao-admin/              # 站长后台（职责：RBAC + 内容审核）
        ├── src/{api,pages,router,stores,layouts}
        ├── vite.config.ts            # 开发端口 6678
        ├── mist.config.ts
        ├── Dockerfile + default.conf # nginx:81
        └── .env / .env.development / .env.production
```

一级目录职责：

| 目录 | 职责 |
|---|---|
| `blog-backend/` | 唯一后端；REST + Spring Security JWT + 业务服务 |
| `blog-frontend/` | 两个独立 Vue 应用（前台 / 后台），无 monorepo workspace |
| `sql/` | MySQL 建库与升级脚本 |
| `img/` | 文档截图，不参与构建 |
| `.sh/` | 运维脚本（仅后端 Docker 重启） |

仓库内 **没有** `docker-compose.yml`（与 `README.md` L115「Docker Compose 一键部署」不符）。

## 4. 子项目 / 多模块说明

三个独立可部署单元，**无 Maven 多模块、无 pnpm workspace**：

1. **blog-backend**：Spring Boot 单体。入口 `xyz.kuailemao.BlogBackendApplication`（`@MapperScan` + `@EnableMethodSecurity`）。
2. **kuailemao-blog**：Vite + Vue3 前台，静态路由。
3. **kuailemao-admin**：Antdv Pro 二次开发，菜单/路由由后端 `sys_menu` 动态下发。

通信方式：HTTP REST。前台通过 Vite `/api` 代理或 nginx `/api/` 转到后端 `8088`。后台同样走 `/api/`（生产 nginx）；开发环境 `VITE_APP_BASE_URL` / `VITE_APP_BASE_API` **提交值为空**，需本地填写。

## 5. 本地启动手册

### 5.1 前置条件

- JDK 17、Maven
- Node 16+、pnpm 8
- MySQL 8（库名模板为 `blog`，见 `application.yml` 注释 L13）
- Redis（模板：6379，database 1）
- RabbitMQ（模板：5672）
- MinIO（对象存储）
- 可选：网易云音乐 API 服务（前台 `/wapi`，nginx 指向 `:3000`）
- 可选：QQ 邮箱 SMTP（验证码 / 评论 / 友链通知）

### 5.2 数据库

```text
1. 创建库（字符集 utf8mb4）
2. 导入 sql/v1.5.0/Ruyu-Blog_v1.5.0.sql
3. 导入 sql/v1.6.0/t_photo.sql
4. 相册菜单与 5 条权限需在后台手工添加（sql/v1.6.0/README.md）
```

默认账号（`sql/README.md`；v1.5.0 dump 用户名为 `Admin` 而非 `ADMIN`）：

| 用途 | 用户名 | 密码 | 角色 |
|---|---|---|---|
| 后台超管 | `Admin` | `123456` | ADMIN |
| 测试账号 | `Test` | `123456` | Test（几乎只读；不可走前台登录） |
| 在线演示 | `Test` / `123456` | `README.md` L67 |

### 5.3 后端

1. 在 `blog-backend/src/main/resources/` 创建 **gitignored** 的 `application-dev.yml`，按 `application.yml` 注释模板填写 JWT / 数据源 / Redis / RabbitMQ / Mail / OAuth / MinIO / Quartz。
2. Maven 默认 profile `dev`（`pom.xml` L175–182），`application.yml` L4–5：`spring.profiles.active: @profiles.active@`。
3. 启动：`mvn spring-boot:run` 或 IDE 运行 `BlogBackendApplication`。
4. 访问：
   - API：`http://127.0.0.1:8088`
   - Knife4j：`http://127.0.0.1:8088/doc.html`（`application.yml` L130 注释）

**当前仓库状态：** `application-dev.yml` / `application-prod.yml` 被 `blog-backend/.gitignore` 忽略且本地不存在。`application.yml` 中除 `server.port` 与 profile 外全部注释。没有这些文件后端无法绑定 `@Value` 所需属性。

### 5.4 前台博客

```bash
cd blog-frontend/kuailemao-blog
pnpm install
pnpm dev          # vite，端口 99（vite.config.ts）
```

- 开发代理：`/api` → `VITE_SERVE`（`.env.development` 为 `http://localhost:8088`），rewrite 去掉 `/api`
- 访问：`http://localhost:99`

### 5.5 后台管理

```bash
cd blog-frontend/kuailemao-admin
pnpm install
# 必须先填写 .env.development 的 VITE_APP_BASE_URL / VITE_APP_BASE_API
pnpm dev          # mist → vite，端口 6678
```

- 访问：`http://localhost:6678`
- 登录必须带请求头 `X-Client-Type: Backend`（`src/api/common/login.ts`）

### 5.6 在线参考地址（`README.md` L63–77）

| 端 | URL |
|---|---|
| 前台 | https://kuailemao.xyz |
| 后台 | https://blog.kuailemao.xyz |
| 接口文档 | http://kuailemao.xyz:8088/doc.html#/home |

## 6. 环境变量与配置

### 6.1 后端（模板来自 `application.yml` 注释；真实值应在 `application-{dev,prod}.yml`）

| 配置项 | 默认/模板值 | 含义 |
|---|---|---|
| `server.port` | `8088`（生效） | HTTP 端口 |
| `spring.profiles.active` | Maven `@profiles.active@`（dev 默认） | 激活 profile |
| `spring.security.jwt.key` | `jwt-key` 占位 | HMAC 密钥 |
| `spring.security.jwt.expire` | `7` | JWT 有效天数 |
| `spring.datasource.url` | `jdbc:mysql://[IP]:[PORT]/blog?...Asia/Hong_Kong` | MySQL |
| `spring.datasource.username` | `root` | |
| `spring.data.redis.host/port` | 空 / `6379` | Redis |
| `spring.data.redis.database` | `1` | 库索引 |
| `spring.data.redis.timeout` | `10000` ms | |
| `spring.rabbitmq.host/port` | 空 / `5672` | |
| `spring.rabbitmq.queue.email` | `email_queue` | 邮件队列 |
| `spring.rabbitmq.queue.log-login` | `log_login_queue` | 登录日志队列 |
| `spring.rabbitmq.queue.log-system` | `log_system_queue` | 操作日志队列 |
| `spring.mail.host` | `smtp.qq.com` | |
| `spring.servlet.multipart.max-file-size` | `10MB` | 上传限制 |
| `spring.quartz.job-store-type` | `jdbc` | Quartz JDBC 存储（同一 `blog` 库） |
| `mail.article-email-notice` 等 | true/false | 各类邮件开关 |
| `mybatis-plus.global-config.db-config.logic-delete-field` | `isDeleted` | 逻辑删除 |
| `oauth.gitee.*` / `oauth.github.*` | 空 | JustAuth |
| `web.index.path` | `http://kuailemao.xyz/` | OAuth 回调后的前台首页 |
| `minio.endpoint/accessKey/secretKey/bucketName` | 空 | 对象存储 |
| `knife4j.enable` | true | 文档增强 |

### 6.2 前台博客 `.env.development`

| 变量 | 值 | 含义 |
|---|---|---|
| `VITE_APP_BASE_API` | `/api` | axios baseURL |
| `VITE_SERVE` | `http://localhost:8088` | Vite 代理目标 |
| `VITE_FRONTEND_URL` | 空 | |
| `VITE_MUSIC_BASE_API` | `/wapi` | 音乐 API 前缀（不走 `/api`） |
| `VITE_MUSIC_FRONTEND_URL` | 空 | 非空才显示 `/music` 菜单 |
| `VITE_MUSIC_SERVE` | 空 | `/wapi` 代理目标 |
| `VITE_YIYAN_API` | 空 | 每日一言；空则 `https://v1.hitokoto.cn/?c=a&encode=json` |
| `VITE_ENABLE_DEV_TOOLSBLOCKER` | `true` | 开发者工具拦截 |

生产 `.env.production`：`VITE_SERVE` 为空，依赖 nginx `/api/`。

### 6.3 后台 `.env`

| 变量 | 值 | 含义 |
|---|---|---|
| `VITE_APP_NAME` | `Ruyu-Blog` | |
| `VITE_APP_BASE` | `/` | vue-router history base |
| `VITE_APP_LOAD_ROUTE_WAY` | `BACKEND` | 动态路由来源 |

`.env.development` / `.env.production`：`VITE_APP_BASE_URL`、`VITE_APP_BASE_API` **提交为空**，必须本地配置后才能调后端。

## 7. 外部依赖服务

| 服务 | 用途 | 来源 |
|---|---|---|
| MySQL | 业务 + Quartz JDBC | `application.yml` 模板 |
| Redis | JWT 白名单、验证码、计数、限流、黑名单缓存 | `RedisConst.java` |
| RabbitMQ | 异步邮件、登录日志、操作日志 | `EmailRabbitConfig` / `LogRabbitConfig` |
| MinIO | 文章封面/插图、头像、banner、相册、站点资源 | `MinioConfig` / `FileUploadUtils` |
| SMTP（QQ） | 验证码、评论/留言/友链通知 | `EmailQueueListener` + Thymeleaf |
| Gitee / GitHub OAuth | 第三方登录 | `OauthController` + JustAuth |
| 网易云风格音乐 API | 前台播放器（非 Java 后端） | nginx `/wapi/` → `:3000` |
| 一言 hitokoto | 每日鸡汤 | `thirdParty/index.ts` |

## 8. 规模与复杂度粗估

| 指标 | 数量 | 来源 |
|---|---|---|
| 后端 Java 源文件 | 308 | `src/main/java` |
| Controller | 25（`UploadController` 无方法） | `controller/` |
| Service 接口 / 实现 | 26 / 26 | `service/` |
| Mapper | 25 | `mapper/`（含无 Controller 的 `ChatGptMapper`） |
| Entity `@TableName` | 25（含无 DDL 的 `t_chat_gpt`） | `domain/entity` |
| MySQL 表（最新） | 34（v1.5.0 的 33 + `t_photo`） | `sql/` |
| REST 方法（含 Security 登录/登出） | 约 154 | 见 `api-system.md` |
| 前台 `.vue` | 77 | `kuailemao-blog/src` |
| 后台 `.vue` | 102 | `kuailemao-admin/src` |
| 前台静态路由页 | 约 18 条 | `src/router/routers.ts` |
| 后台页面（pages） | 56 个 vue（含弹窗） | `src/pages` |

复杂度判断：中型全栈单体博客。RBAC + 动态菜单 + 异步日志/邮件使后台复杂度明显高于内容 CRUD。

## 9. 文档与规范索引

| 文档 | 路径 |
|---|---|
| 项目介绍 / 部署入口 | `README.md`（部署指向 https://kuailemao.xyz/article/48 或 Wiki；仓库无 Wiki 目录） |
| SQL 导入 | `sql/README.md`、`sql/v1.6.0/README.md` |
| 登录 / OAuth 备忘 | `blog-backend/README.md` |
| 黑名单说明 | `blog-backend/src/test/java/xyz/kuailemao/黑名单功能文档.md` |
| 后台权限指令 | `blog-frontend/kuailemao-admin/README.md` |
| 接口文档 | 运行时 Knife4j `/doc.html` |
| 被 gitignore 的外部文档 | `.gitignore` L13 `ruyu-blog-doc/`（不在本仓库） |

本目录（接手文档）：

```
superpowers/project/
├─ overview.md
├─ architecture.md
├─ modules.md
├─ business-flow.md
├─ api-system.md
└─ infrastructure.md
```
