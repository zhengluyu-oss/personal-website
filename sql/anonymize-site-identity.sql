-- Display identity: 陆屿. Run only after a restricted backup of both tables.
-- Keep infrastructure identifiers, credentials, and database structure unchanged.
-- The previous display name is encoded to avoid retaining it in repository text.
SET NAMES utf8mb4;
SET @previous_display_name = CONVERT(0xe98391e99986e5ae87 USING utf8mb4);
SET @public_display_name = CONVERT(0xe99986e5b1bf USING utf8mb4);

START TRANSACTION;
UPDATE sys_website_info
SET webmaster_name = REPLACE(webmaster_name, @previous_display_name, @public_display_name)
WHERE LOCATE(@previous_display_name, webmaster_name) > 0;
UPDATE sys_log
SET req_parameter = REPLACE(req_parameter, @previous_display_name, @public_display_name)
WHERE LOCATE(@previous_display_name, req_parameter) > 0;
UPDATE sys_log
SET return_parameter = REPLACE(return_parameter, @previous_display_name, @public_display_name)
WHERE LOCATE(@previous_display_name, return_parameter) > 0;
COMMIT;

SELECT 'website_name_remaining', COUNT(*) FROM sys_website_info
WHERE LOCATE(@previous_display_name, webmaster_name) > 0;
SELECT 'request_log_remaining', COUNT(*) FROM sys_log
WHERE LOCATE(@previous_display_name, req_parameter) > 0;
SELECT 'response_log_remaining', COUNT(*) FROM sys_log
WHERE LOCATE(@previous_display_name, return_parameter) > 0;
