-- ============================================================
-- V68__call_record.sql — 通话记录（061-call-center）
-- ============================================================

CREATE TABLE `call_record` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id`      BIGINT       DEFAULT NULL,
  `contact_id`       BIGINT       DEFAULT NULL,
  `direction`        VARCHAR(20)  NOT NULL COMMENT 'INBOUND/OUTBOUND',
  `duration_seconds` INT          NOT NULL DEFAULT 0,
  `result`           VARCHAR(20)  NOT NULL COMMENT 'CONNECTED/NO_ANSWER/BUSY/FAILED',
  `remark`           VARCHAR(500) DEFAULT NULL,
  `recorded_by`      BIGINT       NOT NULL,
  `recorded_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_call_customer` (`customer_id`),
  KEY `idx_call_recorded_at` (`recorded_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
