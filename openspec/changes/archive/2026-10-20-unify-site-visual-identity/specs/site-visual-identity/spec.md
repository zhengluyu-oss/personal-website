## ADDED Requirements

### Requirement: Shared Ink and Ember tokens across frontends
The system SHALL expose a single semantic brand token set (canvas, surface, ink, accent, line, radius, shadow) that the blog public UI and the admin workspace surfaces both consume for background, text, and primary actions, and MUST NOT keep LinkedIn-blue (`#155eef`) as the default light-theme accent after this change.

#### Scenario: Blog and admin share accent family
- **WHEN** a visitor views a public blog page in light theme and an operator views the admin workspace in light theme
- **THEN** primary actions and active indicators SHALL use the same ember-family accent semantic
- **AND** page canvases SHALL use the shared near-neutral paper/ink pair rather than unrelated blue-tinted SaaS defaults

#### Scenario: Dark theme keeps one accent family
- **WHEN** the blog is viewed in dark theme
- **THEN** the accent SHALL remain in the same warm ember family as light theme
- **AND** body text and surfaces SHALL meet readable contrast against the dark canvas

### Requirement: Legacy decorative colors do not override brand tokens
Public chrome that still uses template decorative gradients (footer, scrollbars, category chips, or similar shell accents) SHALL be retinted to brand tokens or restrained neutrals and MUST NOT show unrelated pink-purple or rainbow chrome as the default look.

#### Scenario: Footer and scrollbar match brand
- **WHEN** a visitor scrolls a typical public page that includes chrome footer or custom scrollbar styling
- **THEN** those elements SHALL use brand or neutral colors derived from the shared tokens
- **AND** they MUST NOT display the legacy pink-purple footer or rainbow scrollbar as the default theme

### Requirement: Admin login surface matches personal brand
The admin login page SHALL present 陆屿 / configured site branding with the shared accent and canvas tokens and MUST NOT rely on unrelated template product naming as the primary brand mark when personal branding is configured.

#### Scenario: Operator opens admin login
- **WHEN** an operator opens the admin login page
- **THEN** the visible brand treatment SHALL align with the personal site identity and shared accent
- **AND** primary login controls SHALL use the shared accent token

### Requirement: Visual identity acceptance checklist
Before considering the change complete, implementers SHALL verify the shared identity on a fixed set of routes in both light theme and a narrow mobile width without introducing page-level horizontal overflow from the style changes alone.

#### Scenario: Spot-check key routes
- **WHEN** implementers finish the visual identity work
- **THEN** they SHALL spot-check at least home, blog list, one article, experience, public login, and admin login
- **AND** those pages SHALL show the new token accent and no leftover default `#155eef` primary chrome on the checked shells
