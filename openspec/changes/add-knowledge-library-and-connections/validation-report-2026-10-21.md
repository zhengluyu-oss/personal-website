# Knowledge library validation — 2026-10-21

This is a factual test record, not a release certificate. The repository has
other unfinished changes in the same working tree; the knowledge production
release used an isolated source package.

| Check | Result | Evidence / limit |
| --- | --- | --- |
| Blog production build and performance budget | PASS | `pnpm build`; initial JS gzip 130.0 KiB, CSS gzip 9.7 KiB. Existing Sass and CSS minifier warnings remain. |
| Blog utility regression | PASS | `pnpm test`: 47/47. |
| Admin typecheck and production build | PASS | `pnpm build` exit 0. Existing third-party minifier warnings remain. |
| Knowledge DOM behavior | PASS | `node scripts/knowledge/workspace.test.mjs`: 6/6; `node scripts/knowledge/draft.test.mjs`: 2/2. |
| PostgreSQL migrations, RLS, job/export tests | PASS | `python scripts/knowledge/verify-postgres.py`; repeat migration, transactional migration, drift rejection, PG tests. Includes three parse retries and cross-DB uncertain result recovery. |
| MySQL blog mapping tests | PASS | `python scripts/knowledge/verify-mysql.py`; MySQL transaction, retry mapping, deleted draft guard, restricted policy repeat. |
| Knowledge permission/security tests | PASS | `mvn -q '-Dtest=KnowledgeBoundaryTest,KnowledgeSecurityTest,KnowledgeProcessingQueueTest' test`. |
| Real RabbitMQ queue compatibility/dead letter | PASS | Local isolated `rabbitmq:management` container, `KNOWLEDGE_TEST_RABBIT_URI` set; three `KnowledgeProcessingQueueTest` cases passed, including predeclared v1 and v2 → dead routing. Test container stopped. |
| Full backend suite | **NOT PASSING** | `mvn -q test` fails in pre-existing diary AI tests because Java `HttpServer` cannot establish a Windows loopback `PipeImpl` connection. `DiaryAiResponseTest` repeated alone, including `java.net.preferIPv4Stack=true`, with the same infrastructure error. This is not evidence that those assertions pass. |
| OpenSpec | PASS | `openspec validate add-knowledge-library-and-connections --strict`. |
| GitNexus graph | PASS WITH CAUTION | `analyze --force` completed: 21,593 nodes, 47,452 edges, 1,280 flows. Process coverage is truncated. `detect-changes --scope all` reports HIGH across the mixed working tree (50 files, 229 symbols, 11 flows); this does not clear a repository-wide commit. |
| Chinese keyword SQL sample | PASS WITH SCOPE LIMIT | `python scripts/knowledge/benchmark-search.py` generated 1,000 entries and 20,000 current file segments in an isolated PostgreSQL container limited to 512 MiB and one CPU. Verified UTF-8 Chinese bytes. Thirty warmed RLS-filtered count queries: P50 26.46 ms, P95 33.29 ms, max 34.03 ms; 200 matching entries. Plan used sequential scans on 1,000 entries/20,000 segments, hashed join to READY jobs, 322 shared buffers hit. This measures database search matching, not complete HTTP/Java response latency. |
| Scanned PDF parser resource sample | PASS WITH SCOPE LIMIT | `python scripts/knowledge/benchmark-pdf.py` generated a 300-page, 19.83 MiB scanned PDF (20,791,840 bytes) locally. Credential-free child with 256 MiB heap and 64 MiB direct-memory caps classified it `NEEDS_OCR`: 3.59 s wall time, 7.25 CPU-seconds, 144.81 MiB sampled peak RSS. With the page limit set to 299, it classified `PAGE_LIMIT`: 2.07 s, 5.88 CPU-seconds, 148.80 MiB peak RSS. Temporary input file was 19.83 MiB. This was a Windows workstation test, not a production 4 GiB concurrency, DB connection or site-availability measurement. |
| Metadata backup and isolated restore | PASS WITH SCOPE LIMIT | `scripts/knowledge/backup-production.sh` passes `bash -n`; `python scripts/knowledge/verify-backup-restore.py` restored PG custom dump (40,369 bytes) and MySQL mapping SQL (2,476 bytes) into separate disposable databases. Verified 2 history revisions, 1 relation, 1 export mapping, 1 private object manifest row, 12 forced-RLS tables, owner-context filtering and denial of the diary schema. Production encryption script has not itself been run. Manifest does not back up OSS object bytes. |
| Production smoke after isolated knowledge releases | PASS | `zhengluyu-blog.service` active and `/knowledge` HTTP 200 on the follow-up check; `/`, `/diary`, `/knowledge`, and knowledge capabilities returned HTTP 200 at release time. New v2, dead, old v1, and email queues existed at zero messages after the 15:52 CST backend release; no immediate error-level service journal entries. Read-only host check showed 2,086 MiB available memory and 22 GiB free disk. This is only a smoke check. |

Still required before declaring full acceptance: 360 px and desktop authenticated
visual checks; owner/reader/anonymous/revoked/disabled/open-policy end-to-end
checks; isolated full-stack failure injection; HTTP/Java search latency and
production 4 GiB concurrency/DB connection and site-availability measurement;
owner-signed-in production upload, parse, search,
relation, draft, reader authorization and rollback checks.
