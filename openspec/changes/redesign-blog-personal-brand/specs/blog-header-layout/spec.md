## ADDED Requirements

### Requirement: Header navigation has accessible interactive states
The blog frontend header SHALL provide visible hover, active and keyboard focus states for desktop and mobile navigation while preserving dynamic article-category subnavigation.

#### Scenario: Keyboard visitor navigates the header
- **WHEN** a visitor uses Tab to move through header controls
- **THEN** each interactive item shows a visible focus state and expandable navigation remains operable

### Requirement: Header adapts to narrow screens without horizontal overflow
The blog frontend header MUST collapse the desktop navigation into the existing mobile entry at narrow widths and SHALL keep the brand and essential actions legible without horizontal overflow.

#### Scenario: Mobile header is displayed
- **WHEN** the viewport width is 375px
- **THEN** the brand and mobile navigation entry remain visible, touch targets are usable, and the page does not scroll horizontally
