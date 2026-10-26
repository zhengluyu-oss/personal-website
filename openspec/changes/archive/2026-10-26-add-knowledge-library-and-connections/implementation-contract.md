# Implementation checkpoint

## Scope and baseline

Started from clean HEAD `3c9ea5c26` on `zhengluyu_feat_diary`. Earlier login,
diary diagnostics and preview fixes were already committed. At that initial
checkpoint, no production settings or data were changed. The later, separately
authorized production work is recorded below.
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

2026-10-20 continuation: the owner subsequently chose to reuse the existing OSS
Bucket and server credential. Knowledge objects use only `knowledge/private/`,
an explicit private object ACL on the upload request, and server-authorized
downloads. The existing public media flow was not modified. Before enablement,
a temporary object was confirmed private and returned 403 to anonymous requests
at both the OSS origin and configured media domain; the object was deleted.
This shared credential has broader scope than a dedicated knowledge key.

The owner-only explicit trash purge now deletes entries older than 30 days
without file versions, including revisions and mutation receipts. Any entry
with file-version records is held for future private-object cleanup. The
isolated PostgreSQL test revealed that the runtime role lacked DELETE on the
revision table; migration grants were corrected before production use.
The isolated PostgreSQL and MySQL suites, three workspace DOM scenarios,
blog production build, and strict OpenSpec validation pass. A late-response
guard was added to draft-source history loading. The unrelated visual-system
working-tree changes are outside this knowledge implementation.

The private-file track now includes bounded upload/download, object ACL probe,
PDFBox 3.0.8 Java parsing in a short-lived credential-free child process,
RabbitMQ job identifiers with PostgreSQL outbox and fence, three-attempt retry,
owner stop/retry, and delayed file cleanup. Synthetic production data traversed
the deployed OSS → RabbitMQ → parser → PostgreSQL path, reaching READY with a
searchable segment; the test rows and object were then removed. An encrypted
backup of the preflight PostgreSQL database and affected MySQL module tables was
retained on the server. The knowledge runtime PostgreSQL role is restricted to
the new schema and cannot use the diary schema. The module and file processing
are enabled in production; the module policy remains restricted, not public.
Live site checks returned 200 for the blog and diary pages and 401 for an
anonymous knowledge-file download. The owner role was confirmed active ADMIN.

Remaining OpenSpec tasks include orphan-object inventory, dead-letter queue,
broader browser acceptance and resource/recovery exercises, graph analysis,
and the separate draft/graph UI work. Successful synthetic processing is not
a claim that all 42 OpenSpec tasks are complete.

2026-10-21 continuation: the actual Spring security filter-chain test now
checks revoked readers against entry, file metadata/download, search and
graph read routes; an ADMIN who is not the configured owner remains unable
to mutate even after the module is made public, and a disabled owner loses
management access. `mvn -q -Dtest=KnowledgeSecurityTest test` passed. This
completes the view/manage boundary matrix in task 2.5 without claiming the
separate full end-to-end authorization exercise in task 8.2.

The file lifecycle now inventories only the `knowledge/private/objects/`
prefix and reclaims objects older than 24 hours only after a PostgreSQL lookup
confirms they have no live registration. Listing is paginated and bounded;
failed listing or database access stops reclamation, and individual OSS delete
failures wait for the next pass. Unit tests cover prefix/age and pagination;
the isolated PostgreSQL test covers registered versus cleaned keys. This does
not complete task 4.2: interrupted-upload acceptance and temporary-file
recovery still need direct verification.

The backend-only release `fixes-742-20261021-100735` passed the release
resource/secret gate and was deployed with a retained rollback jar. The
service and public site, diary and knowledge capability routes returned
healthy responses; no service errors appeared in the immediate journal check.
The old-object scanner was not force-run against production objects as a
test, so its real deletion behavior remains part of later acceptance.

Search result excerpts now present the matching PDF page or text line range
both in the result list and the selected detail, as ordinary Vue text. A DOM
test using an HTML-looking excerpt confirmed no image element was created;
all four workspace tests and the blog production build passed. The isolated
blog build, containing only the knowledge workspace change, passed the
artifact gate and deployed through the release script. `/knowledge` and
knowledge capabilities returned HTTP 200; the backend stayed active with no
immediate error-level journal entries. This completes the search UI task,
but owner browser acceptance of real content remains open.

PDFBox 3.0.8 was reconfirmed against the Apache project's current download
and security pages (https://pdfbox.apache.org/download.html and
https://pdfbox.apache.org/security.html). Parser tests passed for Chinese text
with an embedded font, malformed and encrypted PDFs, blank/scanned output,
over-page input, and UTF-8 line locations. This completes the parser-format
coverage in task 4.4; process timeout/OOM/startup fault injection remains in
task 4.5.

The current READY file version now exposes a paginated, authorized segment
view with PDF page or text line location. Old versions return 404 and an
unready current version returns 409 in the isolated PostgreSQL tests; the
deployed endpoint returns 401 without a login. The workspace can inspect
extracted text, and the owner-only draft editor can select a fixed segment
as one of up to ten manually curated sources. The PG export test confirmed
that preparing a snapshot before READY fails, the READY segment text is
snapshotted, and a replaced file cannot be selected anew. This completed
tasks 4.9 and 7.1. The backend and blog build passed their isolated release
gates and deployed together; public home, knowledge, diary and capability
routes returned HTTP 200, and the service remained active with no immediate
error-level journal entries. Full signed-in owner acceptance remains open.

The GitNexus index was refreshed to the current `ce5feb7` checkout on
2026-10-21. Concept queries covered article publish, module access, OSS,
RabbitMQ, and PostgreSQL data-source flows. Current impact for the shared
`SiteModuleAccessService.canView` method is CRITICAL (57 upstream symbols);
this method was not edited. `KnowledgeStore.file` is HIGH (5 upstream
symbols), so the new segment route calls its existing current-version check
without modifying that method. `Workspace.load` is MEDIUM (8), while the
private-object class is LOW (2). Scheduler/listener entry points and dynamic
Vue template calls return UNKNOWN/no resolved callers; source annotations,
templates and HTTP tests were used instead of treating zero as safe. Task
1.1 remains open until all planned symbol edits and graph change checks are
fully accounted for.

Deletion now records `ENTRY_DELETED` only for unfinished parse jobs; restoring
the same current file atomically resets those jobs and adds an outbox event.
An owner-stopped job is left canceled. Isolated PostgreSQL tests passed for
replacement, deletion, restoration, stale fenced workers, expired-lease
recovery and duplicate completed-job delivery; no old result was searchable.
This completes task 4.8. The backend-only follow-up
`fixes-742-20261021-103305` passed the resource/secret gate and deployed
with rollback available. Home, knowledge, diary and capability routes all
returned HTTP 200; the service was active with no immediate error-level
journal entries.

The parser runner now distinguishes timeout, child out-of-memory, and
startup failure, tears down the child and reader before temp cleanup, and
reports retryable resource failure without exposing source text. Three
process-level tests passed. The isolated backend/blog release deployed at
2026-10-21 15:11 CST; service, home, diary, knowledge, and capabilities
health checks passed with no new error-level journal entries.

Queue migration keeps `knowledge.parse.v1` declared and consumed while new
outbox messages target `knowledge.parse.v2`. The new queue alone has an
isolated direct dead-letter exchange and durable dead queue. A real isolated
RabbitMQ broker test verified predeclared v1 compatibility and v2 poison
message dead-letter delivery; mock consumer tests verified terminal-state
dead lettering and legacy acknowledgement. PostgreSQL integration verified
exactly three retryable parse attempts before `FAILED`, while existing tests
cover owner stop/retry and lease fencing. Mail queue declarations are
untouched. This completes task 4.7. The isolated backend release deployed at
2026-10-21 15:52 CST; RabbitMQ lists v1, v2, dead, and email queues at zero
messages, the service is active, home/knowledge/capabilities return HTTP 200,
and no new error-level journal entries appeared immediately after release.

GitNexus `analyze --force` completed on 2026-10-21 with 21,593 nodes,
47,452 edges and 1,280 flows. Concept queries covered article publishing,
module authorization, OSS, RabbitMQ and PostgreSQL. Impact returned LOW for
KnowledgeStore and KnowledgePrivateObjects; Spring's scheduled and listener
entry points (KnowledgeProcessing and KnowledgeQueueConfiguration) returned
UNKNOWN, so their annotations and text callers were inspected directly.
The existing site-module `canView` overloads were MEDIUM (58 and 33
impacted respectively) and were not changed in this queue patch. The graph
reports truncated process coverage, so an absent flow was never treated as
proof of no callers. This completes task 1.1.

The graduation-demo/operations document at `docs/knowledge-library.md` now
contains the demo route, API boundary, logical ER diagram, processing and
cross-database export sequences, deployment rollback and the explicitly
non-AI scope. Strict OpenSpec validation passed. A full GitNexus
`detect-changes --scope all` was run, and its HIGH risk across the mixed
50-file working tree is preserved in the validation report rather than
treated as a clean commit gate. This completes documentation task 8.6;
any later commit requires a fresh full graph check of its actual scope.

The full build/regression record is in
`validation-report-2026-10-21.md`: blog and admin builds passed, blog tests
passed 47/47, isolated PG/MySQL and knowledge boundary tests passed, while
the full backend suite failed in unrelated diary AI HTTP fixtures because
this Windows host cannot establish Java loopback sockets. Repeating one
diary fixture alone reproduced the infrastructure failure. No 4 GiB
resource conclusion is claimed before task 8.3's parser measurement. This
completes the run-and-report requirement of task 8.4, not an all-green
backend acceptance.

Versioned production metadata backup script and isolated PG/MySQL restore
drill were added. The drill restored 2 history revisions, 1 relation, the
cross-database export mapping and one referenced private-object manifest
row; 12 tables retained forced RLS, runtime without owner saw zero entries,
and runtime could not read diary. The production script encrypts each dump
and manifest with age and records checksums, but has not itself been run;
OSS object bytes need separate Bucket protection. See the validation report.
This completes task 8.5's script and isolated drill without claiming a
full-Bucket recovery.

Cross-database draft reconciliation is now covered in the isolated PostgreSQL
and MySQL suites: the same export ID and digest are idempotent, a changed
digest is rejected, revoked article permission blocks creation, a deleted
article is not recreated, a transient MySQL failure leaves PG `RETRYABLE`,
and a lost PG writeback after a MySQL create recovers the same article ID on
retry. Both `verify-postgres.py` and `verify-mysql.py` passed. This completes
task 7.3 without exporting any private OSS address.

The article-draft DOM fixture confirmed an owner can select a fixed source,
fill category/tag/cover, review the manual body, confirm a private snapshot,
create an unpublished draft and follow the existing admin edit link. An
uncertain create response preserves the export ID and identical payload for
manual retry. Neither fixture rendered a private object address. The PG
snapshot test also proves deleting a source does not rewrite the prepared
snapshot. Both new DOM tests passed, completing task 7.4.

The knowledge upload boundary now has a dedicated Nginx `/api/knowledge/`
location with a 21 MiB request cap, no access logging or proxy cache, and
request streaming. This preserves the site's 100 MiB limit for blog video.
Spring multipart is 21 MB; the knowledge PDF business cap is 20 MiB and
text cap is 5 MiB. Unit tests verified interrupted input never reaches OSS
or PostgreSQL, a rejected database claim deletes its new private object,
and scheduled cleanup deletes only old knowledge-prefix objects with no
live PostgreSQL registration. The isolated blog release resource gate and
build passed; Nginx syntax, reload, `/knowledge` (200) and knowledge
capabilities (200) passed on production at 2026-10-21 10:47 CST. This
completes task 4.2; owner upload acceptance is still task 9.2.

Workspace DOM tests now also cover a late save acknowledgement after a
different entry is opened, an edit conflict retaining the text, route-leave
confirmation and `beforeunload` protection. All six tests passed. Mobile
visual verification remains part of task 3.5.

The bounded OSS orphan inventory now retains its continuation marker across
hourly scans; without this, a stable first 2,000 registered objects could
starve later orphan keys forever. A 21-page test verified that the next run
starts at page 21. The isolated backend release passed the artifact gate and
deployed at 2026-10-21 10:51 CST. The service remained active; home, diary,
knowledge and capabilities returned HTTP 200, with no immediate error-level
journal entries.
