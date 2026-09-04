-- 定时导出订阅（079-scheduled-export）

CREATE TABLE IF NOT EXISTS `scheduled_export` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `entity_type` VARCHAR(50) NOT NULL,
  `filter_conditions` JSON,
  `export_format` VARCHAR(10) NOT NULL,
  `cron_expression` VARCHAR(100) NOT NULL,
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `next_execution_time` DATETIME,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_se_user` (`user_id`),
  INDEX `idx_se_status` (`status`),
  INDEX `idx_se_next_exec` (`next_execution_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `scheduled_export_execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `scheduled_export_id` BIGINT NOT NULL,
  `executed_at` DATETIME NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `file_path` VARCHAR(500),
  `file_size` BIGINT,
  `row_count` INT,
  `email_status` VARCHAR(20),
  `error_message` TEXT,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_see_export` (`scheduled_export_id`),
  INDEX `idx_see_executed` (`executed_at`),
  CONSTRAINT `fk_see_export` FOREIGN KEY (`scheduled_export_id`) REFERENCES `scheduled_export` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
