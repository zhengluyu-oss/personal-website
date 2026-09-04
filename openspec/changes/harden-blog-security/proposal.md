## Why

The penetration-test findings show that administrative access can be exposed through a combination of a known/default credential, a client-controlled request header, long-lived bearer tokens, and weak trust boundaries around proxy headers. The same review also found publicly exposed API documentation and unsafe HTML rendering paths, so containment and durable security controls are needed before the leaked access can be considered invalidated.

## What Changes

- Require a second, short-lived email verification code after a valid administrator password before issuing an administrator session; the existing administrator password is explicitly not changed by this work.
- Stop using the client-controlled `X-Client-Type` header as an authentication or authorization decision input; derive administrator access exclusively from server-side account roles and the completed second factor.
- Add bounded attempts, expiry, single-use consumption, resend cooldown, and abuse controls to administrator email verification.
- Rotate the production JWT signing secret during rollout, shorten administrator token lifetime, and invalidate all previously issued administrator tokens and cached sessions.
- Resolve client identity only through the trusted reverse-proxy boundary so forged forwarding headers cannot bypass verification-code or login throttling.
- Disable Swagger/OpenAPI endpoints in production and suppress unnecessary server-version disclosure.
- Replace unsafe search-result HTML injection with text-safe highlighting and verify other public content-rendering paths do not execute stored or reflected markup.
- Audit the penetration-test administrator token, authentication events, role assignments, comments, and tree-hole records; remove test artifacts only after exact identification and backup.
- Add regression tests and deployment verification for authentication, rate limiting, documentation exposure, token invalidation, and XSS handling.
- **BREAKING**: Administrator login becomes a two-step flow and existing administrator sessions are invalidated at deployment.

## Capabilities

### New Capabilities

- `admin-email-second-factor`: Two-step administrator authentication using the existing email delivery capability without changing the administrator password.
- `trusted-client-rate-limiting`: Trusted-proxy client-address resolution and abuse-resistant limits for login and verification-code operations.
- `production-interface-hardening`: Production-only suppression of API documentation, version disclosure, and previously issued administrative sessions.
- `safe-content-rendering`: Text-safe search highlighting and validation of user-controlled content rendering against script execution.

### Modified Capabilities

- `comment-moderation`: Ensure comment and tree-hole content remains inert text throughout submission, moderation, and public rendering, and define safe handling of identified penetration-test records.

## Impact

- Backend authentication, JWT issuance/validation, verification-code delivery and storage, request throttling, client-IP resolution, security configuration, and audit logging.
- Administrator login UI and API contract change from one-step login to password verification followed by email-code verification.
- Frontend search-result rendering and any shared content-display utilities that currently accept raw HTML.
- Production Springdoc and Nginx configuration, JWT secret deployment procedure, Redis/session cache, and operational rollback documentation.
- No administrator password modification and no new third-party framework are included. Because the password remains known, email delivery availability and second-factor enforcement become critical controls; rollback must not re-enable password-only administrator login.
