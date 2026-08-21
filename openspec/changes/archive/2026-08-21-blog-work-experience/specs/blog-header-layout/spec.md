## ADDED Requirements

### Requirement: Work experience is a primary header destination
The blog frontend desktop and mobile navigation SHALL provide a primary item labeled「工作经历」that navigates to the experience page, and MUST NOT keep「归档」as a primary dropdown whose only purpose is category/tag/timeline grouping.

#### Scenario: Primary nav opens experience
- **WHEN** a visitor clicks「工作经历」in the header (desktop or mobile menu)
- **THEN** the app SHALL navigate to `/experience`

### Requirement: Category and tag remain reachable under Other
The blog frontend SHALL keep category and tag destinations available under the「其他」dropdown (or equivalent secondary group) after removing the「归档」primary dropdown.

#### Scenario: Category still reachable
- **WHEN** a visitor opens「其他」in the header
- **THEN** they SHALL be able to navigate to category and tag pages

## MODIFIED Requirements

### Requirement: Header navigation spans the available bar space
The desktop header navigation SHALL use the horizontal space between the site title and the right-side toolbar so that nav items appear spaced and stretched rather than clustered only in the left third of the header.

#### Scenario: Nav items are distributed across the middle of the header
- **WHEN** a visitor views the blog header on a desktop-width viewport
- **THEN** the primary nav items SHALL be laid out across the flexible region between the brand title and the right toolbar
- **AND** the navigation block MUST NOT remain visually confined to approximately the left third of the header bar

#### Scenario: Core destinations remain available after experience nav change
- **WHEN** the header navigation is updated for work experience
- **THEN** home, work experience, other (with category/tag), friend links, and album SHALL remain reachable
- **AND**「归档」MUST NOT remain as the primary label for that former dropdown
