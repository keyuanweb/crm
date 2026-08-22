-- ============================================================
-- V39__custom_field_value.sql — 自定义字段值表（016-system-enhancement）
-- ============================================================

CREATE TABLE `custom_field_value` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `field_id`    BIGINT        NOT NULL COMMENT '字段定义 id',
  `entity_type` VARCHAR(30)   NOT NULL COMMENT 'LEAD/CUSTOMER/OPPORTUNITY/TICKET',
  `entity_id`   BIGINT        NOT NULL COMMENT '实体记录 id',
  `field_value` VARCHAR(1000) DEFAULT NULL COMMENT '字符串值',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_entity_value` (`field_id`, `entity_id`),
  KEY `idx_value_entity` (`entity_type`, `entity_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
