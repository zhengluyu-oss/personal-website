## Context

求职向个人站当前顶栏「归档」指向分类/标签/时间轴，不符合作品集「Experience」习惯。用户选定方案 A：独立工作经历页 + 后台结构化配置 + 美观时间线。技术栈为现有 Spring Boot 博客后端、`kuailemao-admin`、`kuailemao-blog`，约束为不改包名/目录名。

## Goals / Non-Goals

**Goals:**

- 顶栏一级「工作经历」进入 `/experience` 时间线页
- 后台可增删改、排序、启停经历条目
- 分类/标签迁入「其他」，保留可达性
- 公开 API 只返回启用数据；写操作鉴权

**Non-Goals:**

- 不做外部 Headless CMS
- 不把经历做成普通博客文章代替结构化字段
- 不改首页粒子/Banner 逻辑
- 不强制录入真实履历文案（可先空库后台填）

## Decisions

1. **结构化表 `t_work_experience`，而非 Markdown 文章**  
   - 字段：公司、岗位、开始/结束日期、是否至今、要点（多行文本或分隔存储）、`order_num`、`status`、软删时间戳。  
   - 备选「一篇文章」被否：不利于时间线排版与后台字段校验。

2. **API 形态对齐现有模块（如友链）**  
   - 公开：`GET /experience/list`（permit / 无登录）。  
   - 管理：`/experience/back/*` + `@PreAuthorize('blog:experience:*')`。  
   - 备选塞进 `websiteInfo` JSON：扩展性差，弃用。

3. **前台垂直时间线组件页**  
   - 倒序展示；空状态提示去后台配置。  
   - 样式贴合现有 Element Plus / 站点 CSS 变量，避免引入新 UI 框架。

4. **管理端动态菜单靠 SQL 注入 `sys_menu`/`sys_permission`**  
   - 与现网 RBAC 一致；Admin 角色授权后可见。

5. **导航改动仅前端 Menu/MoveMenu**  
   - 「工作经历」一级；分类、标签进「其他」；时间轴一并进「其他」以免死链。

## Risks / Trade-offs

- [菜单/权限 SQL 与线上 id 冲突] → 使用足够大的新 id，执行前查 `MAX(id)`。  
- [要点用纯文本难排版] → 约定每行一条要点，前台按行拆分。  
- [仅部署前端未跑 SQL] → 部署清单强制先 SQL 再发版。

## Migration Plan

1. 执行 `sql/work-experience.sql`（表 + 菜单权限）。  
2. 发布 backend → admin → blog。  
3. 后台录入经历并启用以验收前台。  
4. 回滚：恢复导航代码；停用菜单；表可保留。

## Open Questions

- 无阻塞项。时间轴默认迁入「其他」。
