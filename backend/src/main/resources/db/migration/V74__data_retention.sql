-- 数据保留策略（080-data-retention）

CREATE TABLE IF NOT EXISTS `data_retention_policy` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `entity_type` VARCHAR(50) NOT NULL,
  `retention_days` INT NOT NULL,
  `action_type` VARCHAR(20) NOT NULL DEFAULT 'ARCHIVE',
  `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE INDEX `idx_drp_entity` (`entity_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `data_retention_execution` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `policy_id` BIGINT NOT NULL,
  `executed_at` DATETIME NOT NULL,
  `status` VARCHAR(20) NOT NULL,
  `processed_count` INT DEFAULT 0,
  `error_message` TEXT,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_dre_policy` (`policy_id`),
  INDEX `idx_dre_executed` (`executed_at`),
  CONSTRAINT `fk_dre_policy` FOREIGN KEY (`policy_id`) REFERENCES `data_retention_policy` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
