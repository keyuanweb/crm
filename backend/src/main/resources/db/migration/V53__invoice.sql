-- ============================================================
-- V53__invoice.sql — 发票管理（038-invoice）
-- 说明：订单开票记录
-- ============================================================

CREATE TABLE `invoice` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT       NOT NULL COMMENT '订单',
  `invoice_no`   VARCHAR(30)  NOT NULL COMMENT '编号 INV-{yyyyMM}-{seq}',
  `title`        VARCHAR(200) NOT NULL COMMENT '抬头',
  `tax_no`       VARCHAR(50)  DEFAULT NULL COMMENT '税号',
  `amount`       BIGINT       NOT NULL COMMENT '金额（分）',
  `invoice_type` VARCHAR(20)  NOT NULL DEFAULT 'GENERAL' COMMENT 'GENERAL(普票)/SPECIAL(专票)',
  `status`       VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/ISSUED/VOID',
  `void_reason`  VARCHAR(255) DEFAULT NULL COMMENT '作废原因',
  `issued_at`    DATETIME     DEFAULT NULL,
  `voided_at`    DATETIME     DEFAULT NULL,
  `created_by`   BIGINT       DEFAULT NULL,
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  `version`      INT          NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invoice_no` (`invoice_no`),
  KEY `idx_invoice_order` (`order_id`),
  KEY `idx_invoice_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
