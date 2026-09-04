## ADDED Requirements

### Requirement: Administrator challenges use script-compatible Redis encoding
The system MUST store administrator challenge keys, hash fields, hash values, and Lua arguments using a deterministic encoding that preserves numeric counters and digest strings without JSON quoting or type ambiguity.

#### Scenario: Newly created challenge is verifiable
- **WHEN** a valid administrator password creates a new email challenge
- **THEN** the stored attempt counter and the configured attempt limit MUST be readable as numbers by the atomic verification script
- **AND** submitting the correct code MUST NOT fail because of Redis serialization

#### Scenario: Existing application caches remain compatible
- **WHEN** the administrator challenge serialization is changed
- **THEN** the system MUST scope the change to administrator second-factor keys and MUST NOT change the global serialization format of unrelated Redis data

### Requirement: Administrator code verification remains atomic and bounded
The system SHALL compare the code digest, increment failed attempts, enforce the attempt limit, and consume a successful challenge atomically. A successful or exhausted challenge MUST NOT be reusable.

#### Scenario: Correct code completes login once
- **WHEN** the administrator submits the correct unexpired code for an active challenge
- **THEN** the system SHALL consume the challenge and issue the administrator login result
- **AND** a subsequent submission using the same challenge MUST fail

#### Scenario: Incorrect code increments attempts
- **WHEN** the administrator submits an incorrect code for an active challenge below the attempt limit
- **THEN** the system SHALL atomically increment the attempt counter and reject the verification without issuing a token

#### Scenario: Attempt limit is exhausted
- **WHEN** an incorrect submission reaches the configured maximum number of attempts
- **THEN** the system SHALL invalidate the challenge and MUST reject all later submissions for it

### Requirement: Expected verification failures do not become server errors
The system MUST return a controlled, non-enumerating authentication response for an incorrect code, expired or missing challenge, or exhausted challenge. These expected outcomes MUST NOT produce HTTP 500 or expose Redis implementation details.

#### Scenario: Invalid or expired challenge is submitted
- **WHEN** a user submits an incorrect code or a challenge that is expired, missing, consumed, or exhausted
- **THEN** the API SHALL return the established verification-failure business response
- **AND** the response MUST NOT reveal whether the account, challenge, or remaining attempt count exists

#### Scenario: Unexpected Redis outage occurs
- **WHEN** Redis is unavailable or script execution fails for a reason other than an expected challenge outcome
- **THEN** the system SHALL record a sanitized server-side error and MUST NOT expose stack traces or internal script content to the client

### Requirement: The admin login page shows stage-specific errors
The administrator login interface SHALL distinguish first-factor credential errors from second-factor verification errors and SHALL provide an actionable, non-sensitive message for each stage.

#### Scenario: Verification code is rejected
- **WHEN** the second-factor endpoint returns a controlled verification failure
- **THEN** the page SHALL remain on the email-code step, clear the entered code, and explain that the code is invalid or expired
- **AND** it MUST NOT label that outcome as a username or password error

#### Scenario: First-factor credentials are rejected
- **WHEN** the initial username and password request fails authentication
- **THEN** the page SHALL show the account-credential error without displaying a verification-code error
