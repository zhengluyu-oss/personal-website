## ADDED Requirements

### Requirement: Favorites and administrative previews preserve rendering trust boundaries
Favorites and all administrative content previews SHALL display user messages as inert text and sanitize generated article Markdown HTML before DOM insertion. The same protections SHALL apply to legacy records. Favorite creation SHALL validate target existence, supported type and caller visibility, and missing targets SHALL produce a controlled unavailable state.

#### Scenario: Administrator previews a stored malicious favorite
- **WHEN** a favorite contains event handlers, dangerous URL schemes, script-like markup or an SVG payload
- **THEN** no payload script, handler or active embedded content SHALL execute in the administrator browser

#### Scenario: Legitimate Markdown article is previewed
- **WHEN** an authorized administrator opens a valid article favorite
- **THEN** safe headings, tables, code blocks and links SHALL retain their intended presentation after sanitization

#### Scenario: Hidden or removed target is referenced
- **WHEN** a user creates a favorite for an inaccessible target or a stored favorite references a removed record
- **THEN** creation SHALL be denied or the existing favorite SHALL display a controlled unavailable state respectively
- **AND** the system SHALL neither leak the target content nor throw a null-reference server error
