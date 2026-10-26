# 验证记录（2026-10-22）

- 后端纯逻辑测试通过：`DiaryQuoteCatalogTest`、`DiaryValidationTest`、`DiaryAiEvidenceTest`、`DiaryAiAuditTest`、`DiaryAiTextTest`、`CoachingContractTest`、`CoachingCoreOnlyTest`。
- `mvn -q -DskipTests package` 通过；博客前端 `pnpm exec vite build` 通过；`openspec validate --strict` 通过；本次改动的 `git diff --check` 通过。
- 受控 HTTP 回归在 Windows JDK 21 上于测试夹具创建回环服务器时失败：`Unable to establish loopback connection`，尚未执行测试断言。
- 在 WSL Ubuntu / JDK 17 上，夹具能启动，但 Java HTTP 客户端无法连上本地夹具，相关断言得到 `*_CONNECTION_FAILED`；Python 本地回环服务可响应。该环境问题尚未定位，不能把相关用例视为通过。
- 本次未改数据库结构。发布前以生产数据库角色检查，问答与期间复盘的排队/运行任务均为 0；外置生产配置、当前 JAR 和可回滚旧包均存在。
- 本地提交 `6083f1b60`。生产原包 SHA-256 为 `347ca54d398fe31d07223d9e35ac488b289a4e536b3ca9e7a5a62287067dd614`。使用独立提交构建，只将本次日记 AI 类和专用审计日志级别覆盖到生产原包，保留线上其他类；新包 SHA-256 为 `fbce1f62a3d85edb1d6e526efd177b63d5dbe14ce68185d68d7f3017d7dbe40c`。
- 生产后端已通过安全脚本发布为 `fixes-743-20261022-100521`，旧包位于该 release 的 `backup/blog-backend.jar`。服务 active，文章流与模块接口为 200，日记页为 200，未授权日记 API 为 401；发布后五分钟服务日志没有 ERROR/Exception。
- 前端失败原因文案未发布。线上前端构建与干净提交及当前工作区构建均不一致，直接替换会覆盖既有页面改动；新错误代码在现有界面会显示通用提示，后端仍保存具体原因。需取得匹配线上版本的前端源码再单独发布。
- 受控 HTTP 回归在本机与 WSL 均受测试环境网络故障阻断；真实模型的本人问答与复盘验收尚未执行，不能宣称 `QUOTE_MISMATCH` 已经彻底解决。
