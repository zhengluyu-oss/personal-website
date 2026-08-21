# Ruyu-Blog 数据模型与基础设施

> **未直连数据库**，以下均为 `[代码推断]`（SQL 文件 + 实体类 + 配置模板）。  
> 分析日期：2026-08-18。

## 第一部分：数据模型

### 1. 代码层来源

| 来源 | 路径 |
|---|---|
| 完整 dump | `sql/v1.5.0/Ruyu-Blog_v1.5.0.sql`（MySQL 8.0.38 导出标注） |
| 升级 | `sql/v1.6.0/t_photo.sql` |
| 旧库 | `sql/blog(部署).sql`、`sql/blog-banners.sql`（勿作最新基线） |
| ORM | `xyz.kuailemao.domain.entity.*` `@TableName` |
| MP 逻辑删除 | `isDeleted` / 1 / 0（`application.yml` 模板） |

**实例信息（模板）：** 库名 `blog`；字符集业务表以 `utf8mb4` 为主；引擎 InnoDB。v1.6.0 脚本标注源库名 `blog-dev`（导出环境，不代表必须用此库名）。

### 2. 数据整体介绍

按业务域：

1. **RBAC / 系统：** `sys_user`、`sys_role`、`sys_user_role`、`sys_menu`、`sys_role_menu`、`sys_permission`、`sys_role_permission`、`sys_log`、`sys_login_log`、`sys_website_info`
2. **内容：** `t_article`、`t_article_tag`、`t_category`、`t_tag`
3. **互动：** `t_comment`、`t_like`、`t_favorite`、`t_leave_word`、`t_tree_hole`、`t_link`
4. **媒体：** `t_banners`、`t_photo`
5. **安全：** `t_black_list`
6. **调度：** `QRTZ_*` 11 张
7. **代码有实体、SQL 无表：** `t_chat_gpt`（`ChatGpt.java`）

关系：无数据库外键（Quartz 内部除外）。多态互动用 `type` + `type_id`。相册自关联 `parent_id`。

读写特征：文章列表/详情读多；访问量写 Redis；评论/点赞写 MySQL；日志异步写；菜单读多写少。

### 3. 表详解（业务表）

字段来自 v1.5.0 / v1.6.0 DDL。未列出的默认值见 SQL。索引：业务表几乎仅 PRIMARY KEY。

#### 3.1 `sys_user` — 用户 `[代码推断]`

实体：`User.java` `@TableName("sys_user")`

| 列 | 类型 | 说明 |
|---|---|---|
| id | bigint UNSIGNED AI | PK |
| nickname | varchar(50) | |
| username | varchar(50) NOT NULL | |
| gender | tinyint 默认 0 | 0 未定义 1 男 2 女 |
| password | varchar(100) | BCrypt |
| avatar | varchar(255) NOT NULL | |
| intro | varchar(100) | |
| email | varchar(50) | |
| register_ip | varchar(100) NOT NULL | |
| register_type | tinyint NOT NULL | 0 邮箱 1 Gitee 2 Github |
| register_address | varchar(50) | |
| login_ip / login_address | | |
| login_type | tinyint | |
| login_time | datetime NOT NULL | |
| is_disable | tinyint 默认 0 | |
| create_time / update_time | datetime | |
| is_deleted | tinyint 默认 0 | |

种子：id=1 `Admin` / BCrypt 对应明文 `123456`；id=88065990 `Test` 同密码。`sql/README.md` 写用户名 `ADMIN`，与 v1.5.0 dump 的 `Admin` 不一致，**以 dump 为准**。

#### 3.2 `sys_role`

| 列 | 说明 |
|---|---|
| id, role_name, role_key | ADMIN / Test / USER |
| status | 0 正常 1 停用 |
| order_num, remark | |
| create_time, update_time, is_deleted | |

种子：1 超级管理员 ADMIN；2 测试角色 Test；3 普通用户 USER。

#### 3.3 `sys_user_role`

`user_id` int；`role_id` **varchar(20)**（与角色 id 类型不一致）。

#### 3.4 `sys_menu`

后台路由元数据：title, icon, path, component, redirect, affix, parent_id, name, hide_in_menu, url, hide_in_breadcrumb, hide_children_in_menu, keep_alive, target, is_disable, order_num, 时间戳, is_deleted。

#### 3.5 `sys_role_menu` / `sys_permission` / `sys_role_permission`

权限字 `permission_key`（如 `blog:article:list`），`menu_id` 挂菜单。`sys_role_permission` 无 is_deleted。

#### 3.6 `sys_log` / `sys_login_log`

操作日志含 module、method、req_parameter、return_parameter、耗时、state（0 成功 1 失败 2 异常）。  
登录日志 type：0 前台 1 后台 2 非法。

#### 3.7 `sys_website_info`

站长头像/名称/文案/背景、Gitee/GitHub、站点名、通知、公告、备案、start_time。种子站长 `Ruyu`。

#### 3.8 `t_article`

| 列 | 说明 |
|---|---|
| user_id, category_id | 作者、分类 |
| article_cover | varchar(1024) |
| article_title | varchar(50) |
| article_content | longtext |
| article_type | 1 原创 2 转载 3 翻译 |
| is_top | |
| status | 1 公开 2 私密 3 草稿 |
| visit_count | 默认 0 |
| 时间戳 / is_deleted | |

#### 3.9 `t_article_tag` / `t_category` / `t_tag`

多对多中间表；分类/标签仅 name + 时间 + 逻辑删。

#### 3.10 `t_comment`

type 1 文章 2 留言板；parent_id / reply_id；is_check 默认 1。

#### 3.11 `t_leave_word` / `t_tree_hole`

留言 content longtext；树洞 content varchar(100)。

#### 3.12 `t_favorite`

type 1 文章 2 留言板；is_check 默认 1。

#### 3.13 `t_like`

type 1 文章 2 评论 3 留言板。**无 is_deleted、无 is_check。**

#### 3.14 `t_link`

name/url/description/background；is_check 默认 0；email varchar(20)。

#### 3.15 `t_banners`

path, size, type(MIME), user_id, sort_order, create_time。**无逻辑删。** 校对 `utf8mb4_unicode_ci`。

#### 3.16 `t_black_list`

user_id 可空；reason；banned_time / expires_time；type 1 用户 2 路人；ip_info JSON。

#### 3.17 `t_photo`（v1.6.0）

type 1 相册 2 照片；parent_id；url；is_check 默认 1；size double kb。

#### 3.18 Quartz 11 表

标准 JDBC JobStore：`QRTZ_JOB_DETAILS`、`QRTZ_TRIGGERS`、`QRTZ_SIMPLE_TRIGGERS`、`QRTZ_CRON_TRIGGERS`、`QRTZ_BLOB_TRIGGERS`、`QRTZ_SIMPROP_TRIGGERS`、`QRTZ_FIRED_TRIGGERS`、`QRTZ_CALENDARS`、`QRTZ_LOCKS`、`QRTZ_PAUSED_TRIGGER_GRPS`、`QRTZ_SCHEDULER_STATE`。

种子 Job：`refreshTheCache`，SIMPLE 间隔 300000 ms，类名 `xyz.kuailemao.quartz.RefreshTheCache`。Java `QuartzConfig` 的 `@Configuration` 被注释，**运行时是否仍由 JDBC 存储拉起该 job，未直连库故无法确认。**

#### 3.19 缺失 DDL：`t_chat_gpt`

实体字段（`ChatGpt.java`）：conversation、is_check 等。权限种子 136–139 仍引用 ChatGpt。

### 4. ER 关系（逻辑）

```
sys_user ──< sys_user_role >── sys_role ──< sys_role_menu >── sys_menu
                                 └──< sys_role_permission >── sys_permission

t_article.category_id → t_category
t_article ──< t_article_tag >── t_tag
t_comment.type+type_id → t_article | t_leave_word
t_photo.parent_id → t_photo
```

### 5. 全表清单（34）

`sys_user, sys_role, sys_user_role, sys_menu, sys_role_menu, sys_permission, sys_role_permission, sys_log, sys_login_log, sys_website_info, t_article, t_article_tag, t_category, t_tag, t_comment, t_leave_word, t_favorite, t_like, t_link, t_tree_hole, t_banners, t_black_list, t_photo,` + 11 张 `QRTZ_*`。

### 6. 命名规范 `[代码推断]`

- 系统表 `sys_`；业务表 `t_`；Quartz `QRTZ_`
- 逻辑删除 `is_deleted`；审核 `is_check`；禁用 `is_disable`
- Java 实体驼峰，MP 下划线转驼峰
- 权限字 `域:资源:动作`（blog/system/monitor）

---

## 第二部分：基础设施与运维

### 1. 数据库连接与连接池

模板使用 Spring Boot 默认 Hikari（未在 yml 显式配池大小）。Quartz 另配 `dataSource.quartz_jobs` 指向同一 MySQL。真实 URL/账密在 gitignored yml。

### 2. 缓存 Redis

| Key 前缀 | 用途 | 来源 |
|---|---|---|
| `jwt:white:list:` | JWT 白名单 | `RedisConst.JWT_WHITE_LIST` |
| `verifyCode:` | 邮箱验证码，5 分钟 | L18–23 |
| `article:count:favorite:` / `like:` / `comment:` | 计数 | |
| `article:count:visit:` | 访问量 | |
| `article:count:visit:limit:` | 15 分钟去重 | |
| `email:verification:link:` | 友链邮件审核 | |
| `blackList:uid:` / `ip:` | 封禁缓存 | |
| `limit:` / `expire_time_limit:` | 接口限流 | `AccessLimitInterceptor` |

启动：`AutomaticTasks` 初始化文章计数与黑名单缓存。配置类 `RedisConfig`。

### 3. 文件存储 MinIO

`MinioConfig` 读 `minio.endpoint/accessKey/secretKey/bucketName`。`FileUploadUtils` 上传删除。用途：文章封面/插图、banner、头像、站点背景、相册。multipart 模板上限 10MB；后台 nginx `client_max_body_size 100M`。

### 4. 日志

- 应用日志：未在仓库发现 logback-spring.xml，使用 Boot 默认。
- 业务操作日志：AOP + Rabbit → `sys_log`
- 登录日志：Handler + Rabbit → `sys_login_log`
- MyBatis SQL stdout 在 yml 模板中开启（`StdOutImpl`），生产应关闭（需人工在 prod yml 确认）

### 5. 定时任务

`RefreshTheCache`：Redis 文章访问量 → `t_article.visit_count`。  
`QuartzConfig.java` L10 `//@Configuration` → Spring 不注册 JobDetail/Trigger Bean。SQL 仍有 Quartz 行。

### 6. 消息队列

| Queue | Exchange | Listener | 作用 |
|---|---|---|---|
| email_queue | email_exchange | EmailQueueListener | 验证码与通知邮件 |
| log_login_queue | log_exchange | LogQueueListener.handlerLoginLog | 登录日志 |
| log_system_queue | log_exchange | LogQueueListener.handlerSystemLog | 操作日志 |

DirectExchange + 持久化队列（`EmailRabbitConfig` / `LogRabbitConfig`）。消费者重试模板：3 次。

### 7. 邮件

`JavaMailSender` + Thymeleaf。模板目录 `src/main/resources/templates/`（注册/评论/回复/留言/友链等）。开关见 `mail.*` 配置。

### 8. 部署架构

仓库 **无 docker-compose**。三个独立镜像：

| 组件 | Dockerfile | 基镜像 | 暴露 | 内容 |
|---|---|---|---|---|
| 后端 | `blog-backend/Dockerfile` | openjdk:17 | 8088 | 预构建 `blog-backend-0.0.1-SNAPSHOT.jar` |
| 前台 | `kuailemao-blog/Dockerfile` | nginx | 80 | 预构建 `dist/` |
| 后台 | `kuailemao-admin/Dockerfile` | nginx | 81 | 预构建 `dist/` |

`.sh/docker-restart.sh`：停删容器 `ruyu-blog-hd`，build，`-p 8088:8088`。

推荐机器：`README.md` 最低 2 核 4G，作者腾讯云 CentOS。

### 9. 构建与打包

```bash
# 后端
cd blog-backend && mvn -Pdev package    # 或 -Pprod
# jar: target/blog-backend-0.0.1-SNAPSHOT.jar

# 前台
cd blog-frontend/kuailemao-blog && pnpm install && pnpm build

# 后台
cd blog-frontend/kuailemao-admin && pnpm install && pnpm build
```

后端 Dockerfile **不会** 在镜像内 Maven 构建。

### 10. 网络与代理

见 `api-system.md` §6。nginx 写死域名 `kuailemao.xyz`。前台 `default.conf` 还把 `blog.kuailemao.xyz` 反代到 `:81`。

Knife4j：`http://127.0.0.1:8088/doc.html`。

### 11. 安全清单

| 项 | 现状 |
|---|---|
| CORS | 自定义 Filter，镜像 Origin，允许 Authorization / X-Client-Type |
| CSRF | 关闭 |
| 密钥 | JWT/DB/OAuth/MinIO/SMTP 在 gitignored yml；模板有占位 |
| 环境隔离 | Maven profile dev/prod |
| Knife4j | 匿名可访问 |
| 默认口令 | Admin/Test `123456`，部署必须改 |
| JWT | Redis 白名单注销；权限每次查库 |
| 限流 | 注解级 Redis |
| 上传 | 类型/目录由 `UploadEnum` 约束（细节见 FileUploadUtils） |

### 12. 第三方依赖

Gitee/GitHub OAuth（JustAuth）；QQ SMTP；MinIO；可选网易云 API（`:3000`）；一言 hitokoto。

### 13. 与直连差异

未直连。若本地已导入 SQL，可能存在：相册菜单是否手工添加、Quartz 触发器是否 PAUSED、`t_chat_gpt` 是否被手工建过。需要时可授权后补 `[数据库直连]` 章节。
