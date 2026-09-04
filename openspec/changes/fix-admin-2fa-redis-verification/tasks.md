## 1. Backend Redis verification fix

- [x] 1.1 Replace the administrator challenge service's generic JSON-serialized Redis operations with a string-serialized Redis path scoped only to administrator challenge and resend keys
- [x] 1.2 Update challenge read/write types and Lua arguments so the attempt counter, maximum attempts, username, user ID, client binding, and code digest retain deterministic plain-string values
- [x] 1.3 Harden the atomic verification script against missing or non-numeric counters while preserving incorrect-code increment, exhaustion deletion, successful single-use consumption, and replay rejection
- [x] 1.4 Map expected incorrect, expired, consumed, missing, and exhausted challenge outcomes to the existing non-enumerating verification-failure business response instead of HTTP 500

## 2. Backend regression tests

- [x] 2.1 Add a regression test that reproduces the previous Redis serialization failure and proves a newly created challenge can execute the Lua verification path without a nil comparison
- [x] 2.2 Add tests proving a correct code succeeds once, an incorrect code increments attempts, the configured limit invalidates the challenge, and a consumed challenge cannot be replayed
- [x] 2.3 Add endpoint-level tests proving expected challenge failures return a controlled business response without exposing Redis/Lua details or changing the administrator password

## 3. Admin login feedback

- [x] 3.1 Separate first-factor and second-factor error state on the existing admin login page while reusing the current API client and visual components
- [x] 3.2 Keep the email-code step visible after a controlled verification failure, clear the rejected code, and show a concise invalid-or-expired-code message
- [x] 3.3 Remove raw `AxiosError` string concatenation from login notifications and ensure username/password messaging appears only during first-factor failure

## 4. Verification and production rollout

- [x] 4.1 Run the focused backend tests plus the existing backend test suite that covers administrator authentication
- [x] 4.2 Run admin frontend type checking and production build, then verify the login page's password and email-code error states locally
- [x] 4.3 Back up the current production backend JAR and admin static assets, then deploy only the backend and admin frontend without modifying the database, administrator password, or administrator email
- [ ] 4.4 Start a fresh production login challenge and verify email delivery, successful code login, controlled incorrect-code behavior, single-use enforcement, and absence of Redis nil-comparison/HTTP-500 errors in server logs
