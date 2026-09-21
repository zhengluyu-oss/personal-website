# comment-moderation Specification

## Purpose
Requirements maintained by OpenSpec changes for comment-moderation.
## Requirements
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

### Requirement: Moderated content remains inert throughout its lifecycle
The system SHALL preserve comment and tree-hole values as non-executable user content during submission, storage, moderation preview, approval, and public display.

#### Scenario: Pending content contains markup
- **WHEN** a pending comment or tree-hole entry contains HTML or script-like text
- **THEN** the moderation interface SHALL display it as inert text
- **AND** approving the record MUST NOT make the payload executable publicly

### Requirement: Penetration-test records require verified cleanup
The system's operational procedure SHALL identify suspected penetration-test comments and tree-hole records by payload, timestamp, author, and report evidence before deletion, and SHALL create a recoverable backup of matching records.

#### Scenario: Report ID does not match the stored evidence
- **WHEN** a reported record identifier does not match the expected penetration-test payload and metadata
- **THEN** the record MUST NOT be deleted automatically

#### Scenario: Exact test artifact is confirmed
- **WHEN** an authorized operator confirms that a backed-up record exactly matches the penetration-test evidence
- **THEN** the operator MAY remove only that confirmed test artifact

