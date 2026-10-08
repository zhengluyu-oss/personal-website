-- Authorized additive migration; not run automatically at application startup.
-- Run the read-only preflight first and take a restricted backup.
-- Existing collation and email values are deliberately unchanged.
-- A failed CALL may leave this migration-specific helper; a retry recreates it.
DROP PROCEDURE IF EXISTS `migrate_security_email_uniqueness`;
DELIMITER $$
CREATE PROCEDURE `migrate_security_email_uniqueness`()
BEGIN
  IF EXISTS (SELECT 1 FROM sys_user WHERE email IS NOT NULL
             GROUP BY email HAVING COUNT(*) > 1) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Email uniqueness preflight failed: duplicate groups exist; no accounts modified';
  END IF;
  IF EXISTS (SELECT 1 FROM sys_user WHERE email IS NOT NULL AND TRIM(email) = '') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Email uniqueness preflight failed: blank addresses exist; no accounts modified';
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
             AND INDEX_NAME = 'uk_sys_user_email') THEN
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
        AND INDEX_NAME = 'uk_sys_user_email') <> 1
       OR NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
          AND INDEX_NAME = 'uk_sys_user_email' AND NON_UNIQUE = 0
          AND COLUMN_NAME = 'email' AND SEQ_IN_INDEX = 1 AND SUB_PART IS NULL) THEN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unexpected email index definition; manual review required';
    END IF;
  ELSE
    -- Atomic DDL fails if a concurrent writer introduces a duplicate after preflight.
    ALTER TABLE sys_user ADD UNIQUE INDEX uk_sys_user_email (email);
  END IF;
END$$
DELIMITER ;
CALL migrate_security_email_uniqueness();
DROP PROCEDURE migrate_security_email_uniqueness;
