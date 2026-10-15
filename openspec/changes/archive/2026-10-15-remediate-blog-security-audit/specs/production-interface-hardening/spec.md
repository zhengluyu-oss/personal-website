## ADDED Requirements

### Requirement: API routes use explicit exposure classification
Every supported API route SHALL be classified as public, authenticated or permission-restricted, with method-specific matching and default rejection for unclassified routes. Method-level permission enforcement SHALL remain active. Necessary public content, authentication initiation and provider callbacks SHALL remain available through an explicit allowlist.

#### Scenario: Unknown route is introduced without classification
- **WHEN** a request reaches an unclassified route
- **THEN** security configuration SHALL reject it rather than implicitly allow access

#### Scenario: Legitimate visitor browses public content
- **WHEN** an anonymous visitor requests approved public articles, experiences or website shares through listed public routes
- **THEN** those read-only capabilities SHALL remain available

#### Scenario: User attempts an administrator action
- **WHEN** an authenticated user lacks the required permission, regardless of client-type headers
- **THEN** the administrator action SHALL be denied

### Requirement: Cross-origin access is restricted to configured origins
CORS SHALL use exact configured scheme, host and port allowlists, respond with appropriate Vary metadata and necessary methods and headers only, and MUST NOT reflect arbitrary origins. Same-origin and non-browser calls SHALL remain subject to ordinary authentication and authorization.

#### Scenario: Untrusted site sends a preflight
- **WHEN** Origin is absent from the configured cross-origin allowlist
- **THEN** the server SHALL not grant cross-origin access or reflect that origin as allowed

#### Scenario: Approved frontend makes an authenticated request
- **WHEN** a configured origin preflights an allowed method and required headers
- **THEN** the server SHALL permit the intended cross-origin request while retaining normal permission checks

### Requirement: Production pages enforce browser security boundaries
Production responses SHALL include content-type sniffing protection, a restrictive referrer policy and framing restrictions. CSP SHALL move from a documented report-only validation stage to an enforced least-privilege policy compatible with approved resources. HTTPS pages SHALL enable HSTS after HTTPS validation; unverified subdomains MUST NOT be included or preloaded.

#### Scenario: Enforced policy handles an injected script
- **WHEN** a page attempts to execute a script outside the approved sources or nonce/hash policy
- **THEN** the browser SHALL block it without disabling legitimate supported page functionality

#### Scenario: Headers are checked after rollout
- **WHEN** an operator checks normal and error responses over HTTPS
- **THEN** the intended security headers SHALL be present
- **AND** CSP remaining only in report-only mode SHALL be reported as an incomplete enforcement step

#### Scenario: Subdomain coverage is unverified
- **WHEN** only the main site HTTPS behavior has been validated
- **THEN** HSTS SHALL NOT include unverified subdomains or preload registration
