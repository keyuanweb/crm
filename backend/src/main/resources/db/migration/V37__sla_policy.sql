-- ============================================================
-- V37__sla_policy.sql — SLA 策略表（015-customer-service）
-- ============================================================

CREATE TABLE `sla_policy` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `priority`      VARCHAR(20) NOT NULL COMMENT 'LOW/MEDIUM/HIGH/URGENT（唯一）',
  `respond_hours` INT         DEFAULT NULL COMMENT '响应时限（小时，null=不约束）',
  `resolve_hours` INT         DEFAULT NULL COMMENT '解决时限（小时，null=不约束）',
  `enabled`       TINYINT(1)  NOT NULL DEFAULT 1,
  `deleted`       TINYINT(1)  NOT NULL DEFAULT 0,
  `version`       INT         NOT NULL DEFAULT 0,
  `created_by`    BIGINT      DEFAULT NULL,
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sla_policy_priority` (`priority`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
