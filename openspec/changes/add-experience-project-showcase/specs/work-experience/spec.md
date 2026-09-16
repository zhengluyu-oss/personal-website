## MODIFIED Requirements

### Requirement: Structured work experience can be managed in admin
The system SHALL allow an authenticated administrator to create, update, delete, reorder, and enable/disable work experience entries with company, role title, start date, optional end date (or ongoing), responsibility highlights for list summary, optional companyIntroduction and mainBusiness, and optional Markdown content for the detail page.

#### Scenario: Admin creates an experience entry
- **WHEN** an administrator submits a valid work experience form in the admin console
- **THEN** the system SHALL persist the entry including optional company background and content
- **AND** the entry SHALL appear in the admin experience list

#### Scenario: Disabled entries are hidden from the public API
- **WHEN** an administrator disables an experience entry
- **THEN** the public experience list API MUST NOT include that entry
- **AND** the public experience detail API MUST NOT expose that entry as a successful detail

### Requirement: Portfolio-style experience timeline page
The blog frontend SHALL provide /experience as a personal developer experience timeline with concise company, role, period and contribution summaries and navigation to company experience pages.

#### Scenario: Timeline renders entries
- **WHEN** a visitor opens /experience and enabled entries exist
- **THEN** the page SHALL display them in a vertical timeline with clear company, role, period and highlights hierarchy
- **AND** each entry SHALL navigate to its existing detail URL

#### Scenario: Empty state when no entries
- **WHEN** there are no enabled experience entries
- **THEN** the page SHALL show a friendly empty state instead of a broken layout

### Requirement: Public experience detail API returns Markdown body
The system SHALL expose a public read API returning one enabled undeleted experience by id, including companyIntroduction, mainBusiness and existing Markdown content while preserving existing field meanings.

#### Scenario: Visitor loads an enabled experience detail
- **WHEN** the blog frontend requests the public experience detail by a valid enabled id
- **THEN** the response SHALL include company, role, dates, highlights, company background and full content
- **AND** authentication MUST NOT be required

#### Scenario: Disabled or missing experience is not exposed
- **WHEN** the requested id does not exist or the entry is disabled or deleted
- **THEN** the system MUST NOT return the full experience payload as a successful public detail

### Requirement: Experience detail page renders Markdown
The blog frontend SHALL preserve /experience/:id as a company experience page showing available company introduction, main business, personal responsibilities and published project cards, followed by existing experience Markdown as supplementary content using the site's safe preview component.

#### Scenario: Visitor opens detail from the timeline
- **WHEN** a visitor opens an enabled experience with content
- **THEN** its original Markdown including images SHALL remain available after the background and project sections

#### Scenario: Empty content shows a friendly state
- **WHEN** an enabled experience has no content
- **THEN** the page SHALL show available summaries and projects without a broken layout

#### Scenario: Legacy experience
- **WHEN** an experience has no company background or projects
- **THEN** existing identity, responsibilities, outcomes and Markdown SHALL remain visible
- **AND** no empty company information panels or fabricated project data SHALL be inserted
