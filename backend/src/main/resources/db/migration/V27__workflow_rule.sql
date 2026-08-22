-- ============================================================
-- V27__workflow_rule.sql — 工作流规则表（013-workflow-automation）
-- 说明：触发事件 + 条件 JSON + 动作 JSON；逻辑删除 + 乐观锁
-- ============================================================

CREATE TABLE `workflow_rule` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `name`           VARCHAR(100) NOT NULL COMMENT '规则名称',
  `event_type`     VARCHAR(40)  NOT NULL COMMENT 'LEAD_CREATED/OPPORTUNITY_STAGE_CHANGED/FOLLOW_UP_CREATED/PAYMENT_RECORDED',
  `condition_json` TEXT         DEFAULT NULL COMMENT '条件 JSON（field/value）',
  `action_type`    VARCHAR(30)  NOT NULL COMMENT 'CREATE_TASK/ASSIGN/NOTIFY',
  `action_json`    TEXT         NOT NULL COMMENT '动作 JSON',
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  `version`        INT          NOT NULL DEFAULT 0,
  `created_by`     BIGINT       DEFAULT NULL,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_rule_event_enabled` (`event_type`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
