-- ============================================================
-- V41__notification.sql — 统一通知表（016-system-enhancement）
-- 迁移 013 workflow_notification 数据，此后通知中心统一使用本表
-- ============================================================

CREATE TABLE `notification` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`     BIGINT       NOT NULL COMMENT '接收人',
  `type`        VARCHAR(30)  NOT NULL COMMENT 'WORKFLOW/TICKET_ASSIGN/TICKET_REPLY',
  `message`     VARCHAR(500) NOT NULL COMMENT '通知内容',
  `read`        TINYINT(1)   NOT NULL DEFAULT 0,
  `entity_type` VARCHAR(30)  DEFAULT NULL COMMENT '关联实体类型',
  `entity_id`   BIGINT       DEFAULT NULL COMMENT '关联实体 id',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user_read` (`user_id`, `read`),
  KEY `idx_notif_user_created` (`user_id`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 迁移 013 workflow_notification 存量数据
INSERT INTO `notification` (`user_id`, `type`, `message`, `read`, `created_at`)
SELECT `user_id`, 'WORKFLOW', `message`, `read`, `created_at`
FROM `workflow_notification`;
