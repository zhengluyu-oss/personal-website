## Context

后端所有图片上传集中在 `FileUploadUtils`（`MinioClient`：put / list / exists / delete），由文章、相册、Banner、站点信息、用户头像调用。配置模板是 `minio.endpoint/accessKey/secretKey/bucketName`，返回 URL 为 `{endpoint}/{bucket}/{objectKey}`。`ArticleServiceImpl.deleteArticleCover` 用 URL 里出现的 `bucketName` 截取对象路径，这是 MinIO 路径风格，不能套用 OSS 默认域名 `https://{bucket}.{endpoint}/{key}`。

约束：本机开发连阿里云 OSS；密钥进 gitignored 的 `application-dev.yml`；不迁库里旧 MinIO URL；HTTP 上传接口保持不变。调用方只依赖 `FileUploadUtils` 的现有方法，不宜大面积改 Service 签名。

## Goals / Non-Goals

**Goals:**

- 用阿里云 OSS 替换 MinIO 作为唯一对象存储实现。
- 用存储接口隔离 SDK，校验/命名仍留在 `FileUploadUtils`，业务调用点尽量不改。
- 从完整 URL 稳定解析 object key，修复封面删除对 MinIO URL 形态的假设。
- 配置改为 `oss.*`；去掉 `io.minio` 依赖与 `MinioConfig`。

**Non-Goals:**

- 不迁移已有数据库图片 URL。
- 不实现 MinIO / OSS 运行时双开或 `storage.type` 热切换（接口预留，本变更只有 OSS 实现）。
- 不改前端上传组件与路由。
- 不处理私有 Bucket + 签名 URL（默认公共读）。
- 不引入图片处理（缩略图、水印）或 CDN 刷新 API。

## Decisions

### 1. 抽象层 + 单一 OSS 实现（相对“直接改 Utils”和“S3 兼容 MinIO 客户端”）

- **选择：** `FileStorageService` 接口 + `AliyunOssFileStorage`；`FileUploadUtils` 继续做格式/大小校验、UUID 文件名、`UploadEnum` 目录拼接，IO 委托给接口。
- **备选 A：** 只改 `FileUploadUtils` 内部为 OSS SDK。更快，但删除/列举与 SDK 异常绑死工具类，下次换存储仍要改同一文件。
- **备选 B：** 继续用 `MinioClient` 打 OSS S3 兼容端点。改动最少，签名/删除兼容风险高，否决。
- **理由：** 业务面小、配置替换清晰，且满足“预留接口、现在只用 OSS”。

### 2. 官方 `aliyun-sdk-oss`，不用 Hutool OSS 封装

项目已有 `hutool-all`，但官方 SDK 文档与 ACL/端点行为更明确。版本锁定 3.x 稳定版（实现时选用当前可用的 3.17+）。

### 3. 配置模型

```yaml
oss:
  endpoint: oss-cn-hangzhou.aliyuncs.com   # 不含 https://
  access-key: ***
  secret-key: ***
  bucket-name: your-bucket
  domain: ""   # 可选，如 https://image.example.com ，无则用默认 Bucket 域名
```

使用 `@ConfigurationProperties(prefix = "oss")` 绑定，避免业务里散落 `@Value("${minio.*}")`。`application.yml` 只保留注释模板；真实值只写 `application-dev.yml`。

### 4. 公网 URL 与 ACL

- 上传不强制写对象 ACL；依赖 Bucket 公共读（或等价 Bucket Policy）。
- URL：`domain` 非空则 `{domain}/{objectKey}`，否则 `https://{bucket}.{endpoint}/{objectKey}`。
- **不**再拼 MinIO 形态 `{endpoint}/{bucket}/{key}`。

### 5. Object key 解析（修复封面删除）

新增 `FileUploadUtils.toObjectKey(String urlOrKey)`：

- 已是相对 key（不含 `://`）则规范化去前导 `/` 后返回。
- 否则去掉 public base（`oss.domain` 或 `https://{bucket}.{endpoint}`）得到 key。

`ArticleServiceImpl.deleteArticleCover` 改为对该 URL 调 `toObjectKey` 再 `deleteFiles`，禁止 `indexOf(bucketName)`。

`listFiles` 仍返回 **object key 列表**（与现网 `WebsiteInfoServiceImpl` 先 list 再 `deleteFiles` 一致）。`isFileExist(dir, fileName)` 用 OSS `doesObjectExist(bucket, dir+fileName)`，避免 list 全量前缀。

### 6. 包与类

| 类 | 职责 |
|---|---|
| `config.OssProperties` | 绑定 `oss.*` |
| `config.OssConfig` | 创建 `OSS` / `OSSClientBuilder` Bean |
| `storage.FileStorageService` | put、delete、deleteBatch、list、exists |
| `storage.AliyunOssFileStorage` | 官方 SDK 实现 |
| `utils.FileUploadUtils` | 校验 + 命名 + URL 组装 + 委托 |
| 删除 `config.MinioConfig` | |

`FileUploadUtils` 的三个 `upload(...)` 对外签名保持不变，去掉方法签名上的 MinIO 异常类型，统一 `FileUploadException` / 受检 `Exception` 与现状兼容（调用方已 catch Exception）。

### 7. 前端与 HTTP

不改 Controller 路径与 multipart 字段。响应 `data` 仍为字符串 URL。

## Risks / Trade-offs

- [本机每次上传走公网 OSS，延迟与流量] → 接受；开发 Bucket 可单独建并设生命周期清理。
- [Bucket 未公共读导致前台裂图] → 在 overview/配置注释中写明只读策略；上传后可用返回 URL 人工打开验证。
- [旧 MinIO URL 失效] → 明确不迁移；本机库可当新数据。
- [AccessKey 泄露] → 仅 gitignore 的 yml；禁止写进 `application.yml` 生效块。
- [OSS 客户端未 shutdown] → `OssConfig` 注册 destroy 方法 `shutdown`。
- [封面删除解析错误导致删错或删失败] → 集中 `toObjectKey` + 单测或手工用默认域名和自定义 domain 各测一次。

## Migration Plan

1. 改依赖与配置模板，落地抽象与 OSS 实现。
2. 开发者自建 OSS Bucket（公共读），填写 `application-dev.yml`。
3. 启动后端，后台上传一张封面，确认 URL 可浏览器打开；再测删除 Banner / 换头像。
4. 回滚：恢复 MinIO 依赖与旧 `FileUploadUtils`（本变更按一次替换设计；Git revert 即可）。无需数据回滚。

## Open Questions

- Bucket 地域、名称、是否自定义域名：由开发者写入本地配置，实现不写死杭州或特定 Bucket。
- 若后续生产要私有读 + 签名 URL，需另开变更，本次不做。
