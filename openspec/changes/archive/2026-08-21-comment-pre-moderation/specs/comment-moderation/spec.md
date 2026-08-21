## ADDED Requirements

### Requirement: New comments await admin approval before public display
The system SHALL persist newly submitted article and leave-word-board comments in an unapproved state by default, and MUST NOT include unapproved comments in public comment listings or public comment counts until an administrator marks them approved.

#### Scenario: Visitor submits a comment
- **WHEN** an authenticated user successfully submits a comment while the feature is available
- **THEN** the system SHALL store the comment as unapproved
- **AND** public comment list APIs MUST NOT return that comment until it is approved

#### Scenario: Admin approves a pending comment
- **WHEN** an administrator sets a pending comment to approved via the admin moderation action
- **THEN** subsequent public comment list requests for that target SHALL include the comment
- **AND** public comment counts SHALL reflect the newly approved comment

#### Scenario: Admin rejects or unapproves a comment
- **WHEN** an administrator sets a comment to not approved
- **THEN** the comment MUST NOT appear in public comment listings
- **AND** public comment counts MUST NOT include it

### Requirement: Comment authors are informed of pending review
After a successful comment submission that requires moderation, the blog frontend SHALL inform the user that the comment was submitted and is pending review, and MUST NOT present the unapproved comment as already publicly visible in the live thread.

#### Scenario: Success message after submit
- **WHEN** a user submits a comment that is saved as unapproved
- **THEN** the UI SHALL show a pending-review success message (or equivalent)
- **AND** the public thread MUST NOT immediately show that unapproved comment as a live item
