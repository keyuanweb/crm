-- ============================================================
-- V24__department.sql — 部门表（012-data-permission）
-- 说明：树形单上级；逻辑删除 + 乐观锁
-- ============================================================

CREATE TABLE `department` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(50) NOT NULL COMMENT '部门名称',
  `parent_id`  BIGINT      DEFAULT NULL COMMENT '上级部门（空=顶级）',
  `deleted`    TINYINT(1)  NOT NULL DEFAULT 0,
  `version`    INT         NOT NULL DEFAULT 0,
  `created_by` BIGINT      DEFAULT NULL,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_department_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
