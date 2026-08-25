-- ============================================================
-- V64__field_permission.sql — 字段级权限（056-field-permission）
-- ============================================================

CREATE TABLE `field_permission` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `role_code`   VARCHAR(30) NOT NULL COMMENT '角色编码（ADMIN/SALES/SUPPORT 等）',
  `entity_type` VARCHAR(20) NOT NULL,
  `field_id`    BIGINT      NOT NULL,
  `permission`  VARCHAR(20) NOT NULL COMMENT 'HIDDEN/READ_ONLY/EDITABLE',
  `created_by`  BIGINT      DEFAULT NULL,
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_perm` (`role_code`, `entity_type`, `field_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
