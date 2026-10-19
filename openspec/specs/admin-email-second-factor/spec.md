# admin-email-second-factor Specification

## Purpose
TBD - created by archiving change harden-blog-security. Update Purpose after archive.
## Requirements
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

### Requirement: Administrator code delivery failures have controlled actionable reasons
The system SHALL distinguish code-send cooldown and code-delivery unavailability from incorrect first-factor credentials using allowlisted business reasons. It MUST preserve the existing ResponseResult envelope and successful login/challenge contracts.

#### Scenario: Returning from verification during cooldown
- **WHEN** an administrator passes password verification, leaves the email-code step, and submits valid credentials again before the send cooldown expires
- **THEN** the login response SHALL identify code-send cooldown with business code 1004 and an actionable waiting message
- **AND** it MUST NOT report incorrect username or password, issue a token, send another code, or extend the cooldown

#### Scenario: Retry timing is available
- **WHEN** a cooldown response has a valid positive remaining Redis TTL
- **THEN** the response SHALL include reason CODE_SEND_COOLDOWN and a positive integer retryAfterSeconds rounded up from the remaining duration
- **AND** the waiting message SHALL agree with that value

#### Scenario: Exact retry timing cannot be determined
- **WHEN** cooldown is observed but a valid positive remaining TTL cannot be obtained
- **THEN** the response SHALL give a safe waiting instruction without returning a negative or fabricated retry duration

#### Scenario: Email delivery submission fails
- **WHEN** valid administrator credentials are accepted but the email delivery task cannot be created or published
- **THEN** the response SHALL use business code 1008 with reason CODE_SEND_UNAVAILABLE and a fixed delivery-unavailable retry message
- **AND** it MUST NOT report incorrect credentials, expose upstream exception details, issue a token, or claim successful sending
- **AND** unsuccessful challenge creation SHALL retain its existing cleanup behavior

#### Scenario: Manual resend is blocked
- **WHEN** a valid active challenge requests resend during cooldown or delivery submission fails
- **THEN** the resend endpoint SHALL use the same controlled reason mapping as initial login
- **AND** it MUST NOT mislabel the failure as incorrect credentials or an incorrect verification code
- **AND** the prior valid challenge SHALL remain usable with its existing expiry and attempt bound

### Requirement: Login error specificity preserves credential privacy
The system MUST retain a uniform credential-failure response for unknown accounts, incorrect passwords, and account states that cannot safely be disclosed. Unknown internal failures SHALL receive a fixed service-unavailable response rather than raw exception text or a misleading credential error.

#### Scenario: First-factor credentials fail
- **WHEN** an account is unknown or a supplied password fails validation
- **THEN** the response SHALL retain the established uniform credential-failure code and message
- **AND** it MUST NOT reveal whether a code-send cooldown or account exists

#### Scenario: Unclassified failure occurs after password acceptance
- **WHEN** an unexpected infrastructure failure interrupts administrator challenge creation after valid password verification
- **THEN** the system SHALL return a controlled temporary-service-failure response
- **AND** it MUST NOT expose secrets, exception messages, stack traces or falsely report incorrect credentials

#### Scenario: Verification itself fails
- **WHEN** the code or challenge is incorrect, expired, consumed, missing, or exhausted
- **THEN** the system SHALL preserve the existing uniform second-factor verification failure and attempt restrictions
- **AND** it MUST NOT return remaining guess counts or bypass verification

### Requirement: Both login clients display delivery errors without corrupting state
The blog and administrative login interfaces SHALL show the server's controlled business message once per failed request. Optional reason and retry fields MUST NOT be required to handle legacy responses.

#### Scenario: First-factor submission receives cooldown
- **WHEN** either login page receives a code-send cooldown after submitting credentials
- **THEN** it SHALL display the waiting message on the password-login step without claiming login success or automatically resubmitting

#### Scenario: Resend receives a controlled failure
- **WHEN** either login page receives cooldown or delivery-unavailable while resending
- **THEN** it SHALL preserve the active challenge and available code entry, show the controlled reason, and MUST NOT claim a new code was sent

#### Scenario: Legacy response has only code and message
- **WHEN** a login request fails with the existing code/msg response and no new metadata
- **THEN** both clients SHALL still display an appropriate failure and remain able to retry

#### Scenario: Cooldown ends and normal login succeeds
- **WHEN** the send cooldown expires and valid credentials are submitted again
- **THEN** the existing email challenge flow SHALL resume
- **AND** only successful second-factor verification SHALL establish the administrator session

