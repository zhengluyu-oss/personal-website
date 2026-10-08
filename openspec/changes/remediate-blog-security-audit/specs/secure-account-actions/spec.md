## ADDED Requirements

### Requirement: Email ownership remains unique under concurrent writes
Registration, third-party account creation and email changes SHALL enforce uniqueness through a database constraint using the existing email column collation, across account types and including disabled and soft-deleted accounts. NULL SHALL remain available for unbound accounts. Conflicts SHALL produce a controlled failure rather than linking identities by email.

#### Scenario: Existing duplicate addresses block migration
- **WHEN** preflight finds duplicate non-null emails or invalid blank addresses
- **THEN** migration SHALL stop without changing, deleting or merging any account
- **AND** diagnostics SHALL report counts without printing addresses or credentials

#### Scenario: Two accounts claim the same email concurrently
- **WHEN** registration or email replacement races to claim an identical address
- **THEN** at most one write SHALL succeed
- **AND** repeating a successful migration SHALL preserve all account data

### Requirement: Email changes require proof of the existing identity
Email binding and replacement SHALL use server-persisted account type. Password accounts MUST verify the current password; administrator accounts MUST additionally verify their current bound email factor. Third-party accounts MUST recently reauthenticate the same provider identity, and replacing an existing email MUST additionally prove control of the existing email. All paths MUST verify the new email independently.

#### Scenario: Password account calls the third-party binding route
- **WHEN** a password account presents an arbitrary password and a valid code for a new email
- **THEN** the server SHALL reject the change and leave the bound email and credentials unchanged

#### Scenario: Legitimate account owner changes email
- **WHEN** the account owner completes all factors required by their persisted account type and verifies the new email
- **THEN** the server SHALL update the email subject to uniqueness and original-state checks

#### Scenario: Third-party identity differs or original mailbox is unavailable
- **WHEN** provider reauthentication identifies a different account or the required original-email proof is missing
- **THEN** automatic email change SHALL fail closed without falling back to new-email-only verification

### Requirement: Sensitive-action proofs are bounded and single use
Proofs SHALL bind account, credential version, purpose, target email and expiry, expire within five minutes, allow at most five failed verification attempts, and be atomically consumed for one update attempt. Ordinary login SHALL retain its configured lifetime, including the configured 24-hour session behavior; successful identity changes SHALL invalidate prior sessions and pending sensitive challenges for the changed account.

#### Scenario: Proof is replayed or used for a different destination
- **WHEN** a consumed, expired, wrong-purpose or differently bound proof is presented
- **THEN** the operation SHALL be rejected without modifying the account

#### Scenario: Two requests race to consume a proof
- **WHEN** concurrent requests present the same valid proof
- **THEN** at most one update attempt SHALL be authorized

#### Scenario: Old token is used after email replacement
- **WHEN** a token issued before the email change is used after a successful change
- **THEN** it SHALL be rejected even if its expiry is in the future
- **AND** reauthentication SHALL preserve administrator second-factor requirements

#### Scenario: Email returns to its previous value or revocation state is lost
- **WHEN** the account restores a previous email or the server cannot load its authentication version
- **THEN** previously revoked sessions SHALL NOT become valid again
- **AND** missing revocation state SHALL require fresh authentication rather than accepting old tokens

### Requirement: OAuth exchange is bound to the initiating browser and purpose
The server SHALL bind provider state, provider identity, flow purpose and exchange code to an unguessable browser-held secret established before redirection. Exchange codes SHALL remain single-use and expire within two minutes. Invalid attempts MUST NOT consume another browser's valid code or disclose tokens. Callback destinations SHALL come from an explicit allowlist.

#### Scenario: Attacker shares their own valid exchange URL
- **WHEN** another browser opens that URL without the initiating browser secret
- **THEN** no authenticated session SHALL be issued or replaced

#### Scenario: Initiating browser finishes supported provider login
- **WHEN** GitHub or Gitee authentication returns valid bound state and the browser completes exchange
- **THEN** the server SHALL consume the exchange once and continue the appropriate login or administrator second-factor flow
- **AND** provider tokens SHALL NOT appear in the URL or frontend storage

#### Scenario: Existing session or multiple tabs are present
- **WHEN** a callback arrives while the frontend already holds a valid session or another tab has a separate login attempt
- **THEN** the existing identity SHALL NOT be silently overwritten
- **AND** separate attempts SHALL NOT consume each other's state

#### Scenario: State validation or Redis fails
- **WHEN** provider, purpose, browser binding, expiry, or state storage cannot be validated
- **THEN** exchange SHALL fail closed with a controlled error and no token mutation
