## ADDED Requirements

### Requirement: Knowledge reader uses the shared site layout
The blog frontend SHALL render `/knowledge` within the same site layout used by ordinary content pages, including the shared header navigation, without changing the knowledge reader's address or content capabilities.

#### Scenario: Open knowledge from desktop navigation
- **WHEN** an authorized reader selects「知识库」from the desktop site navigation
- **THEN** the knowledge reader appears at `/knowledge` with the shared header still visible and its navigation usable

#### Scenario: Open a bookmarked knowledge page
- **WHEN** an authorized reader opens or refreshes `/knowledge` directly
- **THEN** the shared site layout and knowledge reader appear together at the same address

#### Scenario: Leave knowledge through the header
- **WHEN** a reader selects another site destination from the header while viewing `/knowledge`
- **THEN** navigation reaches that destination without requiring a browser back action

### Requirement: Knowledge layout works on narrow screens
The knowledge page SHALL retain the shared mobile navigation entry and SHALL keep its own reading controls clear of the fixed header without horizontal overflow.

#### Scenario: Navigate from a mobile viewport
- **WHEN** an authorized reader visits `/knowledge` on a narrow viewport
- **THEN** the shared mobile menu remains available and can navigate to other site sections
- **AND** the knowledge title, filters and entry content remain visible below the header without horizontal scrolling

### Requirement: Shared layout preserves knowledge access control
Rendering knowledge within the shared layout MUST NOT bypass the existing module permission check or expose protected entries to an unauthorized reader.

#### Scenario: Reader lacks knowledge access
- **WHEN** a reader without knowledge module access navigates to `/knowledge` through a link or direct URL
- **THEN** the existing access-denied flow is shown within the site layout
- **AND** protected knowledge entries are not rendered
