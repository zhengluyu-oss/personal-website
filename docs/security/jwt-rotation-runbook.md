# JWT signing-key rotation runbook

This procedure invalidates every existing login session. It does not change any user password.

1. Schedule a maintenance window and confirm administrator email delivery works.
2. Back up the active deployment configuration and export the Redis key names matching `jwt:white:list:*` without exporting token values into logs.
3. Generate a new high-entropy JWT signing secret in the server secret store. Never commit it to Git or paste it into deployment output.
4. Deploy the application and Nginx hardening first, and verify that an administrator password produces only an email challenge.
5. Replace the production `spring.security.jwt.key`, delete all `jwt:white:list:*` keys, and restart the backend.
6. Confirm a pre-rotation token is rejected and a new administrator session requires password plus email code.
7. Confirm `/api/v3/api-docs`, `/api/swagger-ui*`, and `/api/doc.html` do not expose documentation.

Rollback may restore application binaries and Nginx configuration, but must retain the new signing key and mandatory administrator second factor. If email is unavailable, recover through authenticated server access; do not restore public password-only administrator login.
