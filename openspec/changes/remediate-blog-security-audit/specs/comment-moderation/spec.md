## ADDED Requirements

### Requirement: Comment access and replies respect target visibility
Public comment lists and counts SHALL require an existing publicly visible target in addition to comment approval. Submission SHALL validate target visibility and supported type; parent and reply references MUST resolve to visible comments in the same target and valid thread. Reply recipients MUST be derived from server-side records, not caller-selected user identifiers. Rejected requests SHALL have no persistence, counter or notification side effects.

#### Scenario: Approved comments belong to a private target
- **WHEN** an anonymous caller requests comments or counts for a private, deleted or unapproved target
- **THEN** the response SHALL reveal neither comments nor target metadata

#### Scenario: Reply references a different target or user
- **WHEN** a reply supplies a cross-target parent, hidden reply, invalid thread relationship or conflicting recipient identifier
- **THEN** the system SHALL reject the request without sending email or saving a comment

#### Scenario: Valid visible thread receives a reply
- **WHEN** an authenticated user submits a valid reply in a visible thread
- **THEN** the reply SHALL follow existing pending-review behavior
- **AND** its recipient relationship SHALL be derived from the actual referenced comment

### Requirement: Notifications do not bypass moderation or privacy
Reply notifications containing user content SHALL be emitted only after approval and transaction commit. Notification consumers and in-app notification queries SHALL revalidate target, source comment and reply visibility, derive recipients server-side, respect preferences and avoid duplicate notifications. Deleted, hidden or unapproved content MUST NOT leak through message bodies or titles.

#### Scenario: New reply remains pending
- **WHEN** a new reply is saved but has not been approved
- **THEN** other users SHALL NOT receive its body through reply email or in-app notifications

#### Scenario: Target changes before queued mail is sent
- **WHEN** a target or comment becomes private, deleted or unapproved between enqueue and consumption
- **THEN** the consumer SHALL suppress content-bearing notification

#### Scenario: Notification is retried
- **WHEN** the same approved reply event is processed repeatedly
- **THEN** a recipient SHALL not receive duplicate notifications for that event
- **AND** disabled notification preferences SHALL remain respected
