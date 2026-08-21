## ADDED Requirements

### Requirement: Blog navigation exposes all categories
The blog SHALL provide a primary “个人博客” navigation entry and generate its category children from the public category API.

#### Scenario: Categories load successfully
- **WHEN** the public category API returns enabled article categories
- **THEN** desktop and mobile navigation show links to `/category/:id` for each category

#### Scenario: Categories are unavailable
- **WHEN** the category API fails or returns an empty list
- **THEN** the fixed entry to `/category` remains available and the rest of navigation remains functional

### Requirement: Navigation works across input modes
The blog SHALL make the category navigation usable with mouse, keyboard and touch interaction.

#### Scenario: Visitor opens a blog category
- **WHEN** a visitor selects the primary blog entry or one of its category children
- **THEN** the router navigates to the category overview or selected category article list
