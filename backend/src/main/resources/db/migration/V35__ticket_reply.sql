-- ============================================================
-- V35__ticket_reply.sql — 工单回复表（015-customer-service）
-- ============================================================

CREATE TABLE `ticket_reply` (
  `id`         BIGINT   NOT NULL AUTO_INCREMENT,
  `ticket_id`  BIGINT   NOT NULL COMMENT '所属工单',
  `replier_id` BIGINT   NOT NULL COMMENT '回复人',
  `content`    TEXT     NOT NULL COMMENT '回复内容',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket_reply_ticket` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
