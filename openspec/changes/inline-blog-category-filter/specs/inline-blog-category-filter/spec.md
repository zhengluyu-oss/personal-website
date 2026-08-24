## ADDED Requirements

### Requirement: Category filtering remains within the blog aggregation page
The public blog SHALL render the same aggregation-page header, category navigation, and article region for `/blog` and every valid category route. Selecting a category MUST replace only the article data and related states in the existing aggregation layout.

#### Scenario: Visitor selects a category
- **WHEN** a visitor selects a category from the aggregation-page navigation
- **THEN** the URL changes to that category's semantic route and the matching articles appear below the unchanged header and category navigation

#### Scenario: Visitor returns to all articles
- **WHEN** a visitor selects “全部文章” from a filtered category state
- **THEN** the URL becomes `/blog` and the full article collection appears in the same article region

### Requirement: Route state controls the active category
The aggregation page MUST derive its active category from the current route and SHALL restore the same category selection on direct entry, refresh, browser back, and browser forward.

#### Scenario: Visitor directly opens a category URL
- **WHEN** a visitor opens a valid `/blog/:slug` URL directly
- **THEN** the matching category is selected and its articles are loaded without first rendering an independent category page

#### Scenario: Visitor uses browser history
- **WHEN** a visitor switches categories and then uses browser back or forward
- **THEN** the selected navigation item and displayed article collection match the resulting URL

### Requirement: Filter states remain contextual
Loading, empty, and error states for category filtering SHALL render in the aggregation page's article region while keeping the page header and category navigation available.

#### Scenario: Selected category has no articles
- **WHEN** a valid category returns an empty article collection
- **THEN** the article region displays a category-specific empty state and the selected category remains active

#### Scenario: Category request fails
- **WHEN** loading a selected category fails
- **THEN** the article region displays a retryable error state without replacing the aggregation-page shell

### Requirement: Latest category selection wins
The aggregation page MUST prevent an earlier category request from overwriting the result of a later selection.

#### Scenario: Visitor switches categories quickly
- **WHEN** multiple category requests are in flight and their responses arrive out of order
- **THEN** only the response associated with the current route is rendered

### Requirement: Unknown category slugs render not found
The application MUST treat a slug that cannot be resolved from the current category list as an unknown page and MUST NOT silently show all articles or another category.

#### Scenario: Visitor opens an unknown slug
- **WHEN** a visitor opens `/blog/:slug` and no category resolves to that slug
- **THEN** the existing not-found experience is rendered
