-- ============================================================
-- V20__payment_plan.sql — 回款计划期次表（009-order-payment）
-- ============================================================

CREATE TABLE `payment_plan` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT       NOT NULL COMMENT '订单',
  `seq_no`      INT          NOT NULL COMMENT '期次序号',
  `amount`      BIGINT       NOT NULL COMMENT '应收金额（分）',
  `due_date`    DATE         NOT NULL COMMENT '计划回款日期',
  `description` VARCHAR(200) DEFAULT NULL COMMENT '期次说明',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARTIAL/PAID',
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_plan_order` (`order_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
