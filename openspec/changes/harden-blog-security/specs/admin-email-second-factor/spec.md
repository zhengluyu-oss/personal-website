## ADDED Requirements

### Requirement: Administrator sessions require two independent factors
The system SHALL validate the administrator password as the first factor and SHALL require a valid email verification code for the same server-side administrator account before issuing an administrator JWT. The system MUST NOT change the account password as part of this capability.

#### Scenario: Valid administrator password starts a challenge
- **WHEN** an administrator submits valid credentials
- **THEN** the system SHALL send a verification code to the email bound to that account
- **AND** it SHALL return an opaque challenge identifier and masked destination
- **AND** it MUST NOT issue an authenticated administrator token

#### Scenario: Valid code completes administrator login
- **WHEN** the administrator submits the correct unexpired code for an active challenge
- **THEN** the system SHALL atomically consume the challenge
- **AND** it SHALL issue an administrator token derived from persisted roles and permissions

#### Scenario: Password remains unchanged
- **WHEN** the administrator completes or fails the second-factor flow
- **THEN** the stored password hash MUST remain unchanged

### Requirement: Administrator challenges resist guessing and replay
The system SHALL make administrator challenges short-lived, single-use, attempt-limited, and purpose-specific, and SHALL avoid storing or logging plaintext verification codes.

#### Scenario: Incorrect code attempts are bounded
- **WHEN** an incorrect code is submitted repeatedly for one challenge
- **THEN** the system SHALL reject each attempt without revealing whether account details are valid
- **AND** it SHALL invalidate the challenge after at most five failed attempts

#### Scenario: Expired or consumed code is reused
- **WHEN** a code is submitted after expiry or after successful consumption
- **THEN** the system SHALL reject it and MUST NOT issue a token

#### Scenario: Code resend is requested too quickly
- **WHEN** another code is requested before the configured resend cooldown ends
- **THEN** the system SHALL reject or defer the request without sending another message

### Requirement: Client hints cannot grant administrator access
The system MUST derive administrative eligibility from server-side roles and MUST ignore client-controlled presentation headers for authentication and authorization decisions.

#### Scenario: Attacker supplies backend client header
- **WHEN** a requester supplies `X-Client-Type: Backend` without completing administrator authentication and email verification
- **THEN** the system MUST NOT issue an administrator token or grant administrator permissions

#### Scenario: Non-administrator completes normal login
- **WHEN** a non-administrator submits valid credentials
- **THEN** the system SHALL preserve the supported public-user login behavior
- **AND** a client-type header MUST NOT change the account's privileges
