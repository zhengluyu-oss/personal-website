## Context

The public Vue application currently exposes blog content through unrelated top-level routes such as `/category`, `/tags`, `/timeline`, and `/article/:id`; the primary “个人博客” entry therefore lands on a category page instead of a coherent blog index. The administrator Vue application receives menu routes from the database and currently uses implementation-oriented paths such as `/blog/essay/publish?id=58`. Static route declarations, dynamic menus, programmatic navigation, metadata matching, and persisted menu records all participate in page routing, so changing only the visible navigation would leave duplicate or broken paths.

This change is intentionally breaking. The user has required one route per page, no compatibility with old URLs, a maximum of three path segments, and no changes to backend APIs.

## Goals / Non-Goals

**Goals:**

- Establish a coherent, resource-oriented public route hierarchy headed by `/blog`.
- Give every public and administrator page one canonical route and update every internal route producer.
- Keep all effective browser URLs at three path segments or fewer, including the `/admin` prefix.
- Make `/admin` land directly on website management → information management.
- Preserve TDK resolution, authorization, dynamic category navigation, and existing backend API behavior.
- Render a real not-found page for removed routes instead of hiding mistakes through a home-page redirect.

**Non-Goals:**

- Changing Java controller mappings, API clients, DTOs, response payloads, or database business schemas.
- Preserving old page URLs through aliases, redirects, or rewrite rules.
- Redesigning page visuals or changing menu permissions and business capabilities.

## Decisions

### 1. Use an explicit canonical route registry

The implementation will maintain a typed route-name/path registry in each frontend and use named routes or registry helpers from navigation code. Static route records, link generation, breadcrumbs, share links, and metadata matching will consume the same definitions where practical.

This is preferred over scattered string replacement because route strings currently appear across many components and database-backed menus. A central registry makes uniqueness and depth testable. Introducing a new routing framework was rejected because Vue Router already provides the required behavior.

### 2. Group public editorial content under `/blog`

The public canonical map is:

| Page | Canonical route | Depth |
|---|---|---:|
| Home | `/` | 0 |
| Work experience list/detail | `/experience`, `/experience/:id` | 1 / 2 |
| Blog index | `/blog` | 1 |
| Article detail | `/blog/articles/:id` | 3 |
| Category index/detail | `/blog/categories`, `/blog/categories/:id` | 2 / 3 |
| Tag index/detail | `/blog/tags`, `/blog/tags/:id` | 2 / 3 |
| Archive | `/blog/archive` | 2 |
| Tree hole | `/tree-hole` | 1 |
| Messages list/detail | `/messages`, `/messages/:id` | 1 / 2 |
| Links, music, about, photos | `/links`, `/music`, `/about`, `/photos` | 1 |
| Authentication | `/auth/login`, `/auth/register`, `/auth/reset` | 2 |
| User account | `/account` | 1 |

Plural resource names are used for collections, and an identifier is added only for a specific resource. Optional ID parameters are rejected because `/category/:id?` combines two pages in one ambiguous record. `/blog/articles/:id` is chosen over `/blog/categories/:categoryId/articles/:id` because the latter exceeds the depth limit and an article must have one category-independent URL.

### 3. Flatten administrator resources beneath `/admin`

The administrator deployment prefix counts toward the depth limit. Consequently, visual menu grouping and URL nesting are deliberately decoupled: the sidebar can still show “网站管理” and “系统管理”, while resource pages use shallow effective URLs.

| Area | Effective browser routes |
|---|---|
| Entry/login | `/admin` → `/admin/site/info`, `/admin/login` |
| Website information | `/admin/site/info` |
| Articles | `/admin/articles`, `/admin/articles/new`, `/admin/articles/:id` |
| Website resources | `/admin/categories`, `/admin/tags`, `/admin/comments`, `/admin/messages`, `/admin/tree-hole`, `/admin/links`, `/admin/photos`, `/admin/experiences`, `/admin/blacklist`, `/admin/collections` |
| System management | `/admin/system/menus`, `/admin/system/users`, `/admin/system/roles`, `/admin/system/permissions`, `/admin/system/logins`, `/admin/system/operations`, `/admin/system/server` |

Creation uses the reserved identifier `new`; editing uses the resource identifier directly. Query strings such as `?id=58` are rejected for identifying a page. Nested forms such as `/admin/site/articles/:id` and `/admin/system/roles/:id/users` were rejected because they exceed three segments.

### 4. Migrate database menus atomically without changing APIs

The menu route and component mapping records will be updated through an idempotent SQL migration and matching initialization SQL. Menu parentage and authorization identifiers remain intact; only page path/component mapping fields required for navigation change. The frontend will continue requesting menu data through the existing API, and no backend endpoint will be renamed.

After migration, menu/cache state must be invalidated or refreshed so clients cannot receive stale paths. The migration must be safe to rerun and scoped to known menu identifiers.

### 5. Removed routes resolve to not found

The public catch-all will render a not-found view rather than redirect to `/`. The administrator catch-all will keep rendering its not-found view. Neither application, the web server, nor menu transformation logic will register old-to-new redirects or aliases.

This makes broken internal links visible during verification and follows the explicit decision not to preserve compatibility. The trade-off is that external bookmarks and indexed old URLs immediately stop working.

### 6. Validate the full route graph before deployment

Automated validation will normalize parameterized paths and assert: no duplicate page destinations, no duplicate route names, no optional identity parameters, no internal references to removed paths, and no route deeper than three segments. Runtime smoke checks will cover direct refresh, desktop/mobile navigation, article/category/tag opening, admin default landing, article create/edit, authorization, and 404 behavior.

If any page is found to require more than three levels, that page is excluded from implementation until the user reviews the exception; the rest of the migration must not silently introduce a deeper route.

## Risks / Trade-offs

- [Old external links and search results break immediately] → Inventory all internal producers, update sitemap/canonical metadata if present, and verify the not-found experience before deployment; do not add compatibility redirects.
- [Database menu cache can retain old paths] → Apply the idempotent migration before frontend rollout and explicitly refresh the relevant server/client cache.
- [A string replacement could accidentally alter API endpoints] → Scope replacements to router, view navigation, menu records, and TDK bindings; add a regression check that API client endpoint strings remain unchanged.
- [Reserved `new` can collide with a numeric article identifier] → Constrain article identifiers to their existing numeric format and place the static `new` route before the dynamic record.
- [Route changes can break direct refresh behind Nginx] → Verify SPA fallback independently for `/blog/articles/:id` and `/admin/articles/:id` while ensuring removed routes are handled by the client not-found page.
- [Three-level limit reduces semantic nesting in admin URLs] → Keep semantic grouping in menu metadata and labels while using shallow resource URLs.

## Migration Plan

1. Capture a complete route/link inventory and run the depth/uniqueness audit before edits. Report and pause any route that cannot meet the limit.
2. Add canonical route definitions and update public routes, links, dynamic category navigation, TDK matching, and the public not-found behavior.
3. Update administrator static/dynamic routes and page navigation, then add the menu migration and synchronize initialization SQL.
4. Build both frontends and run route-focused tests plus direct-refresh smoke checks without changing backend API clients.
5. Deploy the menu migration first, refresh menu/cache state, then deploy administrator and public frontend assets in the same maintenance window.
6. Verify canonical paths and confirm representative removed paths return not found rather than redirecting.

Rollback requires restoring the previous frontend assets and reversing the known menu route values from a pre-migration backup. Because compatibility is intentionally absent, a partial rollout is not supported.

## Open Questions

None. The scope, depth limit, API exclusion, and no-compatibility behavior have been confirmed by the user.
