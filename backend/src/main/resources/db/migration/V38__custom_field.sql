-- ============================================================
-- V38__custom_field.sql — 自定义字段定义表（016-system-enhancement）
-- ============================================================

CREATE TABLE `custom_field` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `entity_type` VARCHAR(30)  NOT NULL COMMENT 'LEAD/CUSTOMER/OPPORTUNITY/TICKET',
  `name`        VARCHAR(50)  NOT NULL COMMENT '字段名称（实体内唯一）',
  `field_type`  VARCHAR(20)  NOT NULL COMMENT 'TEXT/TEXTAREA/NUMBER/DATE/SELECT',
  `required`    TINYINT(1)   NOT NULL DEFAULT 0,
  `options`     VARCHAR(1000) DEFAULT NULL COMMENT 'SELECT 选项（逗号分隔）',
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_order`  INT          NOT NULL DEFAULT 0,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_by`  BIGINT       DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_entity_name` (`entity_type`, `name`, `deleted`),
  KEY `idx_field_entity_enabled` (`entity_type`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
