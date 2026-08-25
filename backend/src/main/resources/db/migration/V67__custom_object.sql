-- ============================================================
-- V67__custom_object.sql — 自定义对象（059-custom-object）
-- ============================================================

CREATE TABLE `custom_object` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(100) NOT NULL,
  `code`       VARCHAR(50)  NOT NULL,
  `fields`     TEXT         NOT NULL COMMENT '字段集 JSON',
  `enabled`    TINYINT(1)   NOT NULL DEFAULT 1,
  `deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `version`    INT          NOT NULL DEFAULT 0,
  `created_by` BIGINT       DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_object_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `custom_object_record` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT,
  `object_id`      BIGINT   NOT NULL,
  `record_values`  TEXT     NOT NULL COMMENT '记录值 JSON 键值对',
  `created_by`     BIGINT   DEFAULT NULL,
  `created_at`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_record_object` (`object_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
