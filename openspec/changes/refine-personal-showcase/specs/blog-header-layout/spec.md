## MODIFIED Requirements

### Requirement: Header navigation has accessible interactive states
The blog frontend header SHALL provide visible hover, current-section and keyboard focus states for desktop and mobile navigation while preserving dynamic article-category subnavigation. Navigation destinations SHALL use real links with href values; submenu toggles SHALL use buttons with accurate expanded state. A navigable parent and its submenu toggle SHALL be separate operable controls when both actions are needed. Exact current-page links SHALL expose `aria-current` without incorrectly marking multiple destinations as the current page.

#### Scenario: Keyboard visitor navigates the header
- **WHEN** a visitor uses Tab to move through header controls
- **THEN** each interactive item shows a visible focus state, links can be followed with Enter, and submenu buttons can be toggled with Enter or Space
- **AND** Escape closes an open submenu and returns focus to its trigger without losing keyboard access to the navigation

#### Scenario: Visitor opens a navigation link in another tab
- **WHEN** a visitor uses the native context menu or modified click on a header destination
- **THEN** the browser can open the destination using its actual href without depending only on a click handler

#### Scenario: Visitor views a nested experience page
- **WHEN** the current route is a company experience or project detail
- **THEN** the work-experience navigation group has a visible current-section state while only an exact current-page link, if present, is marked as the current page

### Requirement: Header adapts to narrow screens without horizontal overflow
The blog frontend header MUST use the mobile entry at viewport widths of 910px or less and desktop navigation above 910px, with no width at which both are unavailable. The header SHALL keep the brand and essential actions legible without horizontal overflow. The existing mobile drawer SHALL use a viewport-constrained readable width, usable touch targets of at least 44px, and keyboard-operable focus and close behavior.

#### Scenario: Mobile header is displayed
- **WHEN** the viewport width is 375px
- **THEN** the brand and mobile navigation entry remain visible, touch targets are usable, and the page does not scroll horizontally

#### Scenario: Visitor crosses the navigation breakpoint
- **WHEN** the viewport width is 909px, 910px or 911px
- **THEN** an available primary navigation entry exists at each width, with the mobile entry used at 909px and 910px and desktop navigation at 911px
- **AND** mutually exclusive navigation modes do not both disappear or create duplicate focusable hidden controls

#### Scenario: Keyboard visitor uses the mobile drawer
- **WHEN** a visitor opens the mobile menu, moves through its controls and closes it with Escape or the close control
- **THEN** focus enters the drawer and remains appropriately managed while it is open, all destinations remain reachable, and closing returns focus to the menu trigger

#### Scenario: Visitor closes the drawer from its backdrop
- **WHEN** the mobile drawer is open and the visitor activates the backdrop
- **THEN** the drawer closes without leaving an invisible focus trap or blocking the page
