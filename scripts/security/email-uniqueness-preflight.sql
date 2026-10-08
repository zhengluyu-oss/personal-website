-- Read-only. Includes disabled and soft-deleted accounts, as existing lookup does.
START TRANSACTION READ ONLY;
SELECT COUNT(*) AS duplicate_email_groups, COALESCE(SUM(account_count), 0) AS conflicting_accounts
FROM (SELECT COUNT(*) AS account_count FROM sys_user
      WHERE email IS NOT NULL GROUP BY email HAVING COUNT(*) > 1) AS duplicates;
SELECT COUNT(*) AS blank_email_accounts FROM sys_user WHERE email IS NOT NULL AND TRIM(email) = '';
ROLLBACK;
