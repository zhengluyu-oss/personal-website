# work-experience Specification

## Purpose
Structured work experience for the personal job-seeking blog: admin-managed entries with optional Markdown body, public list/detail APIs, and portfolio-style timeline plus detail pages.

## Requirements

### Requirement: Structured work experience can be managed in admin
The system SHALL allow an authenticated administrator to create, update, delete, reorder, and enable/disable work experience entries with company, role title, start date, optional end date (or ongoing), responsibility highlights for list summary, and optional Markdown `content` for the detail page.

#### Scenario: Admin creates an experience entry
- **WHEN** an administrator submits a valid work experience form in the admin console
- **THEN** the system SHALL persist the entry including optional `content`
- **AND** the entry SHALL appear in the admin experience list

#### Scenario: Disabled entries are hidden from the public API
- **WHEN** an administrator disables an experience entry
- **THEN** the public experience list API MUST NOT include that entry
- **AND** the public experience detail API MUST NOT expose that entry as a successful detail

### Requirement: Public experience list API for the timeline page
The system SHALL expose a public read API that returns enabled work experience entries ordered for display (by sort order and/or reverse chronology), suitable for the timeline list without requiring the full Markdown body.

#### Scenario: Visitor loads the experience page data
- **WHEN** the blog frontend requests the public experience list
- **THEN** the response SHALL include enabled entries with company, role, dates, and highlights
- **AND** the response MUST NOT require clients to download full Markdown `content` for every entry
- **AND** authentication MUST NOT be required for this read endpoint

### Requirement: Portfolio-style experience timeline page
The blog frontend SHALL provide a dedicated `/experience` page that renders enabled work experiences as a vertical timeline suitable for personal job-seeking showcase, with navigation into per-entry detail pages for full Markdown content.

#### Scenario: Timeline renders entries
- **WHEN** a visitor opens `/experience` and enabled entries exist
- **THEN** the page SHALL display them in a vertical timeline with clear hierarchy (company, role, period, highlights)
- **AND** each entry SHALL be navigable to its detail page

#### Scenario: Empty state when no entries
- **WHEN** there are no enabled experience entries
- **THEN** the page SHALL show a friendly empty state instead of a broken layout

### Requirement: Public experience detail API returns Markdown body
The system SHALL expose a public read API that returns a single enabled work experience entry by id, including its Markdown `content` field for detail-page rendering.

#### Scenario: Visitor loads an enabled experience detail
- **WHEN** the blog frontend requests the public experience detail by a valid enabled id
- **THEN** the response SHALL include company, role, dates, highlights, and full `content`
- **AND** authentication MUST NOT be required

#### Scenario: Disabled or missing experience is not exposed
- **WHEN** the requested id does not exist or the entry is disabled
- **THEN** the system MUST NOT return the full experience payload as a successful public detail

### Requirement: Admin can edit Markdown content with images
The admin console SHALL allow administrators to edit an experience entry's Markdown `content` using a Markdown editor that can upload images via the existing article image upload path and insert image Markdown into the body.

#### Scenario: Admin saves Markdown body with an image
- **WHEN** an administrator writes Markdown content, uploads an image through the editor, and saves the experience
- **THEN** the system SHALL persist the Markdown including the inserted image URL
- **AND** a subsequent public detail request for that enabled entry SHALL return the same Markdown body

### Requirement: Experience detail page renders Markdown
The blog frontend SHALL provide an `/experience/:id` detail page that renders the experience Markdown `content` (including images) with the site's Markdown preview component.

#### Scenario: Visitor opens detail from the timeline
- **WHEN** a visitor opens a detail URL for an enabled experience that has `content`
- **THEN** the page SHALL render the Markdown body including embedded images

#### Scenario: Empty content shows a friendly state
- **WHEN** a visitor opens detail for an enabled experience whose `content` is empty
- **THEN** the page SHALL show a friendly empty or summary-only state without a broken layout

### Requirement: Work experience supports rapid recruiter scanning
The blog frontend SHALL present role, company, period and outcome highlights in an order that can be understood without opening each detail page, while retaining navigation to the full Markdown case narrative.

#### Scenario: Recruiter scans the experience list
- **WHEN** a visitor reviews the experience page on desktop or mobile
- **THEN** each entry exposes its core identity and outcomes in a consistent reading order without decorative labels obscuring the content

### Requirement: Work experience uses the shared personal brand system
The experience list and detail page SHALL use the same semantic colors, typography, spacing, shapes, focus states and responsive behavior as other public blog pages.

#### Scenario: Visitor moves between article and experience pages
- **WHEN** a visitor navigates from blog content to an experience page
- **THEN** both surfaces feel part of one site while the experience page retains a stronger professional hierarchy
