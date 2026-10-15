## MODIFIED Requirements

### Requirement: Upload validated images to object storage
The system SHALL accept a multipart image that matches the `UploadEnum` directory, allowed extensions, actual media content, decoding limits and size limit, store it in Aliyun OSS, and return a publicly reachable HTTPS URL. The URL MUST use `oss.domain` when configured; otherwise it MUST use `https://{bucket-name}.{endpoint}/{objectKey}`. Stored Content-Type MUST be derived from server validation, not trusted client metadata. Existing supported video purposes SHALL use separate media validation rather than image-only decoding.

#### Scenario: Successful article cover upload
- **WHEN** an authenticated publisher uploads a valid PNG under 0.3 MB as an article cover
- **THEN** the object is stored under `article/articleCover/` with a generated unique name
- **AND** the response data is an HTTPS URL that retrieves that object without additional auth headers

#### Scenario: Reject oversized or wrong type
- **WHEN** a caller uploads a file that exceeds the enum size limit or whose extension is not in the allowed list
- **THEN** the system MUST NOT put an object to OSS
- **AND** the caller receives a file-upload failure (empty file, size, or type error)

#### Scenario: File extension disguises active content
- **WHEN** a file claims to be an image but contains HTML, disallowed SVG, mismatching content or exceeds decoding limits
- **THEN** the system SHALL reject it before storage or deletion of any previous object

#### Scenario: Existing video purpose receives a valid video
- **WHEN** a permitted video upload passes its purpose-specific size and media validation
- **THEN** the existing video upload capability SHALL remain available

### Requirement: Delete and replace stored objects
The system SHALL delete OSS objects only after business permission, ownership or server-owned record, purpose and reference checks. Keys SHALL be derived from trusted records or exact configured HTTPS storage origins, never arbitrary client paths or foreign hosts. Listing SHALL return keys scoped to the authorized purpose. Temporary article cover deletion SHALL require the current publisher's valid server-side upload record and absence of all persisted article references, coordinated with reference creation to prevent races.

#### Scenario: Replace webmaster avatar
- **WHEN** an authorized webmaster uploads a validated new avatar
- **THEN** replacement SHALL affect only the authorized `websiteInfo/avatar/` objects
- **AND** the new object URL is persisted on `sys_website_info`

#### Scenario: Delete banner by stored URL
- **WHEN** an authorized admin deletes a banner whose persisted path is a full configured OSS URL
- **THEN** the corresponding object under `banners/` is removed from OSS if it exists
- **AND** client-supplied replacement paths SHALL NOT expand the deletion scope

#### Scenario: Delete article cover from full URL
- **WHEN** a publisher requests deletion of their own unreferenced temporary cover using its returned full URL
- **THEN** the backend SHALL verify the upload record and safely map the URL to its object key
- **AND** it MUST NOT assume the URL contains the Bucket name as a path segment (MinIO style)

#### Scenario: Unrelated or referenced object is requested
- **WHEN** the supplied value identifies another user's cover, a banner, an avatar, a referenced cover, a foreign origin, a raw key or a path traversal
- **THEN** deletion SHALL be rejected before any OSS deletion request

#### Scenario: Reference creation races with deletion
- **WHEN** article save and temporary cover deletion concurrently target the same object
- **THEN** the system SHALL prevent a persisted article reference to an object removed by that deletion

#### Scenario: Upload record expires or object storage fails
- **WHEN** temporary ownership cannot be established or OSS reports a failure
- **THEN** the API SHALL fail safely and MUST NOT falsely report successful deletion

### Requirement: Existing HTTP upload APIs unchanged
Existing upload HTTP routes SHALL keep their paths, multipart fields, auth headers, permission requirements and URL-string response data. Article cover deletion SHALL migrate from GET to a non-GET method with server-side scope checks; the previous GET route MUST NOT mutate storage. Other deletion routes SHALL retain their existing contracts while enforcing business authorization.

#### Scenario: Front-end keeps current upload calls
- **WHEN** the admin client posts to `/article/upload/articleCover` or `/user/auth/upload/avatar`
- **THEN** the request contract (multipart field, auth header) remains the same
- **AND** `data` is the validated OSS URL string

#### Scenario: Old client requests GET cover deletion
- **WHEN** a client invokes the legacy GET deletion method
- **THEN** the backend SHALL reject the mutation without deleting an object
- **AND** the updated frontend SHALL use the authenticated non-GET contract
