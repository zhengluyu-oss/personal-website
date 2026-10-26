# mobile-layout Specification

## Purpose
TBD - created by archiving change fix-mobile-adaptation. Update Purpose after archive.
## Requirements
### Requirement: Common phone viewports do not cause page-level horizontal overflow
The blog frontend and admin login pages SHALL remain usable at viewport widths from 320px to 390px without document-level horizontal scrolling caused by fixed-width chrome or form rows.

#### Scenario: Tree-hole compose row on a small phone
- **WHEN** a visitor focuses the tree-hole input at a 320px-wide viewport and the submit control becomes visible
- **THEN** the page MUST NOT require horizontal scrolling to reach the submit control
- **AND** the input and submit controls remain fully within the viewport width (allowing wrap or stacked layout)

#### Scenario: Admin login card on a small phone
- **WHEN** a visitor opens `/admin/login` at a 320px-wide viewport
- **THEN** the login card and fields fit within the viewport without document-level horizontal scrolling
- **AND** text inputs remain tall enough for reliable touch entry

### Requirement: Music module shows an empty state when the embed URL is missing
The music page SHALL NOT render a full-viewport iframe with an empty source. When the music frontend URL is unset or blank, the page MUST show a clear empty or unavailable state.

#### Scenario: Music URL is not configured
- **WHEN** a visitor opens `/music` and the configured music frontend URL is empty
- **THEN** the page displays an unavailable or empty-state message
- **AND** it MUST NOT present a blank full-screen iframe

### Requirement: Blog category navigation remains discoverable on narrow screens
The blog aggregation category strip SHALL keep categories reachable on viewports near 390px without relying solely on a hidden scrollbar, and MUST NOT widen the document beyond the viewport solely because of off-screen category chips.

#### Scenario: Visitor browses many categories on a phone
- **WHEN** a visitor opens `/blog` at approximately 390px width with more categories than fit on one screen
- **THEN** categories beyond the initial fold remain reachable via intentional horizontal scrolling or wrapping
- **AND** a visual cue indicates that more categories exist when content overflows
- **AND** the document scroll width MUST NOT exceed the client width by more than a negligible rounding tolerance except for the intentional category strip scrollport

### Requirement: Photo album labels remain readable on phone grids
Album titles on the photos index SHALL wrap or truncate with a readable strategy so long Chinese titles do not silently overflow their tile bounds in an unreadable way.

#### Scenario: Long album name on a phone grid
- **WHEN** a visitor views the photos index at approximately 390px width with an album title longer than the tile label width
- **THEN** the title remains readable via wrapping, ellipsis with accessible full text, or equivalent containment
- **AND** the gallery layout MUST NOT depend on a fragile minimum width that forces page overflow

### Requirement: Public login remains usable on phone viewports
The public auth login form SHALL use a mobile-friendly input font size and touch-sized primary actions, and MUST NOT leave decorative welcome copy overlapping essential form controls on narrow screens.

#### Scenario: Visitor logs in on a phone
- **WHEN** a visitor opens `/auth/login` at approximately 390px width
- **THEN** username and password fields use at least 16px computed font size
- **AND** primary login/register actions expose a touch-friendly minimum height
- **AND** welcome branding MUST NOT obscure the form fields or primary actions

### Requirement: Website share metadata does not break narrow layouts
Website share detail metadata SHALL wrap on narrow viewports so external-link actions do not force horizontal page overflow when labels are long.

#### Scenario: Share detail with an external link on a phone
- **WHEN** a visitor opens a website-share detail page near 390px width
- **THEN** the metadata row wraps or stacks as needed
- **AND** the page does not scroll horizontally solely because of the external-link control

