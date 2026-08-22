-- ============================================================
-- V28__workflow_execution_log.sql — 工作流执行日志（013-workflow-automation）
-- ============================================================

CREATE TABLE `workflow_execution_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `rule_id`       BIGINT       NOT NULL COMMENT '规则',
  `event_type`    VARCHAR(40)  NOT NULL COMMENT '触发事件',
  `entity_type`   VARCHAR(30)  DEFAULT NULL COMMENT '业务实体类型',
  `entity_id`     BIGINT       DEFAULT NULL COMMENT '业务实体 id',
  `matched`       TINYINT(1)   NOT NULL DEFAULT 1,
  `action_result` VARCHAR(500) DEFAULT NULL COMMENT '执行结果描述',
  `success`       TINYINT(1)   NOT NULL DEFAULT 1,
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_rule` (`rule_id`),
  KEY `idx_log_event` (`event_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
