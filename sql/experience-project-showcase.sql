SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `add_experience_showcase_columns`;
DELIMITER $$
CREATE PROCEDURE `add_experience_showcase_columns`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_work_experience' AND COLUMN_NAME = 'company_introduction') THEN
    ALTER TABLE `t_work_experience` ADD COLUMN `company_introduction` text NULL COMMENT '公司介绍' AFTER `highlights`;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_work_experience' AND COLUMN_NAME = 'main_business') THEN
    ALTER TABLE `t_work_experience` ADD COLUMN `main_business` text NULL COMMENT '主营业务' AFTER `company_introduction`;
  END IF;
END$$
DELIMITER ;
CALL `add_experience_showcase_columns`();
DROP PROCEDURE IF EXISTS `add_experience_showcase_columns`;

CREATE TABLE IF NOT EXISTS `t_experience_project` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `experience_id` bigint NOT NULL,
  `project_name` varchar(150) NOT NULL,
  `summary` varchar(500) NOT NULL,
  `cover_image` varchar(500) NULL,
  `start_date` date NULL,
  `end_date` date NULL,
  `role_title` varchar(100) NULL,
  `tech_stack` text NULL,
  `contributions` text NULL,
  `outcomes` text NULL,
  `content` mediumtext NULL,
  `order_num` int NOT NULL DEFAULT 1,
  `status` tinyint NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL,
  `update_time` datetime NOT NULL,
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_experience_project_public` (`experience_id`,`is_deleted`,`status`,`order_num`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='工作经历项目';
