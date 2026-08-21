## 1. Dependencies and configuration

- [x] 1.1 In `blog-backend/pom.xml`, remove `io.minio:minio` and add `com.aliyun.oss:aliyun-sdk-oss` (3.17+)
- [x] 1.2 Replace `application.yml` commented `minio.*` block with an `oss.*` template (`endpoint`, `access-key`, `secret-key`, `bucket-name`, optional `domain`) and note that real values belong in gitignored `application-dev.yml`
- [x] 1.3 Create local `src/main/resources/application-dev.yml` (or document placeholders) with `oss.*` plus existing required JWT/DB/Redis/RabbitMQ/Mail keys so the app can start; do not commit secrets

## 2. Storage abstraction and OSS client

- [x] 2.1 Add `OssProperties` (`@ConfigurationProperties(prefix = "oss")`) and `OssConfig` that builds an `OSS` client bean with shutdown on destroy
- [x] 2.2 Add `FileStorageService` interface: put stream, delete one key, delete batch keys, list keys by prefix, exists
- [x] 2.3 Implement `AliyunOssFileStorage` with official SDK against configured bucket
- [x] 2.4 Delete `MinioConfig.java`

## 3. FileUploadUtils and callers

- [x] 3.1 Rewrite `FileUploadUtils` to keep validation/`UploadEnum` naming, delegate IO to `FileStorageService`, build public URLs per design (domain or default Bucket host)
- [x] 3.2 Add `toObjectKey(String urlOrKey)` and use it for delete-by-URL paths; remove MinIO exception types from public upload method signatures where they force MinIO imports
- [x] 3.3 Fix `ArticleServiceImpl.deleteArticleCover` to use `toObjectKey` instead of `indexOf(bucketName)`; remove `@Value("${minio.bucketName}")`
- [x] 3.4 Grep for `minio` / `MinioClient` / `io.minio` under `blog-backend` and clear remaining references (comments in PhotoServiceImpl etc.)

## 4. Verification

- [x] 4.1 Compile backend (`mvn -q -DskipTests compile` or project equivalent)
- [ ] 4.2 With valid OSS credentials, upload one article cover and open the returned URL in a browser (public read)
- [ ] 4.3 Verify replace/delete: change website avatar (list+delete old) and delete a banner; confirm objects disappear in OSS console
- [x] 4.4 Confirm upload HTTP paths and auth headers unchanged for admin clients
