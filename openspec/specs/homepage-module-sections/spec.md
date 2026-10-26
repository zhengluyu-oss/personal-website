# homepage-module-sections Specification

## Purpose
TBD - created by archiving change show-shares-knowledge-about-on-home. Update Purpose after archive.
## Requirements
### Requirement: Homepage presents three distinct module sections
The homepage SHALL present separate sections for website shares, knowledge, and about, each with a clear heading, a concise truthful summary of the module, and one primary in-site entry point. The sections SHALL follow the existing featured work and blog content without duplicating the about destination elsewhere in the homepage closing area.

#### Scenario: Reader scans the homepage
- **WHEN** a reader scrolls past the blog articles on the homepage
- **THEN** website shares, knowledge, and about each appear as a distinguishable section with a short summary and a direct link to `/website-shares`, `/knowledge`, and `/about` respectively

#### Scenario: About is already linked in the current closing area
- **WHEN** the new about section is rendered
- **THEN** the homepage has one clear primary about entry point rather than two repeated about calls to action

### Requirement: Module summaries respect access boundaries
The homepage SHALL show only non-sensitive module descriptions until access is confirmed. It MUST NOT request or render protected knowledge entries or private about content for an unauthorized reader, and MUST preserve the existing route guard and access-denied flow.

#### Scenario: Reader has module access
- **WHEN** access to one of the three modules is confirmed as allowed
- **THEN** that section offers a clear entry action that reaches its existing route

#### Scenario: Reader lacks module access or access is unavailable
- **WHEN** a module is not allowed or its access state has not been confirmed
- **THEN** its homepage section shows only a generic summary and access-information action, and opening the route follows the existing access-denied behavior without exposing protected content

### Requirement: New sections remain usable across viewports and loading states
The three sections SHALL use the homepage's existing visual language, remain readable and keyboard accessible on desktop and narrow screens, and SHALL NOT disappear because unrelated homepage data requests fail.

#### Scenario: Narrow screen browsing
- **WHEN** the homepage is viewed at 360px, 390px, or 430px width
- **THEN** the three sections and their links fit within the viewport, remain readable, and do not cause horizontal scrolling

#### Scenario: Existing content request fails
- **WHEN** loading articles or work experience fails
- **THEN** the three module summaries and their routes remain visible and usable
