# 郑陆宇的个人网站

求职作品集、技术博客与工作经历，面向招聘者与长期访客。站点由我独立维护，线上地址是 [www.zhengluyu.com](https://www.zhengluyu.com/)。

这里不是一份通用博客模板的演示站。公开页用来说明我是谁、做过什么、最近在写什么；后台用来发布真实内容。

## 在线地址

| | |
| --- | --- |
| 公开站点 | [www.zhengluyu.com](https://www.zhengluyu.com/) |
| 源码 | [github.com/zhengluyu-oss/personal-website](https://github.com/zhengluyu-oss/personal-website) |
| 作者 | 郑陆宇 · 后端 / 全栈 |

## 站点在讲什么

访客打开首页，应先认出身份与方向，再核验工作经历，再进入已发布的技术文章。

- **首页**：姓名、职业方向、行动入口，以及真实的文章与经历摘要
- **工作经历**：公司、岗位、时间与可公开成果，支持列表与详情
- **博客文章**：精选卡 + 最新列表，分类、标签、归档与文章详情
- **关于**：个人介绍与可公开联系方式

管理端用于写文章、维护经历、处理评论与站点信息。后台不对访客开放测试账号。

## 我在这套站点上做了什么

仓库早期有一份开源博客作为起点。当前对外产品已经按个人站重写，重点不在堆功能，而在把真实内容讲清楚，并按生产环境把发布、安全与性能收住。

**产品与信息架构**

- 前台从模板化口号站，改成可识别的个人介绍与求职作品集
- 新增工作经历模块（公开列表 / 详情，后台可维护）
- 博客区按已发布内容组织：分类语义路由、分页、可配置精选与分类文案
- 去掉打赏假数据、模板鼠标样式和明显演示向入口

**工程与上线**

- 对象存储改为阿里云 OSS
- 生产环境按模块发布（后端 / 前台 / 后台），带健康检查与上一版回滚目录
- 首页首屏、静态资源体积与 gzip 有可执行的检查

**安全与内容**

- 管理端登录增加邮箱二次校验
- 评论、搜索与页面渲染按不可信内容处理
- 接口限流与生产暴露面收敛

目录名里仍能看到历史仓库痕迹，那是改造范围选择，不代表对外品牌。对外名称、域名、文案和发布流程都以本站为准。

## 技术栈

| 部分 | 选型 |
| --- | --- |
| 公开前台 | Vue 3、TypeScript、Vite、Element Plus、Pinia |
| 管理后台 | Vue 3、TypeScript、Ant Design Vue |
| 后端 | JDK 17、Spring Boot 3、Spring Security、MyBatis-Plus |
| 数据与中间件 | MySQL 8、Redis、RabbitMQ |
| 存储 | 阿里云 OSS |
| 线上 | Nginx、systemd，脚本发布到云主机 |

## 仓库结构

```
blog-frontend/kuailemao-blog    公开站点
blog-frontend/kuailemao-admin   管理后台
blog-backend                    API 与权限
sql                             表结构与迁移
scripts                         发布、校验与运维脚本
deploy                          Nginx 等部署配置
```

## 本地开发

需要 JDK 17、MySQL 8、Redis、Node.js 18+、pnpm。RabbitMQ 在用到邮件和操作日志时再启动。

```bash
# 后端
cd blog-backend
# 按本地环境配置 application-dev，再启动 Spring Boot

# 公开前台
cd blog-frontend/kuailemao-blog
pnpm install
pnpm dev

# 管理后台
cd blog-frontend/kuailemao-admin
pnpm install
pnpm dev
```

生产发布使用仓库内的发布脚本，按模块构建并上传静态资源或后端包。不要把开发默认账号直接用于线上。

## 许可

Apache License 2.0，全文见 [LICENSE](LICENSE)。

本仓库由郑陆宇维护。早期曾以一份 Apache-2.0 开源博客为起点，当前站点定位、信息架构、首页、工作经历、安全策略与发布流程均为独立改写，不再沿用原项目的产品叙事与运营渠道。
