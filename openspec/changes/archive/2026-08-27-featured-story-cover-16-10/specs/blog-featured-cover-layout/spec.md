## ADDED Requirements

### Requirement: Featured story cover uses 16:10 aspect ratio

The blog aggregation page featured story cover container (`.featured-story__cover`) SHALL use aspect ratio **16:10** on all breakpoints, matching the article list card cover container.

#### Scenario: Desktop featured cover container

- **WHEN** a visitor views the blog page with a featured article on a viewport wider than 900px
- **THEN** the featured story cover container SHALL have aspect ratio **16:10**
- **AND** SHALL NOT use a wider ratio such as **16:8**

#### Scenario: Mobile featured cover container

- **WHEN** a visitor views the featured story on a viewport at or below 900px
- **THEN** the featured story cover container SHALL still use aspect ratio **16:10**

### Requirement: 16:10 cover image displays without letterboxing in featured area

When the featured article cover image has aspect ratio **16:10**, the featured story cover SHALL display the image without visible letterboxing on the left or right edges (using the same `object-fit: contain` behavior as list cards).

#### Scenario: Author uploaded 16:10 cover

- **WHEN** the featured article has a cover image with aspect ratio **16:10**
- **AND** the visitor views the featured story on the blog page
- **THEN** the primary cover image SHALL fill the featured cover container without horizontal letterboxing

### Requirement: List card cover behavior unchanged

Unifying the featured cover aspect ratio SHALL NOT change the article list card cover layout or aspect ratio (**16:10**).

#### Scenario: List cards below featured story

- **WHEN** a visitor views the article grid below the featured story
- **THEN** each article card cover SHALL remain **16:10** with existing hover and image fit behavior
