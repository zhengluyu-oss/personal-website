# Redis 拆分文章扩写记录

这些文件是 2026-10-26 对线上知识库中 8 篇 Redis 拆分文章追加的章节。文件名为知识库条目 ID，正文保留在数据库中；原始长文《redis的基础知识》未修改。

| 条目 | 新修订号 | 扩写文件 |
| --- | ---: | --- |
| Redis 基础：概念、性能与使用场景 | 2 | `a55e4c34-3944-4f8c-9a64-ddf3e1e9fb78.md` |
| Redis 基础：数据类型与常用命令 | 2 | `9434028b-933f-45b8-8ae1-78f0d2dc558c.md` |
| Redis 原理：过期机制与内存淘汰 | 2 | `18778839-a95e-4aa9-b177-e4467700367c.md` |
| Redis 原理：RDB 与 AOF 持久化 | 2 | `ce79cadf-44a6-49ac-9099-9cc0a67b0938.md` |
| Redis 缓存：Cache Aside 与常见问题 | 2 | `332f60e5-623b-47dc-81bf-c5e4cc9da42c.md` |
| Redis 实战：MySQL 对比与 Spring Boot 应用 | 2 | `f2add22a-bf60-4df7-a8f8-7dafd5cad2b5.md` |
| Redis 进阶：分布式锁与消息队列 | 2 | `00ad6c75-79d7-4095-a029-f141b2033d9a.md` |
| Redis 学习路线、面试问题与知识结构 | 2 | `f3b2ba31-e8c8-4789-83cf-b35fa1968fd4.md` |

追加前的 8 篇完整记录备份在站主本机 `Documents/知识库备份/redis-entries-before-enrichment-20261026-145401.jsonl`，不纳入仓库。线上更新使用单个数据库事务和条目修订号、正文摘要双重校验；8 篇均新增修订 2，条目正文与修订快照一致。
