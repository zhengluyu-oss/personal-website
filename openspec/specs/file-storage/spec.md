# file-storage Specification

## Purpose
Backend image object storage via Aliyun OSS: validated upload, public URL, delete/list by object key, `oss.*` configuration without MinIO.
## Requirements
### Requirement: Upload validated images to object storage
The system SHALL accept a multipart image that matches the `UploadEnum` directory, allowed extensions, and size limit, store it in Aliyun OSS, and return a publicly reachable HTTPS URL. The URL MUST use `oss.domain` when configured; otherwise it MUST use `https://{bucket-name}.{endpoint}/{objectKey}`.

#### Scenario: Successful article cover upload
- **WHEN** an authenticated publisher uploads a PNG under 0.3 MB as an article cover
- **THEN** the object is stored under `article/articleCover/` with a generated unique name
- **AND** the response data is an HTTPS URL that retrieves that object without additional auth headers

#### Scenario: Reject oversized or wrong type
- **WHEN** a caller uploads a file that exceeds the enum size limit or whose extension is not in the allowed list
- **THEN** the system MUST NOT put an object to OSS
- **AND** the caller receives a file-upload failure (empty file, size, or type error)

### Requirement: Delete and replace stored objects
The system SHALL delete OSS objects by object key. When the caller only has a previously returned URL, the system MUST derive the object key by stripping the configured public base URL (custom domain or default Bucket host). Directory listing MUST return object keys under the given prefix so replace-upload flows can delete old files first.

#### Scenario: Replace webmaster avatar
- **WHEN** a webmaster uploads a new avatar
- **THEN** existing objects under `websiteInfo/avatar/` are deleted
- **AND** the new object URL is persisted on `sys_website_info`

#### Scenario: Delete banner by stored URL
- **WHEN** an admin deletes a banner whose `path` is a full OSS URL
- **THEN** the corresponding object under `banners/` is removed from OSS if it exists

#### Scenario: Delete article cover from full URL
- **WHEN** the backend is asked to delete a cover given the full public URL (not a raw object key)
- **THEN** it MUST map that URL to the object key and delete that object
- **AND** it MUST NOT assume the URL contains the Bucket name as a path segment (MinIO style)

### Requirement: OSS configuration and secrets
The backend MUST read OSS connection settings from configuration (`oss.endpoint`, `oss.access-key`, `oss.secret-key`, `oss.bucket-name`, optional `oss.domain`). Secrets MUST NOT be committed to Git. Application startup MUST fail fast if required OSS settings are missing. The system MUST NOT require a MinIO endpoint or `MinioClient` bean.

#### Scenario: Local profile uses gitignored credentials
- **WHEN** a developer supplies `application-dev.yml` (gitignored) with valid `oss.*` values
- **THEN** the application starts and can upload to the configured Bucket

#### Scenario: MinIO settings are absent
- **WHEN** `minio.*` is not present in configuration
- **THEN** the application still starts
- **AND** no code path instantiates `io.minio.MinioClient`

### Requirement: Existing HTTP upload APIs unchanged
Existing upload and delete HTTP routes SHALL keep their paths, methods, and permission annotations. Only the stored and returned file URL host/path shape may change.

#### Scenario: Front-end keeps current upload calls
- **WHEN** the admin client posts to `/article/upload/articleCover` or `/user/auth/upload/avatar`
- **THEN** the request contract (multipart field, auth header) remains the same
- **AND** `data` is the new OSS URL string

