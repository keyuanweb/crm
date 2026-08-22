-- ============================================================
-- V16__contract.sql — 合同表（008-contract-management）
-- 说明：active_key 生成列实现"非删除记录编号唯一"（与 V6 同模式）
-- ============================================================

CREATE TABLE `contract` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `contract_no`       VARCHAR(30)  NOT NULL COMMENT '合同编号 HT-YYYYMMDD-XXXX',
  `title`             VARCHAR(200) NOT NULL COMMENT '合同标题',
  `customer_id`       BIGINT       NOT NULL COMMENT '客户',
  `quote_id`          BIGINT       DEFAULT NULL COMMENT '关联报价单',
  `amount`            BIGINT       NOT NULL DEFAULT 0 COMMENT '合同金额（分）',
  `start_date`        DATE         DEFAULT NULL COMMENT '生效日期',
  `end_date`          DATE         DEFAULT NULL COMMENT '结束日期',
  `content`           TEXT         DEFAULT NULL COMMENT '合同正文',
  `status`            VARCHAR(30)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_APPROVAL/APPROVED/EFFECTIVE/COMPLETED/TERMINATED',
  `approver_id`       BIGINT       DEFAULT NULL COMMENT '审批人',
  `approved_at`       DATETIME     DEFAULT NULL COMMENT '审批时间',
  `reject_reason`     VARCHAR(500) DEFAULT NULL COMMENT '拒绝意见',
  `effective_at`      DATETIME     DEFAULT NULL COMMENT '生效时间',
  `terminated_reason` VARCHAR(500) DEFAULT NULL COMMENT '终止原因',
  `remark`            VARCHAR(500) DEFAULT NULL,
  `deleted`           TINYINT(1)   NOT NULL DEFAULT 0,
  `version`           INT          NOT NULL DEFAULT 0,
  `created_by`        BIGINT       DEFAULT NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_key`        VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT('c||', contract_no) ELSE NULL END) STORED
                      COMMENT '非删除记录编号唯一（生成列，自动维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contract_active_no` (`active_key`),
  KEY `idx_contract_deleted_customer` (`deleted`, `customer_id`),
  KEY `idx_contract_deleted_status` (`deleted`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
