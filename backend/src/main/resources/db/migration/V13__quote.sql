-- ============================================================
-- V13__quote.sql — 报价单表（007-product-cpq）
-- 说明：报价单头；active_key 生成列实现"非删除记录单号唯一"
-- ============================================================

CREATE TABLE `quote` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `quote_no`      VARCHAR(30)  NOT NULL COMMENT '报价单号 Q-YYYYMMDD-XXXX',
  `customer_id`   BIGINT       NOT NULL COMMENT '客户',
  `opportunity_id` BIGINT      DEFAULT NULL COMMENT '商机（可选）',
  `valid_until`   DATE         DEFAULT NULL COMMENT '有效期',
  `status`        VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_APPROVAL/APPROVED/REJECTED',
  `total_amount`  BIGINT       NOT NULL DEFAULT 0 COMMENT '总额（分）',
  `remark`        VARCHAR(500) DEFAULT NULL,
  `approver_id`   BIGINT       DEFAULT NULL COMMENT '审批人',
  `approved_at`   DATETIME     DEFAULT NULL COMMENT '审批时间',
  `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '拒绝意见',
  `deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  `version`       INT          NOT NULL DEFAULT 0,
  `created_by`    BIGINT       DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_key`    VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT('q||', quote_no) ELSE NULL END) STORED
                  COMMENT '非删除记录单号唯一（生成列，自动维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_quote_active_no` (`active_key`),
  KEY `idx_quote_deleted_customer` (`deleted`, `customer_id`),
  KEY `idx_quote_deleted_status` (`deleted`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
