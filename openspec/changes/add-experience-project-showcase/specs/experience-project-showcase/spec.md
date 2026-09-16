## ADDED Requirements

### Requirement: Company experience exposes project cards
The company experience page SHALL show its published projects as navigable cards with name, summary and available role, contribution/outcome and technology information. Covers SHALL be optional and displayed completely within 16:10 frames without hover cropping.

#### Scenario: Browse projects
- **WHEN** an experience has published projects
- **THEN** visitors SHALL see cards in public API order and navigate to the selected project's nested detail
- **AND** a card without a cover SHALL use a deliberate text layout rather than a broken or oversized blank image

#### Scenario: No projects
- **WHEN** the project request succeeds with an empty list
- **THEN** the page SHALL show a concise empty state and retain all existing experience content

### Requirement: Project detail presents a readable case study
The frontend SHALL provide /experience/:id/projects/:projectId with name, summary, available dates/role/technologies, personal contributions, outcomes and sanitized Markdown content. Optional fields SHALL not produce empty section shells.

#### Scenario: Read complete project
- **WHEN** a visitor opens a public project detail
- **THEN** the page SHALL render its actual data, complete images and safe Markdown
- **AND** SHALL not fabricate achievements, metrics or responsibility descriptions

#### Scenario: Content safety and width
- **WHEN** Markdown contains raw scripts, unsafe links, wide tables or code
- **THEN** existing sanitization SHALL prevent executable unsafe content
- **AND** wide tables/code SHALL scroll within the reading region instead of overflowing the page

#### Scenario: Missing body
- **WHEN** the published project has no Markdown body
- **THEN** available metadata and contributions SHALL remain readable with a concise body-empty state

### Requirement: Navigation and request states remain coherent
The project page SHALL provide linked experience/company breadcrumbs, return navigation and links to other published projects in the same experience. It SHALL refresh on parameter changes, ignore stale responses and support direct refresh via SPA routing.

#### Scenario: Switch between projects
- **WHEN** a visitor follows another project's link without reloading the application
- **THEN** the new project data and page metadata SHALL replace the old data
- **AND** late responses for the previous project SHALL not overwrite it

#### Scenario: Loading failure or unavailable project
- **WHEN** loading is in progress, a request fails, or a project is unavailable
- **THEN** the page SHALL distinguish loading, retryable failure and unavailable states
- **AND** SHALL not retain the previous project's content or treat failures as successful empty results

#### Scenario: Page metadata
- **WHEN** a valid project detail loads or the visitor navigates away
- **THEN** title, description and canonical SHALL correspond to the current page

### Requirement: Case study navigation is accessible and responsive
Project and company pages SHALL use semantic links, visible focus, touch targets at least 44px, reduced-motion support and readable layouts from 360px wide. Project headings SHALL provide a desktop side directory and an expandable mobile directory when headings exist.

#### Scenario: Mobile and keyboard use
- **WHEN** a visitor views pages at 360px width or navigates with a keyboard
- **THEN** all project and return links SHALL be accessible without hover
- **AND** the page SHALL not require horizontal scrolling

#### Scenario: Heading directory
- **WHEN** the project body contains h2/h3 headings
- **THEN** directory entries SHALL navigate to their matching headings without hiding them beneath the fixed header
- **AND** a body without such headings SHALL not show an empty directory
