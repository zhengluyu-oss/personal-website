# 知识库客服向量检索

知识库客服复用日记 AI 已配置的阿里云百炼 `text-embedding-v4` 和 PostgreSQL 的 `diary_vector.vector` 类型。知识库向量保存在独立的 `knowledge_assistant` 表中，不读取日记向量或日记原文。

## 上线顺序

1. 先完成加密备份，核对 PostgreSQL 中已有 `diary_vector.vector` 和知识库 schema 版本 4。
2. 以数据库管理员运行 `scripts/knowledge/005-assistant-vector.sql`。脚本可重复执行，创建强制 RLS 的 `vector_entry`、`vector_chunk`，并记录版本 5 指纹。
3. 发布后端，确认服务健康。新配置默认关闭，未启用时沿用关键词客服。
4. 在外置 `application-prod.yml` 的 `knowledge.assistant` 下设置 `vector-enabled: true`，重启后端。后台每次最多发送 8 个片段，每 3 秒最多处理一批；失败最多重试 3 次。首次同步约 230 万字的现有正文会产生向量模型费用。
5. 检查覆盖率、失败数和问答来源。用“给我详细介绍一下 java”等真实问题核对返回的条目；再用无依据问题检查拒答。`minimum-similarity` 默认 0.45，只是初始值，需要用本知识库的正反问题校准。

## 状态核对

以下查询以管理员身份执行；不要打印或记录正文、密钥、向量。

```sql
SELECT status,count(*) FROM knowledge_assistant.vector_entry GROUP BY status ORDER BY status;
SELECT count(*) FROM knowledge_assistant.vector_chunk;
SELECT count(*) FROM knowledge_assistant.vector_entry WHERE status='FAILED' AND attempts>=3;
```

只检索当前条目版本、当前附件版本、已就绪的索引和当前向量模型配置。修改或删除条目后旧向量立即退出问答；后台清理删除条目的派生向量。向量服务不可用时，问答回退到关键词检索。

## 回退

先在外置配置中将 `vector-enabled` 设为 `false` 并重启，客服随即回到关键词检索。保留版本 5 表以便检查或恢复，不在回退时删除生产数据。若新后端本身健康检查失败，使用原有发布脚本的 JAR 回滚；已新增的表不影响旧版客服。
