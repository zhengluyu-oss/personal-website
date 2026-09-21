## Context

The public blog feed already accepts `pageNum` and `pageSize`, but both the client and controller default to 100, so the visual article grid has no page state and increasingly expensive requests. The administration article list uses separate unpaginated list and search endpoints, then gives the entire result to an Ant Design table without a controlled paginator. Page parameters also lack numeric bounds, allowing accidental or hostile oversized requests.

The change crosses the Spring Boot API/service, Vue public blog, Vue admin, and tests. It must preserve the current featured-article rule: the featured item is selected independently and excluded from ordinary page records.

## Goals / Non-Goals

**Goals:**

- Make public all-article and category feeds genuinely server paginated.
- Make admin list and filtered search share one predictable pagination contract.
- Bound page requests to protect memory, query time, response size, and 3M outbound bandwidth.
- Keep page state recoverable through navigation and browser history.
- Handle empty, invalid, and post-deletion page states without blank screens.

**Non-Goals:**

- Changing article detail, category slug, archive, comment, photo, log, or search-suggestion pagination.
- Adding infinite scroll, cursor pagination, a new UI framework, or a new dependency.
- Changing article ranking, featured configuration, permissions, database schema, or SEO semantics.

## Decisions

### 1. Use existing offset pagination and `PageVO`

Public and admin endpoints will continue using MyBatis-Plus `Page` and the existing `PageVO(records, total)` shape. The blog-feed wrapper will keep `featuredArticle`, `articles`, and `total`; only its default/validated page size changes.

This avoids a new pagination abstraction and is sufficient for the current dataset. Cursor pagination was considered but rejected because the UI needs numbered pages and the project already standardizes on totals.

### 2. Public pages use a fixed size of 9

The three-column desktop grid receives three complete rows, while tablet and mobile naturally reflow the same records. The public UI exposes page navigation but not an arbitrary page-size selector, keeping the reading interface simple and response size predictable.

The `page` URL query is canonical for pagination. Page 1 omits the query to keep `/blog` and `/blog/:slug` clean; later pages use `?page=N`. Category changes replace stale pagination with page 1. Route observation, not component remounting, triggers data reload.

### 3. Admin pages use controlled server pagination

The admin table owns `{current, pageSize, total}` and permits 10, 20, and 50. Initial load, filter search, refresh, page change, and page-size change call the same paginated query contract. Search fields and pagination travel together so the database applies filters before counting and slicing.

The existing `/article/back/search` endpoint will become the primary paginated query. `/article/back/list` will also return a paginated shape for compatibility with current screen initialization, or the screen may consistently call search with empty filters. No new route is necessary. Both paths remain protected by the existing permissions.

### 4. Validate at the controller boundary

Article page inputs use Bean Validation constraints equivalent to `pageNum >= 1` and `1 <= pageSize <= 100`, with optional public defaults of 1 and 9 and admin defaults of 1 and 10. Controller validation prevents oversized queries before service execution. Frontends also clamp/normalize state for usability, but server validation remains authoritative.

### 5. Preserve featured-item and total semantics

`total` represents all matching public articles, including a matching featured article, because that number powers the Hero statistic. Page count for ordinary cards must use an `ordinaryTotal` that excludes the resolved featured item, or the API must expose an equivalent explicit pagination total. The design will prefer adding `listTotal` to `BlogFeedVO` while retaining `total` for the Hero, preventing overloaded semantics and off-by-one final pages.

Admin totals count all records matching the active filters. Ordering remains deterministic (`createTime DESC`, then `id DESC`) so page boundaries do not shuffle when timestamps match.

### 6. Normalize invalid and stale public pages

The frontend parses `route.query.page` as a positive integer. Syntactically invalid values normalize to page 1 before the request. If a valid integer exceeds the last page returned by the server, the client replaces it with the last valid page and issues one corrected request. An empty dataset resolves to page 1 without a second request.

## Risks / Trade-offs

- **[Risk] Changing admin response shapes can break existing callers** → Search all frontend callers, update them atomically, and add controller/service contract tests.
- **[Risk] Featured article causes an off-by-one page count** → Separate Hero total from ordinary-list pagination total and test first, middle, final, and featured-only cases.
- **[Risk] Route watchers can trigger duplicate requests** → Use one route-derived loading function and update queries only when the normalized value differs.
- **[Risk] Deleting or filtering can leave the admin on an empty page** → Recalculate the last valid page and reload once after mutations.
- **[Risk] New pagination may expose stale cached assumptions** → Do not cache page responses under the existing unscoped key; include category, page, and size if caching is added later.
- **[Trade-off] Offset pagination becomes slower at very large offsets** → Acceptable for a personal blog; cursor pagination remains a future option if the dataset grows substantially.

## Migration Plan

1. Implement backend validation, response totals, deterministic ordering, and paginated admin queries with tests.
2. Update the admin API client/table and public blog API/page in the same release branch.
3. Build both frontends and run backend/public frontend pagination tests.
4. Deploy backend first, then admin and public frontend immediately afterward because the admin response contract changes.
5. Verify `/blog`, one category, a later page, browser back/forward, admin filters, page-size changes, and deletion on a final page.
6. Roll back frontends and backend together if the admin response contract causes an issue; no database rollback is required.

## Open Questions

None. The selected defaults are public 9 per page, admin 10 per page, admin choices 10/20/50, and a server maximum of 100.
