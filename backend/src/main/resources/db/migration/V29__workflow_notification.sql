-- ============================================================
-- V29__workflow_notification.sql — 站内通知（013-workflow-automation）
-- ============================================================

CREATE TABLE `workflow_notification` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT       NOT NULL COMMENT '接收人',
  `message`    VARCHAR(500) NOT NULL COMMENT '通知内容',
  `read`       TINYINT(1)   NOT NULL DEFAULT 0,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_notif_user` (`user_id`, `read`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
