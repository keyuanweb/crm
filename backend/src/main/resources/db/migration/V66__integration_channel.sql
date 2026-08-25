-- ============================================================
-- V66__integration_channel.sql — 集成中心（058-integration-hub）
-- ============================================================

CREATE TABLE `integration_channel` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `channel_type` VARCHAR(20)  NOT NULL COMMENT 'WECHAT_WORK/DINGTALK/CUSTOM',
  `name`         VARCHAR(100) NOT NULL,
  `webhook_url`  VARCHAR(500) NOT NULL,
  `enabled`      TINYINT(1)   NOT NULL DEFAULT 1,
  `created_by`   BIGINT       DEFAULT NULL,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
