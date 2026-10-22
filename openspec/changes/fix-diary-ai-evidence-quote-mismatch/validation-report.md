# 验证记录（2026-10-22）

- 后端纯逻辑测试通过：`DiaryQuoteCatalogTest`、`DiaryValidationTest`、`DiaryAiEvidenceTest`、`DiaryAiAuditTest`、`DiaryAiTextTest`、`CoachingContractTest`、`CoachingCoreOnlyTest`。
- `mvn -q -DskipTests package` 通过；博客前端 `pnpm exec vite build` 通过；`openspec validate --strict` 通过；本次改动的 `git diff --check` 通过。
- 受控 HTTP 回归在 Windows JDK 21 上于测试夹具创建回环服务器时失败：`Unable to establish loopback connection`，尚未执行测试断言。
- 在 WSL Ubuntu / JDK 17 上，夹具能启动，但 Java HTTP 客户端无法连上本地夹具，相关断言得到 `*_CONNECTION_FAILED`；Python 本地回环服务可响应。该环境问题尚未定位，不能把相关用例视为通过。
- 本次未改数据库结构。生产服务只读检查：服务 active，当前 JAR 和外置生产配置存在，公开健康请求为 200，旧 release 目录存在。未核对生产未结束任务，也未发布。
- 工作区有知识库、首页、移动端和旧变更归档等未提交改动。直接使用当前工作区构建发布会携带这些变更；发布前须隔离本次修改并完成受控 HTTP 回归。
- 真实模型的本人问答与复盘验收尚未执行，不能宣称 `QUOTE_MISMATCH` 在生产已解决。
