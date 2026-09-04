## ADDED Requirements

### Requirement: Client addresses come only from a trusted proxy boundary
The deployed system SHALL derive the effective client address from an explicitly trusted reverse proxy and MUST NOT trust arbitrary forwarding headers supplied directly by internet clients.

#### Scenario: Client forges forwarding headers
- **WHEN** an internet client changes `X-Forwarded-For`, `X-Real-IP`, or legacy proxy-address headers
- **THEN** Nginx SHALL overwrite the trusted upstream address value
- **AND** the backend SHALL use the trusted value rather than the forged chain

#### Scenario: Application runs without the trusted proxy
- **WHEN** the backend handles a request outside the configured trusted-proxy topology
- **THEN** it SHALL use the socket remote address as the client identity

### Requirement: Sensitive authentication operations have compound limits
The system SHALL rate-limit password login, administrator challenge creation, code resend, and code verification using both a trusted client identity and the normalized target identity or operation purpose.

#### Scenario: Attacker rotates spoofed address headers
- **WHEN** repeated requests target the same administrator account while only untrusted forwarding headers change
- **THEN** the same account-level limit SHALL continue to apply

#### Scenario: Attacker rotates account identifiers from one client
- **WHEN** one trusted client address submits repeated authentication or email-code requests for multiple identities
- **THEN** the client-level limit SHALL continue to apply

#### Scenario: Limit is exceeded
- **WHEN** a configured authentication limit is exceeded
- **THEN** the system SHALL reject further attempts for a bounded cooldown
- **AND** it SHALL record an audit event without recording passwords or verification codes
