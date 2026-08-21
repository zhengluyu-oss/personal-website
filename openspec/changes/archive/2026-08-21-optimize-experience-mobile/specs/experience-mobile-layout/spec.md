## ADDED Requirements

### Requirement: Mobile experience index is scannable
The blog SHALL present each work experience as a touch-friendly vertical summary without horizontal page overflow on viewports from 360px wide.

#### Scenario: Browse experience list on a phone
- **WHEN** a visitor opens the experience index at a viewport between 360px and 560px
- **THEN** the title, period, company, role, highlights and navigation affordance remain readable without horizontal scrolling

### Requirement: Mobile case study remains readable
The blog SHALL reflow the experience detail into a single-column case study and contain wide Markdown content within the reading region.

#### Scenario: Read an experience detail on a phone
- **WHEN** a visitor opens an experience detail at a viewport between 360px and 800px
- **THEN** dossier information precedes the story and wide code or table content does not overflow the page

### Requirement: Touch interaction has visible feedback
The blog SHALL provide visible active and keyboard-focus feedback without requiring hover.

#### Scenario: Activate an experience row
- **WHEN** a visitor presses or keyboard-focuses an experience item
- **THEN** the item displays a clear state change and retains an adequate touch target
