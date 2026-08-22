-- ============================================================
-- V11__sales_target.sql — 销售目标表（006-sales-dashboard）
-- 说明：按月份设置业绩目标（YYYY-MM）；逻辑删除 + 乐观锁；
--       active_key 生成列实现"非删除记录 target_month 唯一"（与 V6 同模式）
-- ============================================================

CREATE TABLE `sales_target` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `target_month`  VARCHAR(7)   NOT NULL COMMENT '目标月份 YYYY-MM',
  `target_amount` BIGINT       NOT NULL COMMENT '目标金额（分）',
  `deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  `version`       INT          NOT NULL DEFAULT 0,
  `created_by`    BIGINT       DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_key`    VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT('m||', target_month) ELSE NULL END) STORED
                 COMMENT '非删除记录按月唯一（生成列，自动维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sales_target_active_month` (`active_key`),
  KEY `idx_sales_target_deleted_month` (`deleted`, `target_month`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
