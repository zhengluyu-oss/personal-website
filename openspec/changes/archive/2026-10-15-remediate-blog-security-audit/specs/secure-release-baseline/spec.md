## ADDED Requirements

### Requirement: Distributable builds exclude private configuration
Backend distributable artifacts SHALL exclude local development credentials and private profile files regardless of Git ignore status. Production deployment SHALL select the production profile explicitly and require externally supplied secrets without development fallbacks. Diagnostic scans SHALL not print secret values.

#### Scenario: Developer has private local configuration
- **WHEN** a normal distributable or production package is built with a gitignored development profile present
- **THEN** the resulting JAR SHALL contain neither that private file nor its secret values
- **AND** local development SHALL retain a documented external configuration path

#### Scenario: Production secret is missing
- **WHEN** production starts without a required credential
- **THEN** startup SHALL fail with a sanitized configuration error instead of using a development value

### Requirement: Dependency remediation uses actual shipped artifacts
The project SHALL maintain reproducible dependency inventories for both frontend builds and the backend runtime, separate development/test/build dependencies from shipped runtime dependencies, and assess official vulnerability advisories against locked versions and reachable code paths. Confirmed reachable high or critical risks SHALL be remediated before release; other residual findings SHALL have documented evidence, mitigation, ownership and review dates, with explicit acceptance where required.

#### Scenario: Audit scanner reports many issues
- **WHEN** dependency audit produces a finding count
- **THEN** the assessment SHALL distinguish scanner matches from confirmed application exposure
- **AND** it SHALL document upgrades or justified residual risks rather than treating the count as confirmed exploitability

#### Scenario: Dependency group is upgraded
- **WHEN** a backend or frontend dependency group is changed
- **THEN** lockfiles and final artifacts SHALL reflect the selected versions
- **AND** Java 17, authentication, Markdown, upload, frontend build and existing business regression checks SHALL pass
- **AND** test-only libraries SHALL NOT appear as backend production dependencies

### Requirement: Operational remediation is gated separately from code changes
The release runbook SHALL specify affected routes, session invalidation, backup, verification, safe rollback and remaining risks. Production deployment, historical log destruction and real credential rotation SHALL require explicit operational authorization after scope and risks are presented.

#### Scenario: Local remediation is completed without deployment authorization
- **WHEN** code and tests are complete but no production operation has been authorized
- **THEN** the report SHALL distinguish locally verified fixes from unexecuted production steps
- **AND** live data and credentials SHALL remain unchanged
