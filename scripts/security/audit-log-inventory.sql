-- Read-only inventory. Run only against an explicitly selected database with a SELECT-only user.
-- No values, usernames, URIs or exception messages are returned; counts are indicators, not proof.
START TRANSACTION READ ONLY;
SELECT DATE(create_time) AS audit_day,
       COUNT(*) AS records_scanned,
       SUM(CASE WHEN LOWER(COALESCE(req_parameter, '')) REGEXP 'password|authorization|token|secret|verifycode|"code"' THEN 1 ELSE 0 END) AS request_candidates,
       SUM(CASE WHEN LOWER(COALESCE(return_parameter, '')) REGEXP 'password|authorization|token|secret|verifycode|"code"' THEN 1 ELSE 0 END) AS response_candidates,
       SUM(CASE WHEN exception IS NOT NULL AND exception <> '' THEN 1 ELSE 0 END) AS exception_candidates
FROM sys_log
GROUP BY DATE(create_time)
ORDER BY audit_day;
ROLLBACK;
