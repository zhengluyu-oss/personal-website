## ADDED Requirements

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
