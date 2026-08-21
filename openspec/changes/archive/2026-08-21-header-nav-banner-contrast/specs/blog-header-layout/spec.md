## ADDED Requirements

### Requirement: Header remains readable over bright home banners
On the home page at the top of the viewport, the blog header navigation (site title and primary menu items) SHALL remain readable against bright or high-contrast banner imagery and MUST NOT rely on a fully transparent bar with no contrast aid.

#### Scenario: Bright banner does not hide nav text
- **WHEN** a visitor views the home page at scroll position 0 with a bright banner region behind the header
- **THEN** the site title and primary nav labels SHALL remain legible
- **AND** the header MUST provide contrast aids such as a frosted/semi-opaque bar and/or a top banner gradient and/or text shadow

#### Scenario: Immersive overlay feel is preserved
- **WHEN** the home header contrast aids are applied
- **THEN** the header MAY remain visually overlaid on the banner
- **AND** the bar MUST NOT become a fully opaque solid slab that completely hides the banner under the nav height unless needed for accessibility

## MODIFIED Requirements

### Requirement: Header navigation spans the available bar space
The desktop header navigation SHALL use the horizontal space between the site title and the right-side toolbar so that nav items appear spaced and stretched rather than clustered only in the left third of the header, while remaining readable when overlaid on the home banner.

#### Scenario: Nav items are distributed across the middle of the header
- **WHEN** a visitor views the blog header on a desktop-width viewport
- **THEN** the primary nav items SHALL be laid out across the flexible region between the brand title and the right toolbar
- **AND** the navigation block MUST NOT remain visually confined to approximately the left third of the header bar

#### Scenario: Core destinations remain available after experience nav change
- **WHEN** the header navigation is updated for work experience
- **THEN** home, work experience, other (with category/tag), friend links, and album SHALL remain reachable
- **AND**「归档」MUST NOT remain as the primary label for that former dropdown

#### Scenario: Top-of-page transparency does not remove contrast
- **WHEN** the visitor is at the top of the home page and the header uses a top-of-page style
- **THEN** that style MUST NOT be fully transparent without additional contrast treatment
- **AND** nav text MUST remain readable over the banner
