## ADDED Requirements

### Requirement: Public pages use canonical routes
The public blog SHALL expose each page through exactly one canonical route from the approved route map: `/`, `/experience`, `/experience/:id`, `/blog`, `/blog/articles/:id`, `/blog/categories`, `/blog/categories/:id`, `/blog/tags`, `/blog/tags/:id`, `/blog/archive`, `/tree-hole`, `/messages`, `/messages/:id`, `/links`, `/music`, `/about`, `/photos`, `/auth/login`, `/auth/register`, `/auth/reset`, and `/account`.

#### Scenario: Visitor opens an article
- **WHEN** a visitor opens an article from any public entry point
- **THEN** the article is addressed only as `/blog/articles/:id`

#### Scenario: Visitor opens a standalone page
- **WHEN** a visitor selects a public navigation item or in-page link
- **THEN** the generated URL matches the corresponding canonical route in the approved route map

### Requirement: Administrator pages use canonical routes
The administrator application SHALL expose its pages beneath `/admin` using resource-oriented routes with one route per page. Website-management pages SHALL use `/admin/site/info`, `/admin/articles`, `/admin/articles/new`, `/admin/articles/:id`, `/admin/categories`, `/admin/tags`, `/admin/comments`, `/admin/messages`, `/admin/tree-hole`, `/admin/links`, `/admin/photos`, `/admin/experiences`, `/admin/blacklist`, and `/admin/collections`. System-management pages SHALL use `/admin/system/menus`, `/admin/system/users`, `/admin/system/roles`, `/admin/system/permissions`, `/admin/system/logins`, `/admin/system/operations`, and `/admin/system/server`.

#### Scenario: Administrator enters the application root
- **WHEN** an authenticated administrator opens `/admin`
- **THEN** the application navigates to `/admin/site/info`

#### Scenario: Administrator creates or edits an article
- **WHEN** an administrator creates an article or edits an existing article
- **THEN** the editor uses `/admin/articles/new` for creation and `/admin/articles/:id` for editing without an article ID query parameter

### Requirement: Page route depth is limited to three levels
Every public or administrator page route MUST contain no more than three non-empty path segments, with a dynamic parameter counting as one segment and `/admin` counting as the first segment for administrator URLs.

#### Scenario: Route inventory passes depth validation
- **WHEN** the complete public and administrator route inventory is validated
- **THEN** every concrete and parameterized page route contains at most three path segments

#### Scenario: A page appears to require a deeper route
- **WHEN** implementation discovers a page that cannot be represented within three path segments without changing its meaning
- **THEN** implementation for that page pauses and the exception is reported to the user before any deeper route is introduced

### Requirement: Legacy routes are not retained
The applications MUST NOT register aliases, compatibility redirects, or duplicate route records for replaced page routes.

#### Scenario: Visitor opens a replaced public route
- **WHEN** a visitor opens a replaced route such as `/category`, `/article/:id`, or `/photo`
- **THEN** the public application renders its not-found experience without redirecting to the home page or a canonical page

#### Scenario: Administrator opens a replaced management route
- **WHEN** an administrator opens a replaced route such as `/admin/blog/essay/publish?id=:id`
- **THEN** the administrator application renders its not-found experience without resolving the old route to the new editor

### Requirement: All internal route producers use canonical routes
Navigation menus, dynamic category links, article cards, breadcrumbs, search results, share actions, authentication links, administrator menu data, and programmatic router calls SHALL generate only canonical page routes.

#### Scenario: Internal links are audited
- **WHEN** the application source and administrator menu records are inspected after migration
- **THEN** no internal page link references a replaced route and each destination has a single route producer format

### Requirement: Backend APIs remain unchanged
The route migration MUST NOT change Java backend API paths, request methods, request parameters, response structures, or frontend API client endpoint strings.

#### Scenario: Page routes are migrated
- **WHEN** the new public and administrator page routes are implemented
- **THEN** existing frontend API requests continue to target their original backend endpoints

### Requirement: Canonical routes preserve page metadata
Every canonical public page SHALL retain its applicable fixed-page TDK behavior, and every canonical article route SHALL continue to resolve article-specific TDK from article data.

#### Scenario: Search engine opens a canonical article URL
- **WHEN** an article is rendered at `/blog/articles/:id`
- **THEN** its title, description, keywords, and canonical URL identify that single canonical route

