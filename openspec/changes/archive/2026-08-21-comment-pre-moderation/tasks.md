## 1. Backend moderation default

- [ ] 1.1 `userComment` 保存前强制 `isCheck = 0`；发表成功时不把待审评论计入公开 Redis 评论数
- [ ] 1.2 确认公开 `getComment` 仍只返回已通过；审核通过/取消时计数逻辑正确
- [ ] 1.3 新增 SQL：`t_comment.is_check` 默认值改为 0，并在目标库执行

## 2. Frontend UX

- [ ] 2.1 博客发表成功提示改为「已提交，待管理员审核后展示」或等价文案
- [ ] 2.2 确认发表后列表不插入未过审评论

## 3. Deploy & verify

- [ ] 3.1 部署 backend（及 blog 若有改动）；执行 SQL
- [ ] 3.2 验收：新评论前台不可见 → 后台通过后可见；公开计数一致
