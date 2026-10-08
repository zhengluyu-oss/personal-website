## ADDED Requirements

### Requirement: Email approval requires explicit authorized confirmation
Friend-link email GET requests SHALL be read-only confirmation entry points. Approval SHALL require a non-GET request from an authenticated administrator with link-review permission and a valid token for the pending link. Approval links SHALL not expose tokens through logs or referrers.

#### Scenario: Email scanner opens a link
- **WHEN** a scanner or anonymous browser performs GET on the approval URL
- **THEN** the link approval state and token consumption state SHALL remain unchanged

#### Scenario: User without review permission submits approval
- **WHEN** an anonymous or insufficiently privileged caller submits a valid token
- **THEN** approval SHALL be denied without consuming the legitimate administrator's token

#### Scenario: Authorized administrator confirms
- **WHEN** an administrator with review permission submits a valid token for a pending link
- **THEN** the matching link SHALL be approved once and subsequent replay SHALL not repeat state changes or notifications

### Requirement: Approval credentials resist guessing and replay
Approval credentials SHALL have at least 256 bits of cryptographic randomness, be stored as digests bound to the link and operation, expire within 24 hours, and be claimed atomically. Legacy timestamp credentials SHALL be rejected. Database updates SHALL verify the original pending state.

#### Scenario: Expired or legacy token is presented
- **WHEN** a timestamp token, expired token, or previously claimed token is submitted
- **THEN** no link state SHALL change and a fresh approval path SHALL be offered

#### Scenario: Concurrent confirmations arrive
- **WHEN** two valid confirmation requests race for one pending link
- **THEN** at most one request SHALL perform the state transition and approval side effects
