## MODIFIED Requirements

### Requirement: Blog navigation exposes all categories
The blog SHALL provide a primary “个人博客” navigation entry at `/blog` and generate its category children from the public category API using the shared semantic slug route builder.

#### Scenario: Categories load successfully
- **WHEN** the public category API returns enabled article categories
- **THEN** desktop and mobile navigation show one `/blog/:slug` link for each category without exposing category IDs in the URL

#### Scenario: Categories are unavailable
- **WHEN** the category API fails or returns an empty list
- **THEN** the fixed entry to `/blog` remains available and the rest of navigation remains functional

### Requirement: Navigation works across input modes
The blog SHALL make the category navigation usable with mouse, keyboard and touch interaction.

#### Scenario: Visitor opens the blog or a category
- **WHEN** a visitor selects the primary blog entry or one of its category children
- **THEN** the router navigates to `/blog` or the selected `/blog/:slug` aggregation-page filter
