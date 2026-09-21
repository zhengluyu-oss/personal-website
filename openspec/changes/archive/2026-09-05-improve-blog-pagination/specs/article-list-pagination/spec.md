## ADDED Requirements

### Requirement: Public blog lists are server paginated
The system SHALL paginate ordinary articles on both `/blog` and `/blog/:slug` through the existing blog-feed API, using 9 ordinary articles per page while displaying the resolved featured article separately and without duplication.

#### Scenario: Open the first page of all articles
- **WHEN** a visitor opens `/blog` without a page query
- **THEN** the system requests page 1 with page size 9, displays at most 9 ordinary articles, and uses the returned total to calculate page count

#### Scenario: Open a category page
- **WHEN** a visitor opens a valid `/blog/:slug`
- **THEN** the system paginates only that category's ordinary articles while preserving the category's configured featured article and Hero copy

#### Scenario: Featured article is excluded from ordinary cards
- **WHEN** the resolved featured article also belongs to the current list scope
- **THEN** it SHALL appear only in the featured position and SHALL NOT be repeated among the 9 ordinary cards

### Requirement: Public pagination state is represented in the URL
The system SHALL store non-default public blog page state in the `page` query parameter so navigation history, refreshes, and copied URLs reproduce the same list state.

#### Scenario: Visitor changes page
- **WHEN** a visitor selects another valid page
- **THEN** the route query updates and the corresponding server page is loaded without navigating to a separate page component

#### Scenario: Visitor changes category
- **WHEN** a visitor selects a different category or returns to all articles
- **THEN** the active page resets to 1 and stale page state from the prior category is removed

#### Scenario: Invalid page query
- **WHEN** the page query is missing, non-numeric, less than 1, or greater than the available last page
- **THEN** the interface SHALL resolve to a valid page and normalize the URL without displaying a blank result caused only by the invalid page

### Requirement: Public pagination is responsive and accessible
The system SHALL provide a visible pagination control after the article cards whenever more than one page exists, with keyboard-operable controls and a compact mobile presentation.

#### Scenario: Multiple pages on desktop
- **WHEN** the current scope contains more than 9 ordinary articles
- **THEN** the visitor can select a page and use previous/next controls below the list

#### Scenario: Single page
- **WHEN** the current scope fits on one page
- **THEN** the pagination control is not displayed

#### Scenario: Mobile viewport
- **WHEN** the viewport is narrow
- **THEN** the control remains inside the content width and prioritizes previous, current-state, and next navigation without horizontal overflow

### Requirement: Admin article lists use server-side pagination
The system SHALL paginate the management article list and filtered search results on the server and return both the current records and total matching record count.

#### Scenario: Open article management
- **WHEN** an administrator opens the article list
- **THEN** the table requests page 1 with the default page size of 10 and displays the returned total

#### Scenario: Change page size
- **WHEN** an administrator selects 10, 20, or 50 rows per page
- **THEN** the table resets to page 1 and requests that page size from the server

#### Scenario: Apply filters
- **WHEN** an administrator searches by title, category, status, or top state
- **THEN** the same filters are applied server-side, page resets to 1, and pagination uses the filtered total

#### Scenario: Refresh without changing filters
- **WHEN** the administrator refreshes the current result set
- **THEN** the current filters, current page, and selected page size remain active

#### Scenario: Delete the last record on a page
- **WHEN** deletion leaves the current page empty and a previous page exists
- **THEN** the table moves to the previous valid page and reloads it

### Requirement: Article pagination parameters are bounded
All article list endpoints that accept page parameters SHALL reject a page number below 1 and a page size outside 1–100 with a clear validation response, and SHALL use documented defaults where parameters are optional.

#### Scenario: Valid pagination request
- **WHEN** a client requests a page number of at least 1 and a page size between 1 and 100
- **THEN** the server returns only that page and the total matching count using deterministic article ordering

#### Scenario: Invalid page number
- **WHEN** a client submits page number 0 or a negative page number
- **THEN** the server rejects the request with a page-number validation message and performs no unbounded article query

#### Scenario: Invalid page size
- **WHEN** a client submits page size 0, a negative value, or a value greater than 100
- **THEN** the server rejects the request with a page-size validation message and performs no unbounded article query

### Requirement: Existing article behavior remains stable
Pagination SHALL preserve existing public visibility rules, featured-article selection, category filtering, deterministic ordering, article detail routes, and management permissions.

#### Scenario: Navigate between pagination and an article
- **WHEN** a visitor opens an article from a paginated list and returns through browser history
- **THEN** the prior category and page are restored and the article detail route remains `/blog/articles/:id`

#### Scenario: Unauthorized management request
- **WHEN** a client without article-list permission requests a paginated management endpoint
- **THEN** the existing authorization policy continues to deny the request

