-- ============================================================
-- V55__stage_action_template.sql — 阶段动作模板（045-sales-playbook）
-- ============================================================

CREATE TABLE `stage_action_template` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT,
  `stage`       VARCHAR(30)   NOT NULL COMMENT 'INITIAL_CONTACT/NEGOTIATING（仅活动阶段）',
  `action_name` VARCHAR(100)  NOT NULL COMMENT '动作名称',
  `description` VARCHAR(500)  DEFAULT NULL COMMENT '动作描述',
  `sort_order`  INT           NOT NULL DEFAULT 0,
  `required`    TINYINT(1)    NOT NULL DEFAULT 0,
  `enabled`     TINYINT(1)    NOT NULL DEFAULT 1,
  `deleted`     TINYINT(1)    NOT NULL DEFAULT 0,
  `version`     INT           NOT NULL DEFAULT 0,
  `created_by`  BIGINT        DEFAULT NULL,
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_template_stage_enabled` (`stage`, `enabled`),
  KEY `idx_template_stage_sort` (`stage`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
