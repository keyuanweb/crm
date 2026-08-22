-- ============================================================
-- V18__contract_template.sql — 合同模板表（008-contract-management）
-- ============================================================

CREATE TABLE `contract_template` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(100) NOT NULL COMMENT '模板名称',
  `content`    TEXT         NOT NULL COMMENT '模板正文（含占位符）',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `version`    INT          NOT NULL DEFAULT 0,
  `created_by` BIGINT       DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_template_deleted_status` (`deleted`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
