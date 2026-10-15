## ADDED Requirements

### Requirement: Audit events exclude authentication secrets before transport
The system SHALL project sensitive operations into an allowlisted audit event before serialization or queue publication, and MUST NOT record plaintext passwords, verification codes, session or provider tokens, cookies, signing secrets, or storage credentials in requests, responses, or exceptions. Other structured audit data SHALL undergo bounded recursive redaction.

#### Scenario: Password operation succeeds or fails
- **WHEN** registration, password reset, login, or email change succeeds or throws an exception
- **THEN** every emitted log and queued audit event SHALL exclude the submitted secret values
- **AND** safe operation, outcome, and correlation metadata SHALL remain available

#### Scenario: Nested or unknown input reaches logging
- **WHEN** a request contains nested lists, maps, unexpected objects, or excessive depth
- **THEN** the logger SHALL redact sensitive fields, bound traversal, and omit unsafe objects without invoking arbitrary string conversion

### Requirement: Audit consumers and historical remediation preserve confidentiality
Audit consumers SHALL defensively sanitize legacy messages before persistence. Historical remediation SHALL default to counts and metadata only, and destructive cleanup SHALL require an explicit target scope, restricted backup, and operator authorization.

#### Scenario: Legacy queue message contains a password
- **WHEN** a message produced before remediation is consumed
- **THEN** the plaintext secret SHALL NOT be persisted or echoed in error diagnostics

#### Scenario: Historical scan runs without cleanup authorization
- **WHEN** an operator inventories potentially affected logs
- **THEN** the report SHALL contain safe counts and locations only
- **AND** records SHALL remain unchanged until the cleanup operation is authorized
