# 标签内手动排序与序号

每个标签有独立顺序，前后台列表均显示「标签名 · 序号」。多标签条目分别编号；未加标签的条目不编号。选择标签后，分页按此顺序；关键词和类型筛选显示原标签内的序号，不重新从1编号。

后台选择标签并查找后，可上移、下移或指定位置。新增条目追加在已排条目之后；初次使用按创建时间、UUID排序。正文修改不改变顺序。删除或移出标签后显示序号连续补齐；恢复条目可回到此前保存的位置。上限每标签一万条，超限明确拒绝。

上线前由迁移账号执行 `scripts/knowledge/005-tag-order.sql`。新增独立 `knowledge_order` schema，不改原日记或知识库表结构指纹。顺序与幂等记录需随数据库完整备份；回退应用时保留该schema。调整操作仅站主可调用，使用原owner锁、RLS、当前集合摘要和mutationId；网络未确认可确认原请求，多窗口并发修改返回409。

新增API：GET `/knowledge/view/tags/{id}/order`；POST `/knowledge/view/tag-numbers`（最多50个当前列表UUID）；POST `/knowledge/manage/tags/{id}/order`（mutationId、entryId、position、expectedDigest）。均沿用访问资格校验及private/no-store。
