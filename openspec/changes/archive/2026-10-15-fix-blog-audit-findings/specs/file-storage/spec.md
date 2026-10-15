## MODIFIED Requirements

### Requirement: Delete and replace stored objects
The system SHALL delete OSS objects by validated object key and operation-specific authorization. A stored public URL MUST be parsed against explicitly trusted storage origins and its full purpose-specific path; arbitrary host stripping, raw caller keys, userinfo, query/fragment and ambiguous paths MUST NOT authorize deletion. Directory listing SHALL return keys under its given prefix but MUST NOT authorize bulk deletion during webmaster image replacement. Replacement SHALL validate and upload a unique new object, commit its database reference, and only then clean the exact obsolete object when no longer referenced.

#### Scenario: Replace webmaster avatar
- **WHEN** a webmaster uploads a valid new avatar
- **THEN** the new unique object is uploaded and its URL is committed on `sys_website_info` before the exact old object is cleaned
- **AND** unrelated objects under `websiteInfo/avatar/` remain intact

#### Scenario: Delete banner by stored URL
- **WHEN** an authorized admin deletes a banner whose stored `path` is a full trusted OSS URL
- **THEN** the corresponding object under `banners/` is removed if it exists
- **AND** a caller-controlled foreign URL or out-of-purpose key cannot select another object

#### Scenario: Delete article cover from full URL
- **WHEN** an authorized publisher cancels their own valid, unreferenced temporary cover using its full public URL
- **THEN** the backend validates its ownership and lifecycle, maps the full URL to the exact object key and deletes only that object
- **AND** it does not assume the URL contains the Bucket name as a path segment

#### Scenario: New image validation or persistence fails
- **WHEN** an avatar or website background upload fails validation, object upload or database reference persistence
- **THEN** the existing reference and old object remain usable
- **AND** any successfully uploaded but unreferenced new object is cleaned safely or recorded for retry if cleanup fails

#### Scenario: Concurrent replacements and delayed cleanup
- **WHEN** concurrent image replacements conflict or deletion of the obsolete object fails after commit
- **THEN** a losing replacement cannot overwrite the winner or delete a currently referenced object
- **AND** cleanup failure is recorded for retry without falsely reporting that the committed database reference was rolled back

### Requirement: Existing HTTP upload APIs unchanged
Existing upload HTTP routes SHALL keep their paths, multipart fields, authentication and permission contracts and URL-string response shape. Article cover cancellation SHALL change from GET to `DELETE /article/delete/articleCover`, preserving the `articleCoverUrl` query field and publisher permission. The old GET route MUST reject mutation with method-not-supported behavior. Other upload and delete routes SHALL retain their existing methods and permissions while applying the stricter storage checks.

#### Scenario: Front-end keeps current upload calls
- **WHEN** the admin client posts to `/article/upload/articleCover` or `/user/auth/upload/avatar`
- **THEN** the multipart field and auth header contract remain the same
- **AND** `data` is the new OSS URL string

#### Scenario: Coordinated cover cancellation client
- **WHEN** the updated admin cancels a newly uploaded temporary cover
- **THEN** it sends DELETE with the existing `articleCoverUrl` field
- **AND** it presents storage or authorization failure rather than reporting deletion success

#### Scenario: Legacy GET cannot delete
- **WHEN** a client calls GET on the old article-cover cancellation route
- **THEN** the backend rejects the method without deleting objects or changing lifecycle records

## ADDED Requirements

### Requirement: Temporary covers have owned and coordinated lifecycles
New temporary cover uploads SHALL register owner, canonical key, purpose, state and a 24-hour authorization lifetime in the existing Redis boundary before returning success. Cancellation MUST require publisher permission, matching owner and purpose, valid state and no persisted cover or body reference. Standard article reference writes and cancellation MUST coordinate atomic claims so an object cannot become referenced and deleted concurrently. Unknown, expired or unresolved lifecycle state and registry failure MUST fail closed. Historical referenced media SHALL remain viewable and editable without being implicitly claimed for cancellation.

#### Scenario: Registration fails after upload
- **WHEN** the object uploads successfully but its ownership registry cannot be written
- **THEN** the upload is not announced as a usable authorized temporary cover
- **AND** only the newly created unreferenced object is considered for controlled cleanup

#### Scenario: Cross-owner or out-of-purpose cancellation
- **WHEN** a publisher tries to cancel another publisher's cover, an avatar, a video, a raw key or an untrusted-origin URL
- **THEN** cancellation fails without deleting any object

#### Scenario: Referenced and legacy covers cannot be cancelled
- **WHEN** cancellation targets an object referenced by an article cover or body, or a historical object without temporary ownership proof
- **THEN** cancellation is rejected and the object remains available
- **AND** existing historical article references can still be displayed and retained in edits

#### Scenario: Publishing races with cancellation
- **WHEN** article publication and cancellation attempt to claim the same temporary cover concurrently
- **THEN** at most one lifecycle operation proceeds
- **AND** publication cannot commit a deleted cover and cancellation cannot delete a committed reference

#### Scenario: Failed or interrupted lifecycle operation
- **WHEN** storage deletion fails, publication rolls back, a process stops after claiming, or a claim expires before its outcome is known
- **THEN** failures are explicit and unconfirmed state is not treated as proof of an unused object
- **AND** a retry proceeds only after ownership, references and state are safely revalidated

### Requirement: Photo deletion follows authoritative stored records
Photo deletion SHALL derive the exact full `photoAlbum/` key from the server-stored trusted URL and use the record's actual type and parent relationship. Caller hints MUST NOT override these facts or bypass nonempty-album checks. The backend MUST confirm object deletion or verified absence before removing a photo row. Invalid records, keys and storage failures MUST return honest failures; database deletion failure after object cleanup SHALL preserve a retryable record and MUST NOT be reported as complete success.

#### Scenario: Nested and renamed albums
- **WHEN** a photo belongs to a nested album or an album renamed since upload
- **THEN** deletion uses the full key saved with the photo rather than reconstructing a path from its current album name
- **AND** only that photo's object and record are removed

#### Scenario: Caller supplies a false album type
- **WHEN** the caller labels a nonempty album as a photo or supplies a conflicting parent
- **THEN** the backend checks the actual record and rejects the invalid deletion
- **AND** descendants remain intact

#### Scenario: Object storage is unavailable
- **WHEN** exact-object cleanup fails because of OSS or network failure
- **THEN** the photo database record remains and the response reports failure

#### Scenario: Verified missing object and database failure
- **WHEN** an authorized photo key is confirmed absent or deleted but database removal fails
- **THEN** the request reports the database failure and retains the record for a verified retry
- **AND** verified object absence alone does not prevent an otherwise valid retry from completing
