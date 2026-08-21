## ADDED Requirements

### Requirement: Structured work experience can be managed in admin
The system SHALL allow an authenticated administrator to create, update, delete, reorder, and enable/disable work experience entries with company, role title, start date, optional end date (or ongoing), and responsibility highlights.

#### Scenario: Admin creates an experience entry
- **WHEN** an administrator submits a valid work experience form in the admin console
- **THEN** the system SHALL persist the entry
- **AND** the entry SHALL appear in the admin experience list

#### Scenario: Disabled entries are hidden from the public API
- **WHEN** an administrator disables an experience entry
- **THEN** the public experience list API MUST NOT include that entry

### Requirement: Public experience list API for the timeline page
The system SHALL expose a public read API that returns enabled work experience entries ordered for display (by sort order and/or reverse chronology).

#### Scenario: Visitor loads the experience page data
- **WHEN** the blog frontend requests the public experience list
- **THEN** the response SHALL include enabled entries with company, role, dates, and highlights
- **AND** authentication MUST NOT be required for this read endpoint

### Requirement: Portfolio-style experience timeline page
The blog frontend SHALL provide a dedicated `/experience` page that renders enabled work experiences as a vertical timeline suitable for personal job-seeking showcase.

#### Scenario: Timeline renders entries
- **WHEN** a visitor opens `/experience` and enabled entries exist
- **THEN** the page SHALL display them in a vertical timeline with clear hierarchy (company, role, period, highlights)

#### Scenario: Empty state when no entries
- **WHEN** there are no enabled experience entries
- **THEN** the page SHALL show a friendly empty state instead of a broken layout
