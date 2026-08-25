-- ============================================================
-- V63__open_platform.sql — 开放平台（055-open-platform）
-- ============================================================

CREATE TABLE `api_key` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `name`         VARCHAR(100) NOT NULL,
  `key_hash`     VARCHAR(128) NOT NULL,
  `key_prefix`   VARCHAR(16)  NOT NULL,
  `scopes`       TEXT         DEFAULT NULL,
  `expires_at`   DATETIME     DEFAULT NULL,
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  `last_used_at` DATETIME     DEFAULT NULL,
  `use_count`    BIGINT       NOT NULL DEFAULT 0,
  `created_by`   BIGINT       DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_api_key_hash` (`key_hash`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `webhook_subscription` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `event_type`   VARCHAR(50)  NOT NULL,
  `callback_url` VARCHAR(500) NOT NULL,
  `secret`       VARCHAR(64)  NOT NULL,
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
  `created_by`   BIGINT       DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_webhook_event` (`event_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `webhook_delivery` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `subscription_id` BIGINT       NOT NULL,
  `event_type`      VARCHAR(50)  NOT NULL,
  `entity_type`     VARCHAR(30)  DEFAULT NULL,
  `entity_id`       BIGINT       DEFAULT NULL,
  `payload`         TEXT         NOT NULL,
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS',
  `http_status`     INT          DEFAULT NULL,
  `error`           VARCHAR(500) DEFAULT NULL,
  `retry_count`     INT          NOT NULL DEFAULT 0,
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_delivery_sub` (`subscription_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
