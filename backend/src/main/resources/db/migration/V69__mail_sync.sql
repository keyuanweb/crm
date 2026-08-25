-- ============================================================
-- V69__mail_sync.sql — 邮件账户与同步记录（062-email-sync）
-- ============================================================

CREATE TABLE `mail_account` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `email`             VARCHAR(100) NOT NULL,
  `display_name`      VARCHAR(100) NOT NULL,
  `imap_host`         VARCHAR(100) DEFAULT NULL,
  `imap_port`         INT          DEFAULT NULL,
  `smtp_host`         VARCHAR(100) DEFAULT NULL,
  `smtp_port`         INT          DEFAULT NULL,
  `enabled`           TINYINT(1)   NOT NULL DEFAULT 1,
  `is_default_sender` TINYINT(1)   NOT NULL DEFAULT 0,
  `created_by`        BIGINT       DEFAULT NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mail_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `mail_sync_record` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `account_id`   BIGINT       NOT NULL,
  `direction`    VARCHAR(20)  NOT NULL COMMENT 'INBOUND/OUTBOUND',
  `subject`      VARCHAR(200) DEFAULT NULL,
  `from_address` VARCHAR(100) DEFAULT NULL,
  `to_address`   VARCHAR(100) DEFAULT NULL,
  `sync_status`  VARCHAR(20)  NOT NULL COMMENT 'SYNCED/FAILED',
  `external_id`  VARCHAR(100) DEFAULT NULL,
  `sync_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_sync_account` (`account_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
