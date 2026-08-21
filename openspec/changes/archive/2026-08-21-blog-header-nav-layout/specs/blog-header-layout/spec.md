## ADDED Requirements

### Requirement: Site title stays on one line in the header
The blog frontend header SHALL display the configured website name on a single line at typical desktop widths and MUST NOT wrap the title into multiple lines solely due to an undersized title container.

#### Scenario: Desktop header title does not wrap
- **WHEN** a visitor views the blog on a desktop-width viewport with website name similar to「郑陆宇的个人博客」
- **THEN** the header brand title SHALL appear on one line
- **AND** the title text MUST NOT break into two lines such as「郑陆宇的个人」over「博客」

#### Scenario: Narrow viewports remain usable
- **WHEN** the viewport is narrow enough that the site uses the existing mobile header pattern
- **THEN** the layout MAY hide or collapse desktop nav as today
- **AND** the change MUST NOT break the existing mobile navigation entry point

### Requirement: Header navigation spans the available bar space
The desktop header navigation SHALL use the horizontal space between the site title and the right-side toolbar so that nav items appear spaced and stretched rather than clustered only in the left third of the header.

#### Scenario: Nav items are distributed across the middle of the header
- **WHEN** a visitor views the blog header on a desktop-width viewport
- **THEN** the primary nav items SHALL be laid out across the flexible region between the brand title and the right toolbar
- **AND** the navigation block MUST NOT remain visually confined to approximately the left third of the header bar

#### Scenario: Existing routes and labels are preserved
- **WHEN** the header layout styles are updated
- **THEN** existing nav destinations and visible labels (e.g. 首页、归档、其他、友链、相册) SHALL remain available
- **AND** this change MUST NOT remove those entries or require backend menu configuration
