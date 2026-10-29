# 知识库客服标注问答集（合成资料）

验收资料均为虚构条目，不连接生产知识库。基准条目 A《Redis 持久化》正文含“RDB 定期生成快照；AOF 记录写命令，恢复时重放命令。AOF 重写可缩小文件。”；条目 B《HTTP 状态码》正文含“200 表示请求成功，404 表示资源不存在。”；附件 C 有两段，分别讨论 RDB 与 AOF 的限制。

| 编号 | 问题及上下文 | 预期证据 | 预期结果 |
| --- | --- | --- | --- |
| Q1 | Redis 有哪些数据落盘方式？ | A 中 RDB、AOF 片段 | 找到证据并逐条引用 |
| Q2 | 持久化方式有哪些 | A 中 RDB、AOF 片段 | 中文改写可召回 |
| Q3 | RDB 和 AOF 各有什么限制？ | A 与 C 的两种限制片段 | 跨段归纳，结论分开引用 |
| Q4 | Redis 的主从复制默认端口是什么？ | 无 | 拒答，不调用模型 |
| Q5 | 前轮问“Redis 有哪些数据落盘方式？”，追问“第二种方式有什么限制？” | 当前 A/C 中 AOF 限制片段 | 基于当前资料回答，不把旧回答当证据 |
| Q6 | 历史声称“AOF 永不丢数据”，追问“为什么永不丢？” | 无直接支持 | 拒答，不复述伪造历史 |
| Q7 | 提问过程中撤销权限或更新条目版本 | 旧证据失效 | 丢弃回答和摘录 |

记录口径：召回率按 Q1/Q2/Q3/Q5 是否取到预期片段；证据准确率按返回片段是否支持该问；拒答按 Q4/Q6/Q7；延迟记录检索与完整调用耗时。实施期在隔离 PostgreSQL 中运行同一批样本，并记录改动前后结果。

## 改动前影响检查

GitNexus 对 `KnowledgeAssistantStore` 与共享 `KnowledgeStore` 的 upstream 风险判定为 CRITICAL，分别报告 350 与 338 个潜在受影响符号；`KnowledgeAssistantCloud.generate` 也标记 CRITICAL。图中包含较多跨模块间接流程，因此实施时不修改共享 `KnowledgeStore.search`，只改客服专用服务和存储逻辑。`KnowledgeAssistantService` 为 UNKNOWN，源码确认直接入口为 `KnowledgeAssistantController`，测试入口为 `KnowledgeAssistantTest`。现有测试覆盖匿名与撤权、版本变化、READY 文件、并发及日额度；新增测试应集中覆盖检索质量、逐条引用和追问。
