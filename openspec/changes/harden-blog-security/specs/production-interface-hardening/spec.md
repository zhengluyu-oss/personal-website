## ADDED Requirements

### Requirement: Production API documentation is unavailable publicly
The system SHALL disable generated OpenAPI documents and Swagger UI in the production application profile and SHALL block known documentation routes at the production reverse proxy.

#### Scenario: Public client requests OpenAPI JSON
- **WHEN** a public client requests a known production OpenAPI or Swagger route
- **THEN** the response SHALL NOT contain the API schema or interactive documentation

#### Scenario: Developer runs the development profile
- **WHEN** the application starts with the explicitly selected development profile
- **THEN** local API documentation MAY remain available for development use

### Requirement: Previously issued administrator sessions can be invalidated
The system SHALL support invalidation of all pre-rotation JWTs by rotating the signing secret and clearing server-side accepted-session state, without modifying administrator passwords.

#### Scenario: Leaked pre-rotation token is presented
- **WHEN** any token signed before the production rotation is presented after rollout
- **THEN** the system MUST reject it regardless of its embedded expiry time

#### Scenario: Administrator completes login after rotation
- **WHEN** an administrator completes both authentication factors after rotation
- **THEN** the system SHALL issue a token signed only by the new secret
- **AND** that token SHALL use the separately configured shorter administrator lifetime

### Requirement: Production services minimize version disclosure
The production reverse proxy SHALL suppress its version token, and production error responses SHALL avoid unnecessary framework or component version details.

#### Scenario: Client requests a missing or rejected resource
- **WHEN** the production proxy or application returns an error response
- **THEN** the response MUST NOT disclose the detailed Nginx or application framework version
