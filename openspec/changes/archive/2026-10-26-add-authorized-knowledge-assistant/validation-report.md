# 知识库客服实施验证（2026-10-26）

- 后端：`mvn -q -Dtest=KnowledgeAssistantTest,KnowledgeSecurityTest,KnowledgeBoundaryTest test` 通过。覆盖匿名、停用账号、未授权、默认关闭、无证据、伪造来源、生成中撤权、供应商失败不重试与有效站内来源。
- 隔离 PostgreSQL：`python scripts/knowledge/verify-postgres.py` 通过。迁移可重复、事务回滚与漂移拒绝有效；合成数据库验证并发额度、删除和修订失效、当前 READY 文件片段及旧文件版本失效。
- 隔离备份恢复：`python scripts/knowledge/verify-backup-restore.py` 通过。`knowledge_assistant.usage` 随知识库元数据一起恢复，RLS 隔离仍有效，日记数据不可读。
- 前端：`pnpm run build:app` 通过。合成 API 的 `assistant-ui-qa.mjs` 验证首页、博客、关于我、文章、知识库，宽度 360/390/430/1440px、键盘焦点、与现有右下角控件不重叠、来源跳转、模型 HTML 仅显示为文本、刷新和撤权清理。
- 现有 `pnpm test` 为 46/47 通过；失败项为未修改的 `src/utils/seo.test.ts`，Node 测试运行器无法解析 `seo.ts` 中现有的 `@/config` 别名。单独运行该用例复现同一错误；前端构建及本变更浏览器验收通过。

功能保持默认关闭。生产启用前须由站主核对独立模型凭据、供应商数据政策、费用额度并执行生产备份；本次未连接供应商或部署生产。
