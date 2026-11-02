# 标签内手动排序与序号

每个标签有独立顺序，前后台列表均显示「标签名 · 序号」。多标签条目分别编号；未加标签的条目不编号。选择标签后，分页按此顺序；关键词和类型筛选显示原标签内的序号，不重新从1编号。

后台选择标签并查找后，可上移、下移或指定位置。新增条目追加在已排条目之后；初次使用按创建时间、UUID排序。正文修改不改变顺序。删除或移出标签后显示序号连续补齐；恢复条目可回到此前保存的位置。上限每标签一万条，超限明确拒绝。

上线前由迁移账号执行 `scripts/knowledge/005-tag-order.sql`。新增独立 `knowledge_order` schema，不改原日记或知识库表结构指纹。顺序与幂等记录需随数据库完整备份；回退应用时保留该schema。调整操作仅站主可调用，使用原owner锁、RLS、当前集合摘要和mutationId；网络未确认可确认原请求，多窗口并发修改返回409。

新增API：GET `/knowledge/view/tags/{id}/order`；POST `/knowledge/view/tag-numbers`（最多50个当前列表UUID）；POST `/knowledge/manage/tags/{id}/order`（mutationId、entryId、position、expectedDigest）。均沿用访问资格校验及private/no-store。

## 2026-11-02 上线验证

- 从独立 checkout 的 `bb96aab` 构建并部署后端、博客前台和管理后台；工作区中未提交的日记统一聊天改动未进入发布包。后端生产构建、前后台构建及后台类型检查通过，产物检查通过。
- 新 schema 迁移完成，两张表均由 `knowledge_migrator` 拥有且强制 RLS。生产迁移在同一事务临时授予迁移角色建 schema 所需 CREATE 权限，提交前收回；运行角色未设置 owner 时读取排序表为零行。
- 发布前保存全库加密备份：`/root/luyu-releases/tag-order-bb96aab-20261102/diary-and-knowledge-before.dump.age`；旧应用和前后台快照保存在同目录的 `luyu-release-backup-IYJYSs/`，回退保留新增 schema。
- 服务 active；`/knowledge`、`/admin/` 及新增前后台代码资源返回200，资源摘要与本地一致；匿名读取/修改排序接口均返回401。生产私密配置及 nginx 配置摘要保持一致，未调用付费模型或修改真实内容排序。
- 上线 JAR SHA256：`6b7264c12cde0400bd399cda71328745bcce63218ca54b39bff022ac78a12c2d`。
