# Security remediation verification

Change: `remediate-blog-security-audit`.
Baseline: `e4e676cae82f5f31eb1ef515d82bfbb11370d9bf`, branch `zhengluyu_fix_userAuthority`.
Tracked working tree was clean before implementation. Existing untracked media, scripts and tooling are outside the change.

## Safe test boundary

Use constructor-level unit tests, mocked repositories, mocked mail/OSS/queue clients and isolated Redis fixtures only. Do not start the application using private development configuration. No live mail, cloud deletion, database cleanup, deployment or credential rotation is authorized by local implementation.

| Finding | Synthetic regression fixture |
| --- | --- |
| 1 Preview XSS | Inert marker event handler, unsafe URI, SVG and ordinary Markdown |
| 2 Audit secrets | Fake password/code/token in nested DTO, response, exception and old queue event |
| 3 Email binding | Wrong password plus valid new-address code; mismatched provider and proof replay |
| 4 OAuth | Two independent browser secrets, cross-browser exchange, replay, expiry |
| 5 OSS deletion | Two publishers, referenced/unreferenced fake keys, mocked OSS failure and race |
| 6 Link approval | Scanner GET, unauthorized POST, concurrent confirmation, legacy timestamp |
| 7 Comment privacy | Private target, pending reply, cross-target references, later visibility change |
| 8 Packaging | Synthetic canary configuration excluded from both default and production JAR |
| 9 Dependencies | Locked runtime inventory, advisory reachability, backend/frontend regression |

## Impact evidence

- Repository bound to `personal-website` at the workspace path; MCP index commit matches baseline.
- `LogAspect.recordLog`: LOW in the graph, direct caller `LogAspect.log`.
- `LogAspect.log`: UNKNOWN because Spring pointcut dispatch is unresolved. Source confirms `@Around("pt()")` intercepts controller `@LogAnnotation` operations including register/reset and administration; success/failure/non-ResponseResult regressions required.
- `LogQueueListener.handlerSystemLog`: UNKNOWN because Rabbit listener dispatch is not a static caller. Source confirms `@RabbitListener(LOG_SYSTEM_QUEUE)` and the producer binding; changes affect system audit persistence, not login log persistence.

Further impact results and executed test outcomes must be appended as implementation proceeds. An unchecked task is not a verified fix, and local verification is not production deployment.

## First implementation batch (2026-10-02)

- `userFavorite` and `getBackFavoriteList` impact: LOW lower bounds at DI boundaries. Concrete controller consumers are favorite creation and both admin list/search routes. Target visibility is now checked and absent targets/users are handled safely.
- `viewFunc` and `sanitizeRenderedHtml`: UNKNOWN for Vue binding dispatch; text verification found collect modal click handlers and sanitizer props on article, experience and website-share views. The existing sanitizer is reused by the admin preview; SVG mutation elements and inline style/srcset are excluded. Safe Markdown DOM tests retain headings, tables, code and links.
- Backend targeted Maven tests: LogAspectTest (4), AuditDataProtectionTest (3), FavoriteVisibilityTest (3), all passed. No Spring application or real external services were started.
- Admin DOM tests: 3 passed using the actual installed MdPreview renderer, plus malicious SVG/scheme and plain-text fixtures.
- Historical inventory is structurally tested read-only SQL; it has NOT been run against a live database, and no historical cleanup has occurred.

## OAuth implementation and validation (2026-10-02)

- Login attempts now use separate browser-bound state cookies and single-use exchange codes. Wrong-browser attempts do not consume the legitimate browser's code. Existing frontend sessions are not silently overwritten by callback URLs.
- Pre-edit impact: OAuth controller entry points UNKNOWN (HTTP dispatch, checked route annotations); service `handleLogin` LOW with two provider callbacks; `exchangeCode` LOW with the exchange controller; frontend `thirdLogin` LOW with Header/Layout consumers. DI and browser dispatch remain static-analysis boundaries.
- Targeted backend suite passed 22 tests: AuditDataProtectionTest (3), LogAspectTest (4), FavoriteVisibilityTest (3), OauthBrowserBindingTest (4), OauthServiceImplExchangeTest (5), OauthControllerExchangeTest (2), SecurityHandlerSecondFactorTest (1).
- Both frontend production builds passed. Existing admin CSS warnings and frontend Sass/chunk-size warnings remain; successful builds are not a browser end-to-end test.
- OAuth concurrency and consume behavior currently use an atomic in-memory Redis double. Actual Redis Lua execution and real provider round trips have NOT been verified. Reauthentication purpose integration and complete storage-failure coverage are still pending, so tasks 4.1–4.4 remain unchecked.

## Graph refresh diagnostic

- Incremental refresh completed, but subsequent MCP queries returned missing method targets and malformed file metadata; CLI impact for `createJwt` returned unrelated banner/configuration symbols. These results are NOT accepted as trustworthy impact evidence.
- A full, index-only, uncached rebuild was started before further authentication-core edits. No source files, credentials or production data are removed by this recovery. The final graph validation gate remains pending.
- The full rebuild finished but reported FTS checkpoint/rename errors for Enum and Impl tables. Keyword search is degraded; graph integrity and final impact evidence are not yet confirmed. No authentication-core edits were made based on the suspect output.

## Email uniqueness decision history (resolved with owner approval)

- Repository schema `sql/blog(部署).sql` defines `sys_user.email` without a unique index. `userRegister` checks availability then inserts; OAuth registration also writes the provider-returned email. Production schema has not been inspected in this implementation session.
- A user/proof-bound Redis challenge and a conditional update on that user's original state do not alone serialize competing registrations or changes by different accounts to the same address. The planned claim of email uniqueness therefore needs a deliberate cross-writer mechanism.
- Recommended design revision: add a reviewed database migration with a non-destructive duplicate preflight, settle active/deleted-account email reuse and third-party collision handling, and handle uniqueness errors in every writer. Do not silently merge/delete existing accounts or deploy the migration.
- The owner approved the additive migration and preserving all duplicate accounts for manual resolution. Proposal/design/spec/tasks were updated accordingly; production migration remains unauthorized and unexecuted.

## Account security validation (2026-10-03)

- Isolated MySQL migration suite: 6 passed (repeat execution, NULL/collation behavior, duplicate/blank preflight with unchanged records, soft-deleted ownership, conflicting index, concurrent writers). No production connection.
- Graph queries after the full rebuild returned correct named methods and callers. `createJwt`, shared `LoginUser`, and admin challenge creation carried HIGH risk and were announced before edits. Email write wrappers and OAuth callback consumers were LOW with DI boundaries. New/unindexed classes and Vue event handlers were UNKNOWN and checked against concrete source call sites. Final complete `detect_changes` remains pending; FTS rebuild warnings were not treated as a clean gate.
- Email updates now converge on one service. Five-minute proofs bind identity, destination, original credentials, current random epoch and purpose. Password, old-mailbox and exact provider-identity factors are selected from persisted account type. Successful consume rotates epoch before a conditional database update; DB failure does not restore old sessions. Authentication lookup is uncached and reloads the database and Redis epoch on each JWT check.
- Actual isolated Redis tests passed: AccountAuthenticationVersionIntegrationTest (4), EmailChangeServiceIntegrationTest (10), OauthBrowserBindingIntegrationTest (6). External provider and email delivery boundaries are synthetic/mocked; no real provider login, mail delivery or production service tested.
- Additional selected regression classes passed: UserEmailConflictTest (3), OauthBrowserBindingTest (4), OauthServiceImplExchangeTest (5), JwtUtilsSessionSafetyTest (3), JwtUtilsExpiryTest (2), AdminLoginChallengeServiceImplTest (8), UserServiceImplAuthSafetyTest (4), OauthControllerExchangeTest (2), SecurityHandlerSecondFactorTest (1). Refer to Surefire reports for exact counts if tests evolve.
- EmailChangeControllerSafetyTest (2) passed: wrong action proofs return verification failure, not session-logout code; legacy requests cannot bypass required proof; invalid password/code requests do not leak values via validation logs. This closes a global validation-error logging path found during regression.
- Windows Java 17/21 NIO Unix-domain loopback failed locally. Java 17 test runs succeeded with a process-only `-Djdk.net.unixdomain.tmpdir=D:/nonexistent-luyu-qa-socket-dir` after verifying that directory does not exist; JDK's existing socket fallback then uses TCP. No OS/network or production configuration was changed.
- Frontend production build passed. Real compiled Vue email component tests (3) passed using DOM/component boundary doubles: no persisted passwords/codes, fixed destination, retryable failures, provider navigation restriction, expired proof removal. Passwords and mailbox codes never enter browser storage.
- Full frontend `vue-tsc --noEmit` failed with 359 diagnostic output lines across the codebase; none reference the edited Header/Setting components. This is NOT a passed full typecheck and unrelated diagnostics were not silently repaired or suppressed. Full browser/production acceptance and remaining security areas are still pending.
- Archive status: no changes archived yet. Superseded old showcase draft will be archived with its approved supersession note only after the successor is accepted.

## Release package validation (2026-10-03)

- Added explicit Maven resource and final JAR allowlists; default runtime profile is prod, and starter-test is test-only. Existing local dev/private files remain untouched.
- ProductionConfigurationGuardTest: 4 passed, including missing credentials, unresolved placeholders, mixed prod/dev profiles and Quartz JDBC credentials. The final executable JAR was started with only packaged config and prod: it rejected missing settings before external-service initialization as expected. No real credentials or external configuration were supplied to that smoke test.
- Default and `-Pprod` JAR builds passed with a synthetic private profile in source resources and another in stale target/classes. Both final JARs excluded the synthetic files/values and actual known local configuration secrets. The two synthetic QA files were then removed; user configuration was preserved.
- Final JAR plus both existing frontend dist directories passed `check_release_artifacts.py`; scanner tests: 5 passed (private config, test dependencies, known private values, compressed frontend data, missing guard/unbuilt output). The scanner reports only paths/rule names. This gate does not claim a comprehensive secret or vulnerability audit.
- Deployment script modifications require explicit prod/external-config startup and scan artifacts even with SkipBuild. PowerShell parser and Bash syntax checks passed; neither deployment script was executed. Server units/configuration were not inspected or changed.
- Artifact/resource scanning and production-config tests do not resolve the dependency upgrade, full typecheck, remaining functional security tasks or browser acceptance gates.

## Dependency migration decision (approved 2026-10-03)

- Current backend parent is Spring Boot 3.1.4. The official [3.5.16 announcement](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) identifies 3.5.16 as the last open-source-supported 3.5 release. The [official project wiki](https://github.com/spring-projects/spring-boot/wiki/) lists 4.0/4.1 as actively maintained.
- Satisfying the design's supported open-source baseline now requires a major Spring Boot migration, not only a 3.x patch. Java 17 remains supported, but [the migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) calls for staged 3.5 migration and compatibility work. [MyBatis compatibility](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/) and [MyBatis-Plus starters](https://baomidou.com/getting-started/install/) also require aligned integration modules.
- Owner explicitly approved Spring Boot 4.x and necessary compatibility changes, retaining Java 17 and existing business functionality. Production deployment remains unauthorized. Use a staged 3.5 compile check before migrating to the maintained 4.0 line and its aligned integration modules.
- Tasks 10.x and final acceptance remain unchecked until tested. No completed-change archive is claimed.

## pnpm unification and dependency batch (2026-10-03)

- Owner approved pnpm-only. Blog now declares pnpm 11.3.0; admin keeps its established 8.10.0. Duplicate blog npm lock removed, recoverable from Git. Deployment uses Corepack selection plus frozen installs for both projects; temporary admin Husky suppression is restored even on failure.
- Axios 1.20.0 selected in both locks. Backend OSS transitive Bouncy Castle managed at 1.86 and OpenTelemetry at 1.66.0; actual repackaged JAR audited, not only the Maven manifest.
- Latest isolated Java 17 run: 131 targeted tests passed, no skipped. Both frontend builds passed; blog 45 tests, admin pagination 2, admin preview 3, email settings 3, inventory 5 and package-manager policy 2 passed. See `dependency-remediation.md` for commands, scope limitations and the earlier Docker-down failed run.
- Pre-edit GitNexus binding remains personal-website at this checkout, baseline e4e676c. Build manifests/deployment files and new inventory functions were UNKNOWN/not indexed; actual CLI entry points, deployment calls, lock consumers and HTTP wrapper imports were verified before editing. No business request-wrapper bodies changed in this batch. Final graph change gate is still pending with remaining implementation.
- Dependency inventory: no unresolved JAR coordinates or pagination pending; no Maven advisory coordinate matches, 84 npm coordinate matches requiring frontend remediation/reachability review. No zero-risk or complete-security claim.
- A required build-tool compatibility decision was surfaced: Mist's latest peer range excludes maintained Vite versions. Asked permission to migrate to direct Vite rather than bypass peer constraints. No replacement, production operation, Git commit, or archive performed. Overall OpenSpec completion remains 21/48.

## Approved admin build migration (2026-10-03)

- Owner approved direct Vite replacement. Admin now uses Vite 7.3.6 and aligned build plugins, Vitest 5.0.3 and the existing pnpm version. Removed unused Mist CLI/Nitro paths; kept the used ESLint configuration. Preserved static output, API proxy, routing, aliases and browser syntax targets; corrected the public config script's deployment-base path. See `dependency-remediation.md` for versions and reproduction steps.
- Pre-edit graph analysis bound to personal-website at this checkout: `createVitePlugins` LOW, called by Vite config. Manifest/config/new-test targets UNKNOWN or unindexed; concrete package scripts, deployment entry points, disabled-Nitro Mist implementation and owned test source were checked before edits. UNKNOWN was not treated as proof of safety.
- Final frozen install/build, 3 build compatibility tests, 2 pagination tests, 3 safe-preview DOM tests, and the private-value/resource scan passed. Local mocked-browser checks passed at desktop/mobile widths, including loading-overlay removal after verification failure. No real authentication, production requests or deployment were performed. Admin typecheck was not passing at this milestone (the earlier reported count of 43 was corrected to 25 errors in 9 files by two reruns on 2026-10-04); full authenticated-page acceptance remains open.
- Whole-worktree `detect_changes(scope: all)` returned 113 listed changed symbols in 90 files and 66 affected flows; no partial/truncated flags, but CRITICAL risk due to the cumulative authentication/security changes. This is a risk inventory, NOT release approval. New unindexed files and non-symbol build configuration require separate coverage; final cross-feature review and task 12.2 remain open. No commit made.
- Dependency inventory now has 74 npm advisory-coordinate matches across both frontends and zero Maven matches; residual frontend work is not complete. No whole OpenSpec task completed solely by this sub-batch: progress remains 21/48, no archive.

## Admin type-safety repair (2026-10-04)

- Owner requested repair of the 25 verified admin type errors. This bounded batch does not implement the other remaining security tasks. Strict checks remain enabled; no dependency upgrades or backend/database edits were made. Existing unrelated work was preserved.
- Upload lists now use non-optional UploadFile arrays and actual FileType hook arguments. Profile background list entries have stable IDs and filename fallbacks. Compressed Blob/File results in profile-background, banner and photo uploads become named Files with MIME-aligned image extensions through a small adapter; shared `compressImage` behavior is unchanged. Banner previews accept persisted banner records instead of assuming browser upload objects. Removed the now-inapplicable local base64 preview helper and unused black-list imports/variable; tracked deletions remain recoverable in Git.
- Role-state errors no longer fall through to reading an undefined response. Both request rejection and non-success responses restore the actual row.status field and report failure, without modifying other rows or reporting success. Menu/monitoring empty states, black-list selected user IDs and photo callback types now match their actual data. Theme keys use the installed Ant Design Vue 4.0.7 definitions, retaining existing colors.
- `corepack pnpm typecheck`: PASS, zero diagnostics. `corepack pnpm build`: PASS, now runs `vue-tsc --noEmit && vite build` so type failures stop normal packaging. No strict-check disablement, ts-ignore or new any escape hatch was introduced. Existing CSS/Sass and package deprecation warnings are outside this type-error batch and remain.
- `node --test scripts/type-safety.test.mjs`: 8 passed (File/Blob metadata, both role error modes and success, persisted banner preview, banner upload success/failure). Tests transpile the actual page handler bodies with injected API boundaries, not copied implementations; they are not whole-component or production E2E tests. Initial test runner failed to resolve the transitive compiler package; corrected to Vue's public compiler-sfc export without installing a dependency.
- Pagination tests: 2 passed; safe-content DOM tests: 3 passed; build-compatibility tests: 3 passed. First compatibility run printed a canceled build and stalled during the dev-server check; interrupted only that test process and reran unchanged, with all 3 passing. Cause of the transient stall is not established. Desktop/mobile mocked-browser login, email challenge, invalid-code retry and assets passed. Admin dist resource/private-value gate and diff whitespace checks passed. Tests neither upload real objects nor contact production.
- GitNexus repository personal-website, same workspace, index baseline e4e676c: updateBreadcrumb LOW (enterAlbum caller); Vue handlers/store targets UNKNOWN, verified template/App call sites and actual installed component contracts. Shared compressor HIGH (8 direct callers), so its implementation was intentionally left untouched. Current whole-worktree graph scan reports 131 listed changed symbols, 99 files and 66 affected flows, no partial/truncated flags; CRITICAL cumulative security impact still requires final cross-feature review. New tests/adapter have source-and-test coverage but are not indexed yet.
- This completes the admin typecheck sub-step of task 12.1, not the entire frontend/backend security acceptance. Overall original OpenSpec tasks remain 21/48. No commit, deployment or archive performed.
