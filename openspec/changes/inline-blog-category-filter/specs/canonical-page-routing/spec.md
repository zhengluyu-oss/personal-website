## MODIFIED Requirements

### Requirement: Public pages use canonical routes
The public blog SHALL expose each page through exactly one canonical route from the approved route map: `/`, `/experience`, `/experience/:id`, `/blog`, `/blog/:slug`, `/blog/articles/:id`, `/blog/tags`, `/blog/tags/:id`, `/blog/archive`, `/tree-hole`, `/messages`, `/messages/:id`, `/links`, `/music`, `/about`, `/photos`, `/auth/login`, `/auth/register`, `/auth/reset`, and `/account`.

#### Scenario: Visitor opens an article
- **WHEN** a visitor opens an article from any public entry point
- **THEN** the article is addressed only as `/blog/articles/:id`

#### Scenario: Visitor opens a category filter
- **WHEN** a visitor selects a blog category from any public entry point
- **THEN** the aggregation page is addressed as `/blog/:slug` using that category's semantic slug

#### Scenario: Visitor opens a standalone page
- **WHEN** a visitor selects a public navigation item or in-page link
- **THEN** the generated URL matches the corresponding canonical route in the approved route map

### Requirement: Legacy routes are not retained
The applications MUST NOT register aliases, compatibility redirects, or duplicate route records for replaced page routes.

#### Scenario: Visitor opens a replaced public route
- **WHEN** a visitor opens a replaced route such as `/category`, `/article/:id`, `/photo`, `/blog/categories`, or `/blog/categories/:id`
- **THEN** the public application renders its not-found experience without redirecting to the home page or a canonical page

#### Scenario: Administrator opens a replaced management route
- **WHEN** an administrator opens a replaced route such as `/admin/blog/essay/publish?id=:id`
- **THEN** the administrator application renders its not-found experience without resolving the old route to the new editor

### Requirement: All internal route producers use canonical routes
Navigation menus, dynamic category links, article cards, breadcrumbs, search results, share actions, authentication links, administrator menu data, and programmatic router calls SHALL generate only canonical page routes. Category links MUST use the shared semantic slug route builder and MUST NOT expose database category IDs in page URLs.

#### Scenario: Internal links are audited
- **WHEN** the application source and administrator menu records are inspected after migration
- **THEN** no internal page link references a replaced route and each destination has a single route producer format

#### Scenario: Category links are audited
- **WHEN** desktop, mobile, article-detail, category-index, and context-menu navigation sources are inspected
- **THEN** every category destination uses `/blog/:slug` and no category page URL contains `/categories/` or a raw category ID
