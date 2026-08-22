-- ============================================================
-- V12__product.sql — 产品表（007-product-cpq）
-- 说明：产品目录；active_key 生成列实现"非删除记录编码唯一"（与 V6 同模式）
-- ============================================================

CREATE TABLE `product` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `code`           VARCHAR(50)  NOT NULL COMMENT '产品编码',
  `name`           VARCHAR(100) NOT NULL COMMENT '产品名称',
  `spec`           VARCHAR(255) DEFAULT NULL COMMENT '规格',
  `unit`           VARCHAR(20)  DEFAULT NULL COMMENT '单位',
  `standard_price` BIGINT       NOT NULL DEFAULT 0 COMMENT '标准售价（分）',
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  `version`        INT          NOT NULL DEFAULT 0,
  `created_by`     BIGINT       DEFAULT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_key`     VARCHAR(64)  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT('p||', code) ELSE NULL END) STORED
                  COMMENT '非删除记录编码唯一（生成列，自动维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_active_code` (`active_key`),
  KEY `idx_product_deleted_status` (`deleted`, `status`),
  KEY `idx_product_deleted_name` (`deleted`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
