-- ============================================================
-- V21__payment_record.sql — 回款记录表（009-order-payment）
-- ============================================================

CREATE TABLE `payment_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `plan_id`     BIGINT       NOT NULL COMMENT '回款计划',
  `order_id`    BIGINT       NOT NULL COMMENT '订单',
  `amount`      BIGINT       NOT NULL COMMENT '回款金额（分，>0）',
  `paid_at`     DATE         NOT NULL COMMENT '回款日期',
  `method`      VARCHAR(20)  NOT NULL DEFAULT 'TRANSFER' COMMENT 'TRANSFER/CASH/CHECK/OTHER',
  `recorded_by` BIGINT       DEFAULT NULL COMMENT '登记人',
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_plan` (`plan_id`),
  KEY `idx_record_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
