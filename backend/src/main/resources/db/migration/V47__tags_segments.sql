-- ============================================================
-- V47__tags_segments.sql — 客户标签与细分（031-customer-tags）
-- 说明：tag + customer_tag（客户多对多）+ segment（动态细分，JSON 条件）
-- ============================================================

CREATE TABLE `tag` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(50) NOT NULL COMMENT '标签名（按实体唯一）',
  `color`       VARCHAR(20) DEFAULT NULL COMMENT '颜色（antd Tag 色板）',
  `entity_type` VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' COMMENT '适用实体：CUSTOMER/LEAD/CONTACT',
  `created_by`  BIGINT      DEFAULT NULL,
  `deleted`     TINYINT(1)  NOT NULL DEFAULT 0,
  `version`     INT         NOT NULL DEFAULT 0,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name_entity` (`name`, `entity_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `customer_tag` (
  `id`          BIGINT NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT NOT NULL COMMENT '客户',
  `tag_id`      BIGINT NOT NULL COMMENT '标签',
  PRIMARY KEY (`id`),
  KEY `idx_ct_customer` (`customer_id`),
  KEY `idx_ct_tag` (`tag_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `segment` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(50)  NOT NULL COMMENT '细分名（唯一）',
  `description` VARCHAR(255) DEFAULT NULL,
  `conditions`  TEXT         NOT NULL COMMENT 'JSON 条件（logic + filters）',
  `created_by`  BIGINT       DEFAULT NULL,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_segment_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
