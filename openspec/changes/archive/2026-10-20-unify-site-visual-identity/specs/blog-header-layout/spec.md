## ADDED Requirements

### Requirement: Brand mark outranks utility chrome
The desktop header SHALL visually prioritize the site brand title over utility controls so the first glance reads as a personal site, while keeping search and login reachable.

#### Scenario: Desktop header hierarchy
- **WHEN** a visitor views the blog header on a desktop-width viewport
- **THEN** the brand title SHALL be the strongest text signal in the bar
- **AND** search and login SHALL appear as secondary controls without a competing saturated accent color

### Requirement: Navigation icons stay quiet
Primary header navigation MAY show icons, but those icons SHALL use the same ink or muted brand color as the labels and MUST NOT introduce a second accent palette or dominate the label text.

#### Scenario: Nav icons do not overpower labels
- **WHEN** a visitor views primary header destinations with icons enabled
- **THEN** icons SHALL match the nav label color family
- **AND** active state emphasis SHALL remain on the label or shared accent underline rather than a loud multicolor icon row
