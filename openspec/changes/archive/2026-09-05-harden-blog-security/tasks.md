## 1. Authentication Boundary

- [x] 1.1 Add administrator second-factor request/response DTOs and explicit Redis key constants without adding or changing password fields
- [x] 1.2 Refactor credential validation so persisted roles determine administrator status and `X-Client-Type` cannot select privileges or token claims
- [x] 1.3 Implement hashed, five-minute administrator email challenges with opaque IDs, masked destinations, single-use atomic consumption, five-attempt exhaustion, and sixty-second resend cooldown
- [x] 1.4 Add the administrator challenge verification endpoint and issue an administrator JWT only after successful second-factor verification
- [x] 1.5 Preserve normal non-administrator login behavior and normalize authentication errors to avoid account or factor enumeration
- [x] 1.6 Remove obsolete `X-Client-Type` injection and CORS allowances from both frontend clients after backend dependence is eliminated

## 2. Administrator Login Experience

- [x] 2.1 Update the admin login API client and state model to handle password-challenge and code-verification responses
- [x] 2.2 Add the email-code step to the existing admin login page with masked destination, resend countdown, pending/error states, and accessible keyboard behavior
- [x] 2.3 Ensure refresh, back navigation, expiry, and successful verification clear challenge state without persisting codes or passwords

## 3. Trusted Client Identity and Abuse Controls

- [x] 3.1 Replace permissive proxy-header parsing with trusted-proxy client-address resolution and socket-address fallback
- [x] 3.2 Configure Nginx to overwrite the backend client-address header and prevent inbound forwarding-header spoofing
- [x] 3.3 Extend rate-limit keys and policies for password login, challenge creation, resend, and verification using operation, normalized identity, and trusted client address
- [x] 3.4 Add sanitized audit events for challenge creation, verification success/failure, exhaustion, throttling, and administrator login without logging credentials or codes

## 4. Session and Production Interface Hardening

- [x] 4.1 Add a separately configurable administrator JWT lifetime with a secure default target of thirty minutes while preserving supported public-user configuration
- [x] 4.2 Disable Springdoc API docs and Swagger UI in the production profile while retaining explicit development-profile access
- [x] 4.3 Add Nginx blocks for known Swagger/OpenAPI routes and suppress detailed Nginx version tokens
- [x] 4.4 Document the production JWT-secret rotation and JWT whitelist/session invalidation procedure, including backup, verification, and rollback constraints

## 5. Safe Content Rendering

- [x] 5.1 Replace all search-result `v-html` highlighting with escaped matched/unmatched text segments while preserving the visual highlight treatment
- [x] 5.2 Audit public and admin comment/tree-hole rendering paths and convert user-generated values to inert text wherever executable HTML is possible
- [x] 5.3 Audit article Markdown rendering and enforce sanitization for raw HTML, event handlers, and unsafe URL schemes at the trusted publishing boundary
- [x] 5.4 Add frontend regression tests covering malicious titles, excerpts, comments, tree-hole content, and article Markdown payloads

## 6. Automated Security Verification

- [x] 6.1 Add backend tests proving a valid administrator password alone and forged `X-Client-Type` cannot issue an administrator token
- [x] 6.2 Add backend tests for challenge expiry, incorrect-attempt exhaustion, resend cooldown, atomic single use, masked responses, and unchanged password hashes
- [x] 6.3 Add proxy and rate-limit tests proving spoofed forwarding headers cannot reset account-level or client-level limits
- [x] 6.4 Add configuration tests proving production documentation routes are disabled and development documentation remains explicitly available
- [x] 6.5 Run backend tests plus admin and blog frontend type-check/build suites and record any unrelated pre-existing failures separately

## 7. Controlled Production Rollout

- [ ] 7.1 Back up production configuration and required database/Redis metadata, verify the administrator mailbox receives email, and verify the stored password hash is unchanged
- [ ] 7.2 Deploy backend, admin frontend, blog frontend, and Nginx changes together and confirm password-only administrator login is rejected while email verification succeeds
- [ ] 7.3 Rotate the production JWT signing secret, clear accepted JWT/session state, restart the backend, and verify all pre-rotation tokens including the reported token are rejected
- [ ] 7.4 Verify production Swagger/OpenAPI routes, version disclosure, forwarding-header spoofing, rate limits, and XSS payload rendering from outside the server
- [ ] 7.5 Audit authentication events, role assignments, comments, and tree-hole records against the penetration report; export exact matches and request explicit confirmation before deleting any confirmed test artifacts
- [ ] 7.6 Document final security checks, remaining risk from the unchanged known password, recovery steps for email outages, and deployment evidence
