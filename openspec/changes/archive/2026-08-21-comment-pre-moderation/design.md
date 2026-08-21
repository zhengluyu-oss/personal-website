## Context

`t_comment.is_check`、公开列表 `eq(isCheck, 1)`、管理端 `/comment/back/isCheck` 已存在。线上/SQL 默认值为 **1**，`userComment` 保存时未置 0，导致新评立即公开。用户改主意：不要全站关评论，改为先审后发。原 `site-comment-toggle` 已废弃。

## Goals / Non-Goals

**Goals:**

- 用户仍可发表评论（登录等现有规则不变）
- 新评论默认待审核，公开端不可见，直至管理员通过
- 公开评论数与列表一致；待审不抬公开计数
- 用户知悉「已提交待审核」

**Non-Goals:**

- 不做全站关闭评论开关
- 不做敏感词/AI 自动审核
- 不重做管理端评论列表（已有通过开关）
- 不强制改留言板主帖/树洞（本变更范围：`Comment` 模块，含 type=文章评论与留言板下的评论）

## Decisions

1. **写入时强制 `isCheck = 0`**  
   - 在 `userComment` 保存前设置，不信任客户端/DTO。  
   - ALTER `is_check` DEFAULT 0 防漏写。

2. **公开读路径保持过滤**  
   - 已有 `getComment` 等只查已通过；验收回归即可。

3. **Redis 文章评论数**  
   - 从「发表成功立即 +1」改为「仅审核通过时 +1」（`isCheckComment` 已有 +/– 逻辑）。  
   - 发表路径去掉对 `ARTICLE_COMMENT_COUNT` 的立即 increment，避免待审虚增与重复。

4. **前端提示**  
   - 发表成功返回/提示改为待审核语义；不在列表临时插入未过审评论。

## Risks / Trade-offs

- [历史评论已是通过态] → 不回溯；仅约束新评论。  
- [站长自己评论也要审] → 默认一律待审，简单一致；若需管理员豁免可后续加。  
- [邮件通知仍会发] → 有利于提醒审核，保留。

## Migration Plan

1. 执行 SQL 改默认值为 0。  
2. 发 backend（+ blog 提示若有改动）。  
3. 发一条测试评论：前台不可见 → 后台通过 → 可见。  
4. 回滚：恢复默认 1 与发表即通过（不推荐）。

## Open Questions

- 无阻塞。管理员本人评论是否免审：默认否。
