# Implementation checkpoint

## Scope and baseline

Started from clean HEAD `3c9ea5c26` on `zhengluyu_feat_diary`. Earlier login,
diary diagnostics and preview fixes were already committed. No production
configuration, credentials, diary records or object-storage policies are read or changed.
Existing backend module, metadata, diary security and article cover suites passed
before implementation. Existing frontend module and safe-content checks passed.

## Contracts

API root `/knowledge`; `/view/capabilities` contains capability booleans and fixed
limits only. Other `/view/*` endpoints require current module permission;
`/manage/*` additionally requires the configured owner id and an active account.
All data responses are private/no-store, noindex and nosniff. No body is returned
by entry list responses. Source URLs are HTTP/HTTPS metadata; never fetched.

POST `/manage/entries`: mutationId, entryId, expectedRevision, content.
Content contains kind, title, body, sourceUrl, categoryId and tagIds. Kinds are
NOTE, DOCUMENT, CONCEPT; existing kinds cannot change. New entries use revision
zero; mutations compare expected revision and atomically record immutable history
plus the acknowledgement. Identical retry returns the same acknowledgement;
same mutation with different content or stale revision returns 409.

POST `/view/entries/search`: kind/categoryId/tagId, updatedFrom/updatedTo,
literal keyword, page/pageSize, deleted. Deleted entries require manage access.
GET detail, label CRUD, paginated history and delete/restore/recover operations
use the same authorization and owner scope. No operation publishes articles.

Limits: 200 code points/title; 2 MiB UTF-8/body; 20 unique tags; 80 code
points/label; query 1–100 code points; pages default20/max50. Planned PDF20MiB,
UTF-8 text5MiB, extracted text2MiB, PDF300pages, extraction60sec,
temporary storage256MiB, retained file versions1GiB. File endpoints remain
unavailable until the private adapter and parsing pipeline are verified.

Processing states: EMPTY, QUEUED, RUNNING, READY, FAILED, NEEDS_OCR, CANCELED,
RETRY_WAIT. Export states: PREPARED, CREATED, RETRYABLE, REJECTED, DELETED.
These state declarations are not a claim that their workflows are implemented.

## Access matrix

| Identity | Read current content | Mutate/manage | File processing/export |
| --- | --- | --- | --- |
| Configured active owner with module access | Yes | Yes | Only when separately available |
| Other active ADMIN | Existing module policy | No | No |
| Active authorized role | Yes | No | Read-only downloads when implemented |
| Anonymous | Only explicit public module | No | Same read gate when implemented |
| Disabled account or revoked restricted role | No | No | No |
| Missing knowledge policy | No, including ADMIN | No | No |

## Graph evidence and remaining boundary

GitNexus refresh produced 19,925 nodes / 44,417 edges. Resident MCP queries still
report one-commit staleness and some corrupt symbol metadata. No zero-caller
result is treated as proof of no impact. Source confirms new store/model/advice
references are confined to the knowledge package. Module service upstream impact
MEDIUM:14 callers/9 direct. Module validation LOW:8/1 direct; route mapping
LOW:10/3 direct. File-level route impact LOW:8/1 direct. Header/admin integration
has dynamic Vue references: verified actual template imports and save handlers.
Deployment script has no resolved callers; source confirms invocation by its
isolated regression harness. Do not mark full graph verification complete until
the resident index inconsistency is resolved and changes are analyzed.

## Verified locally

`python scripts/knowledge/verify-postgres.py` creates/removes only a labeled,
disposable PostgreSQL container on localhost:15433. Verified migration rollback,
repeat execution, drift refusal; six real PostgreSQL tests cover idempotency,
optimistic conflicts, transaction rollback, history recovery, soft deletion,
pooled owner-context reset, denied diary schema access, literal wildcard search,
metadata-only listing and label referential protection.

Additional verification: six knowledge boundary/configuration tests and one
actual HTTP security-chain test passed. Existing module policy suite passed
17 tests. Frontend module compatibility passed four tests; workspace DOM tests
passed three scenarios (uncertain-save retry, reader-only UI, revocation/late
response). Both frontend production builds passed and blog initial assets remain
JS130.0KiB/CSS9.6KiB gzip. Release-script regressions passed12 cases inside a
network-disabled disposable Linux container. Windows Git Bash run failed three
frontend probe cases due to MSYS argument conversion; no production script
behavior was changed to accommodate that host-specific harness issue.

Whole-blog vue-tsc remains unsuccessful with diagnostics in existing music and
other components; no diagnostics were reported for Knowledge/knowledge-api.
This is not a clean global type-check claim. OpenSpec strict validation passed.
GitNexus detect_changes observed12 tracked changed files, but did not include the
new untracked implementation. Its LOW result is not a complete pre-commit check;
the pending graph-validation task remains unchecked. Nothing is staged, committed
or deployed by this apply checkpoint.

Remaining: file storage/parser/queue, explicit expired-trash cleanup,
relationships/graph, draft export, broad acceptance,
performance/backup tests and separately authorized production rollout.
