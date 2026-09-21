## ADDED Requirements

### Requirement: Category names produce URL-safe semantic slugs
The frontend SHALL derive a lowercase slug containing only ASCII letters, digits, and hyphens from every category name.

#### Scenario: Chinese category name is converted
- **WHEN** the category name is “技术笔记”
- **THEN** the generated base slug is `jsbj`, using the first letter of each Han character's pinyin

#### Scenario: English category name is converted
- **WHEN** a category name begins with one or more English words
- **THEN** the generated base slug uses the first English word in lowercase

#### Scenario: Mixed category name is converted
- **WHEN** a category name contains Chinese and English segments such as “AI工具”
- **THEN** the generated base slug preserves segment order and combines the first English word with the Chinese pinyin initials using hyphens, such as `ai-gj`

### Requirement: Category slugs are unique and deterministic
The slug generator MUST return one stable, unique slug per category for the same category list. Empty results, duplicate base slugs, and reserved slugs MUST be disambiguated with a deterministic category ID suffix.

#### Scenario: Two categories produce the same base slug
- **WHEN** multiple categories normalize to the same base slug
- **THEN** each conflicting category receives a unique slug using its category ID as the deterministic suffix

#### Scenario: Category conflicts with a fixed blog route
- **WHEN** a category normalizes to a reserved slug such as `articles`, `tags`, or `archive`
- **THEN** its category ID is appended so the fixed route remains unambiguous

#### Scenario: Category name cannot be transliterated
- **WHEN** normalization produces no usable letters or digits
- **THEN** the category remains addressable through a deterministic ID-suffixed fallback slug

### Requirement: Slugs resolve back to existing category IDs
The frontend SHALL resolve a valid slug to the corresponding category ID before calling the existing category article API and MUST NOT change the API request contract.

#### Scenario: Valid slug is resolved
- **WHEN** the frontend receives the category list and matches the current slug
- **THEN** it calls the existing filtered article API with that category's numeric ID

### Requirement: All category links share one slug implementation
Desktop navigation, mobile navigation, aggregation filters, article detail category links, and any category index links MUST use the same shared slug and route-building implementation.

#### Scenario: Internal category links are audited
- **WHEN** all frontend category link producers are inspected
- **THEN** none of them directly concatenate `/blog/categories/` with a category ID
