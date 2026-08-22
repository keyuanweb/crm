-- ============================================================
-- V26__customer_share.sql — 客户共享表（012-data-permission）
-- 说明：active_key 生成列实现"客户+用户"非删除唯一（与 V6 同模式）
-- ============================================================

CREATE TABLE `customer_share` (
  `id`                 BIGINT      NOT NULL AUTO_INCREMENT,
  `customer_id`        BIGINT      NOT NULL COMMENT '客户',
  `shared_to_user_id`  BIGINT      NOT NULL COMMENT '共享给用户',
  `shared_by`          BIGINT      DEFAULT NULL COMMENT '共享人',
  `deleted`            TINYINT(1)  NOT NULL DEFAULT 0,
  `version`            INT         NOT NULL DEFAULT 0,
  `created_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_key`         VARCHAR(64) GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT(customer_id, '||', shared_to_user_id) ELSE NULL END) STORED
                       COMMENT '客户+用户非删除唯一（生成列，自动维护）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_customer_user` (`active_key`),
  KEY `idx_share_customer` (`customer_id`),
  KEY `idx_share_to_user` (`shared_to_user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
