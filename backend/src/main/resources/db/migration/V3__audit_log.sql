-- ============================================================
-- V3__audit_log.sql — 关键操作审计日志表（FR-017）
-- 说明：仅记录操作元数据，不记录敏感字段明文内容
-- ============================================================

CREATE TABLE `audit_log` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT,
  `actor_id`    BIGINT       DEFAULT NULL COMMENT '操作人',
  `actor_name`  VARCHAR(50)  DEFAULT NULL COMMENT '操作人用户名',
  `action`      VARCHAR(50)  NOT NULL COMMENT '动作：CREATE/UPDATE/DELETE/IMPORT/EXPORT/CLOSE',
  `entity_type` VARCHAR(50)  NOT NULL COMMENT '对象类型：CUSTOMER/OPPORTUNITY/SALES_OPPORTUNITY',
  `entity_id`   BIGINT       DEFAULT NULL,
  `detail`      VARCHAR(500) DEFAULT NULL COMMENT '补充说明（不含明文敏感数据）',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_entity` (`entity_type`, `entity_id`),
  KEY `idx_audit_created` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
