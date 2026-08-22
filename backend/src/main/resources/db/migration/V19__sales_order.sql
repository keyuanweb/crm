-- ============================================================
-- V19__sales_order.sql — 订单表（009-order-payment）
-- 说明：active_key 生成列实现"非删除记录单号唯一"（与 V6 同模式）
-- ============================================================

CREATE TABLE `sales_order` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `order_no`    VARCHAR(30)  NOT NULL COMMENT '订单号 SO-YYYYMMDD-XXXX',
  `title`       VARCHAR(200) NOT NULL COMMENT '订单标题',
  `customer_id` BIGINT       NOT NULL COMMENT '客户',
  `contract_id` BIGINT       DEFAULT NULL COMMENT '关联合同',
  `amount`      BIGINT       NOT NULL DEFAULT 0 COMMENT '订单金额（分）',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PARTIAL/PAID',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '说明',
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_by`  BIGINT       DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_key`  VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT('o||', order_no) ELSE NULL END) STORED
                COMMENT '非删除记录单号唯一（生成列，自动维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_active_no` (`active_key`),
  KEY `idx_order_deleted_customer` (`deleted`, `customer_id`),
  KEY `idx_order_deleted_status` (`deleted`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
