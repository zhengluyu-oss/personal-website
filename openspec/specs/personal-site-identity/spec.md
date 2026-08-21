# personal-site-identity Specification

## Purpose
Blog frontend personal branding and de-templating: identity copy, hidden sample donation UI, no legacy template CDN cursors, and clear About navigation for 郑陆宇’s personal site.

## Requirements

### Requirement: Personal identity copy replaces template copy
The blog frontend SHALL present webmaster-facing copy consistent with 郑陆宇 personal branding and MUST NOT display the template About prose that describes cultivation/xianxia roleplay or “中二Web全栈小白” as the primary self-introduction.

#### Scenario: About page shows personal intro
- **WHEN** a visitor opens the About page
- **THEN** the headline and body text SHALL be the configured personal intro (job-seeking / tech-oriented)
- **AND** the page MUST NOT show the legacy cultivation template paragraph as primary content

#### Scenario: Greeting text is personalized
- **WHEN** the site shows time-based or welcome greeting strings from frontend utilities
- **THEN** the text SHALL refer to the personal blog brand (郑陆宇 / configured site name)
- **AND** it MUST NOT say “小张的个人博客” or other leftover template owner names

### Requirement: Template donation list is not shown by default
The blog frontend SHALL NOT display the hardcoded sample donation (`ChargingList`) entries that reference ruyu-blog, 开源项目, or 群主开源.

#### Scenario: Donation sidebar hidden
- **WHEN** a visitor views pages that include the default sidebar layout
- **THEN** the sample charging/donation list SHALL NOT be visible
- **AND** no fabricated donor messages from the original template SHALL appear

### Requirement: No legacy template CDN cursor assets
The blog frontend MUST NOT load custom cursor files from `image.kuailemao.xyz` (or other retired template CDN hosts) for default browsing cursors.

#### Scenario: Default system cursor
- **WHEN** a visitor browses the blog with default theme styles applied
- **THEN** cursor styles SHALL use the browser/system default (or non-template-hosted assets only)
- **AND** the browser MUST NOT request cursor URLs under `image.kuailemao.xyz`

### Requirement: About navigation avoids duplicate template social tiles
The About page SHALL provide clear personal navigation without two identical large GitHub icons that convey a unfinished template layout.

#### Scenario: Distinct About links
- **WHEN** a visitor views the About navigation section
- **THEN** GitHub profile and repository (if both present) SHALL be visually or labeled as distinct
- **OR** only one primary GitHub entry is shown with an optional secondary text link
