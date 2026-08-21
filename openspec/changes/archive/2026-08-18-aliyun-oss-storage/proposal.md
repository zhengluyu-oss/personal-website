## Why

本机开发不再部署 MinIO，图片仍需可公网访问的对象存储。当前上传、删除、存在性检查全部绑死 `MinioClient`，后端启动也强依赖 `minio.*`，无法对接阿里云 OSS。需要把文件存储从 MinIO 换成 OSS，并让业务层继续走现有上传入口。

## What Changes

- 新增对象存储抽象，阿里云 OSS 作为唯一实现；业务仍通过现有 `FileUploadUtils` 方法上传/删除（封面、插图、头像、Banner、相册、站点图）。
- 配置由 `minio.endpoint/accessKey/secretKey/bucketName` 改为 `oss.*`（endpoint、access-key、secret-key、bucket-name、可选 `domain` 自定义访问域名）。
- **BREAKING**：Maven 移除 `io.minio`；删除 `MinioConfig`；返回的文件 URL 不再是 `minio.endpoint/bucket/objectKey`，改为 OSS 公网 URL（默认 Bucket 域名或自定义 `oss.domain`）。
- 库中已有 MinIO URL **不迁移**（本机开发可接受旧链接失效）。
- 不改前端上传调用路径；不改 RabbitMQ/邮件/JWT；不在本次引入存储类型热切换（仅预留接口，实现只有 OSS）。

## Capabilities

### New Capabilities

- `file-storage`: 后端对象存储：按目录与校验规则上传图片、返回可访问 URL、按对象键删除/判断存在；配置与密钥不入库、不进 Git。

### Modified Capabilities

- （无。`openspec/specs/` 目前没有已落地的能力规格。）

## Impact

- 代码：`MinioConfig.java` 删除；`FileUploadUtils.java` 改为依赖存储抽象；`ArticleServiceImpl` 中 `${minio.bucketName}` 解析封面删除路径需改为不依赖 MinIO URL 形态。
- 调用方（方法签名尽量保持）：`ArticleServiceImpl`、`PhotoServiceImpl`、`BannersServiceImpl`、`WebsiteInfoServiceImpl`、`UserServiceImpl`。
- 依赖：`pom.xml` 增加 `aliyun-sdk-oss`，移除 `io.minio:minio`。
- 配置：`application.yml` 模板与本地 `application-dev.yml`（gitignore）使用 `oss.*`。
- 运维：Bucket 需公共读或等价读策略，否则前台图片无法直接打开；密钥仅本地配置。
- API：HTTP 路径不变，仅响应里的图片 URL 主机不同。
