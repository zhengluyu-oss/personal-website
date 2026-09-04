## Context

The current login endpoint distinguishes frontend and backend behavior using the browser-supplied `X-Client-Type` header. A valid administrator password can therefore enter the backend flow without an independent proof that the requester controls the administrator mailbox. JWTs are kept in a Redis whitelist but are long-lived, and a token was included in the penetration-test evidence. Rate limiting derives identity from forwarding headers that can be supplied by an untrusted client. Production also exposes OpenAPI output, while the public search component inserts transformed result strings through `v-html`.

The project already has email-code delivery, Redis-backed transient state, Spring Security, JWT whitelist storage, Vue login pages, and Nginx reverse proxying. The design reuses those facilities and does not add a third-party authentication system. The owner explicitly requires the existing administrator password to remain unchanged.

## Goals / Non-Goals

**Goals:**

- Require possession of the administrator mailbox in addition to the current password before an administrator JWT is issued.
- Remove client-controlled headers from security decisions and make rate limits resistant to forged proxy addresses.
- Invalidate the exposed token and reduce the impact window of future token disclosure.
- Close production documentation/version leaks and unsafe HTML-rendering paths identified during review.
- Preserve the current frontend/backend architecture and provide automated regression coverage and a reversible deployment sequence.

**Non-Goals:**

- Changing, resetting, displaying, or migrating the administrator password.
- Adding SMS, authenticator-app TOTP, passkeys, a new identity provider, or a new authentication framework.
- Redesigning unrelated blog pages or changing public article APIs.
- Blindly deleting production records based only on report IDs; cleanup requires identity verification and backup.

## Decisions

### Use a two-step challenge instead of issuing a token after password validation

After credentials are valid, the server checks the account's server-side role. A non-administrator follows the existing public login behavior. An administrator receives a short-lived, opaque `challengeId`; the server sends a code to the email already bound to that account and does not issue a JWT. A dedicated verification endpoint accepts `challengeId` and code, consumes the challenge atomically, and only then uses the existing success handler to issue an administrator JWT.

The challenge record in Redis contains only the account ID, a cryptographic hash of the code, expiry, attempt count, purpose, and binding metadata. Responses expose a masked email only. Codes expire after five minutes, are single-use, have a maximum of five attempts, and use a sixty-second resend cooldown. Successful verification, exhaustion, or expiry removes the record. Login and verification failures use non-enumerating messages.

Alternative considered: fixed-IP restriction. It was rejected because it can lock out an administrator on dynamic/mobile networks and would make availability depend on network location. Email verification reuses existing delivery and proves control of a second channel.

### Treat roles as the only source of account privilege

`X-Client-Type` is removed from login authorization and from CORS/header injection where no longer needed. Backend eligibility is loaded from persisted roles/permissions. The header may not select a privileged handler, bypass a control, or affect token claims.

Alternative considered: signing the client-type header. It was rejected because a browser cannot safely hold a signing secret and the header encodes presentation context, not identity.

### Keep transient second-factor state in Redis

No database migration is needed. The existing email service and Redis cache are reused with distinct key namespaces for admin challenges, resend cooldowns, and attempt limits. Atomic consume/increment semantics prevent replay and concurrent double use.

Alternative considered: storing codes in the user table. It was rejected because codes are short-lived secrets and do not belong in durable account data or backups.

### Establish one trusted proxy boundary for client identity

Nginx overwrites the upstream client-address header from the connection address instead of appending arbitrary inbound values. The backend uses the trusted proxy-provided address when deployed behind Nginx and falls back to the socket remote address outside that topology. Arbitrary `X-Forwarded-For`, `Proxy-Client-IP`, and similar headers are not independently trusted.

Rate-limit keys combine operation purpose with normalized account/email identity and trusted client address. This prevents changing only one input from resetting all limits.

### Use existing JWT whitelist as the session kill switch

Administrator access tokens receive a shorter, configurable lifetime than ordinary public-user tokens. The rollout rotates the production signing secret and clears existing JWT whitelist/session entries after a backup of required operational metadata. This invalidates the token in the report without changing the password. No old signing key remains accepted.

Alternative considered: only waiting for the leaked token to expire. It was rejected because the token remains usable during that window and its distribution is unknown.

### Remove executable markup from search highlighting

Search results retain raw text and derive an array of plain-text matched/unmatched segments for Vue to render with normal interpolation and styled elements. No API text is concatenated into markup and no `v-html` is used for highlighting. Comment/tree-hole values are treated as inert text in public and moderation views; article Markdown remains a separate trusted publishing path and is reviewed for sanitizer boundaries.

Alternative considered: sanitizing the generated highlight HTML. Segment rendering is simpler, avoids an HTML parser dependency, and makes the safe default explicit.

### Defense in depth at both application and reverse proxy

Production configuration disables Springdoc API docs and Swagger UI. Nginx also returns a non-success response for known documentation paths and disables version tokens. Development documentation remains available only in the development profile.

## Risks / Trade-offs

- [Email delivery outage can block administrator login] → Preserve an already authenticated session until normal expiry during routine operation, monitor delivery errors, document a local server recovery procedure, and never fall back to password-only login over the public network.
- [The unchanged known password remains a weak first factor] → Enforce email verification for every administrator session, shorten admin JWT lifetime, throttle both steps, and clearly record this residual risk.
- [JWT secret rotation logs out every user] → Announce the maintenance window, perform the rotation once, verify login immediately, and treat universal logout as intentional containment.
- [Incorrect proxy trust configuration can collapse clients to one address or trust spoofed data] → Add unit/integration tests for direct and proxied requests and verify the deployed Nginx header behavior before enabling strict limits.
- [Aggressive limits can lock out the owner] → Scope counters by operation and identity, use bounded cooldowns rather than permanent locks, and provide observable audit events without logging codes.
- [Removing `v-html` can change highlight appearance] → Preserve current matched-text styling with segment components and visual regression checks.
- [Production record cleanup is destructive] → Export matching rows, compare payload/timestamps with the report, obtain explicit deployment-time confirmation, then delete only exact test artifacts.

## Migration Plan

1. Implement and test the two-step administrator login, safe role derivation, trusted-address resolution, rate limits, safe highlighting, and production configuration without deploying.
2. Back up production configuration and relevant database/Redis metadata. Identify the administrator mailbox and verify that it can receive a test message without altering the password.
3. Deploy backend, admin frontend, and Nginx configuration together while retaining the current signing key for this first restart only; verify that password-only administrator login cannot issue a token and email verification succeeds.
4. Rotate the production JWT signing secret, clear JWT whitelist/session entries, restart the backend, and confirm the penetration-test token and all pre-rotation tokens are rejected.
5. Verify Swagger endpoints are unavailable, spoofed forwarding headers do not reset limits, and XSS test payloads render as text.
6. Audit authentication logs, roles, comments, and tree-hole records. Back up and conditionally remove only confirmed test artifacts.

Rollback restores application binaries and Nginx configuration but retains mandatory administrator email verification and the new JWT secret. If email verification is unavailable, recovery is performed through controlled server access; public password-only administrator login is not restored.

## Open Questions

- Confirm the production administrator account has a unique, reachable email address before deployment.
- Choose the final administrator JWT lifetime during implementation; the default target is 30 minutes and MUST remain separately configurable from public-user sessions.
