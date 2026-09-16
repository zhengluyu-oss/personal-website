## MODIFIED Requirements

### Requirement: Featured story cover uses 16:10 aspect ratio

The blog aggregation page and category page featured story cover container (`.featured-story__cover`) SHALL use aspect ratio **16:10** on all breakpoints, matching the article list card cover container. Its ratio SHALL remain independent of title and excerpt length.

#### Scenario: Desktop featured cover container

- **WHEN** a visitor views the blog page with a featured article on a viewport wider than 900px
- **THEN** the featured story cover container SHALL have aspect ratio **16:10**
- **AND** SHALL NOT use a wider ratio such as **16:8**
- **AND** the card SHALL retain its image-left, text-right layout with vertically centered text

#### Scenario: Mobile featured cover container

- **WHEN** a visitor views the featured story on a viewport at or below 900px
- **THEN** the featured story cover container SHALL still use aspect ratio **16:10**
- **AND** the card SHALL display the cover above its text

#### Scenario: Short or long article text

- **WHEN** a featured article has a one-line excerpt such as `111`, a multi-line excerpt, or a long title
- **THEN** its cover container SHALL remain 16:10 without stretching to match text height
- **AND** text SHALL remain within the card using the existing title and excerpt line limits

### Requirement: 16:10 cover image displays without letterboxing in featured area

When the featured article cover image has aspect ratio **16:10**, the featured story cover SHALL display the complete image without visible letterboxing, cropping, or distortion, using `object-fit: contain` behavior. Other source ratios SHALL preserve the complete primary image with the existing soft background filling unused space.

#### Scenario: Author uploaded 16:10 cover

- **WHEN** the featured article has a cover image with aspect ratio **16:10**
- **AND** the visitor views the featured story on the blog page
- **THEN** the primary cover image SHALL fill the featured cover container without horizontal letterboxing
- **AND** all four image edges SHALL remain visible

#### Scenario: Near-standard or non-standard source ratio

- **WHEN** a cover has a near-standard ratio such as 1586×992, a wider ratio, or a portrait ratio
- **THEN** the primary image SHALL remain fully visible and undistorted
- **AND** unused space SHALL use the existing soft background rather than cropping the primary image

### Requirement: List card cover behavior unchanged

Unifying the featured cover aspect ratio SHALL preserve the article list card cover layout, **16:10** ratio, and contain image fit. Primary image hover enlargement SHALL be removed from list cards to prevent edge cropping; other card hover and keyboard focus feedback SHALL remain.

#### Scenario: List cards below featured story

- **WHEN** a visitor views the article grid below the featured story
- **THEN** each article card cover SHALL remain **16:10** with complete primary image display

#### Scenario: Hovering a list card

- **WHEN** a visitor hovers an article list card
- **THEN** the primary image SHALL NOT enlarge or crop its edges
- **AND** the existing card border, shadow, and movement feedback SHALL remain

## ADDED Requirements

### Requirement: Featured primary image remains complete during interaction

The featured primary cover image SHALL remain at its resting scale during hover and keyboard focus. Existing navigation and focus feedback SHALL remain available.

#### Scenario: Hovering the featured story

- **WHEN** a visitor hovers the featured story
- **THEN** the primary cover image SHALL remain fully visible without enlargement or edge cropping

#### Scenario: Keyboard navigation

- **WHEN** a visitor focuses and activates the featured story through its existing keyboard interaction
- **THEN** the focus indicator and article navigation SHALL remain functional
- **AND** focusing SHALL NOT crop the primary image
