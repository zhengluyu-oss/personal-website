## ADDED Requirements

### Requirement: Projects belong to one work experience
The system SHALL persist projects with a required experience id, name and summary; optional cover, participation dates, role, technologies, contributions, outcomes and Markdown content; and order, publication status, timestamps and soft deletion. New projects SHALL default to unpublished. The parent MUST exist and not be deleted; project ownership SHALL be immutable after creation.

#### Scenario: Create a project
- **WHEN** an authorized administrator creates a valid project from an experience
- **THEN** it SHALL be linked to that experience and default to unpublished
- **AND** its optional fields SHALL be retained without generating fictional content

#### Scenario: Invalid project input
- **WHEN** a write references a missing/deleted parent, empty name/summary, invalid status, overlong bounded fields, or an end date before its start date
- **THEN** the system SHALL reject the write without partial persistence

#### Scenario: Parent mismatch on update
- **WHEN** a project update attempts to change ownership or uses another experience's project id
- **THEN** the update SHALL be rejected

### Requirement: Administrators manage projects within experiences
The admin experience list SHALL provide a project-management entry. Authorized administrators SHALL create, edit, order, publish/unpublish and soft-delete projects, with a Markdown editor and the existing image upload flow. The page SHALL identify its parent experience and permit return to the experience list.

#### Scenario: Save project details
- **WHEN** an administrator edits project metadata and uploads an image in its Markdown editor
- **THEN** saving and reopening SHALL preserve the fields and image Markdown
- **AND** the interface SHALL clearly display save success or failure and publication status

#### Scenario: Enforce existing experience permissions
- **WHEN** a request lacks the corresponding blog:experience:list/add/update/delete authority
- **THEN** the related project management read or mutation SHALL be denied by the server

#### Scenario: Batch delete across parents
- **WHEN** a deletion batch contains an id outside the selected experience
- **THEN** the entire deletion SHALL fail without deleting valid entries in that batch

### Requirement: Public projects enforce parent and child visibility
Public project list and detail SHALL require an enabled, undeleted parent and a published, undeleted project with matching ownership. Anonymous access SHALL be supported only for the intended public read endpoints. Non-public or missing resources MUST NOT return successful private payloads.

#### Scenario: Public summary list
- **WHEN** an anonymous visitor requests projects of a public experience
- **THEN** only its published undeleted projects SHALL be returned ordered by orderNum ascending then id descending
- **AND** the list SHALL exclude full Markdown content

#### Scenario: Public project detail
- **WHEN** an anonymous visitor requests a published project under its public parent
- **THEN** the response SHALL include full project content and parent identity

#### Scenario: Direct link bypass attempt
- **WHEN** the parent is disabled/deleted, the project is unpublished/deleted, or the URL parent does not match the project
- **THEN** the public detail SHALL not expose project content

#### Scenario: Disable and re-enable a parent
- **WHEN** an administrator disables then re-enables an undeleted experience
- **THEN** child publication states SHALL remain unchanged
- **AND** previously published projects SHALL become publicly accessible only after re-enablement

### Requirement: Additive migration preserves existing data
The migration SHALL be repeatable and add nullable company fields and project storage without changing existing experience ids, fields or content, and without seeding public sample projects.

#### Scenario: Existing installation migration
- **WHEN** the migration runs twice on a database containing experience records
- **THEN** both runs SHALL succeed and old records SHALL retain their values
- **AND** existing experiences SHALL remain readable without any projects
