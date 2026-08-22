-- ============================================================
-- V34__ticket.sql — 服务工单表（015-customer-service）
-- ============================================================

CREATE TABLE `ticket` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id`          BIGINT       NOT NULL COMMENT '关联客户（必填）',
  `contact_id`           BIGINT       DEFAULT NULL COMMENT '关联联系人（可选）',
  `title`                VARCHAR(200) NOT NULL COMMENT '标题',
  `description`          TEXT         COMMENT '问题描述',
  `priority`             VARCHAR(20)  NOT NULL COMMENT 'LOW/MEDIUM/HIGH/URGENT',
  `status`               VARCHAR(20)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/IN_PROGRESS/RESOLVED/CLOSED',
  `assignee_id`          BIGINT       DEFAULT NULL COMMENT '处理人',
  `sla_respond_deadline` DATETIME     DEFAULT NULL COMMENT 'SLA 响应到期',
  `sla_resolve_deadline` DATETIME     DEFAULT NULL COMMENT 'SLA 解决到期',
  `sla_status`           VARCHAR(20)  DEFAULT NULL COMMENT 'NORMAL/WARNING/OVERDUE',
  `remark`               VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `deleted`              TINYINT(1)   NOT NULL DEFAULT 0,
  `version`              INT          NOT NULL DEFAULT 0,
  `created_by`           BIGINT       DEFAULT NULL,
  `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket_status` (`status`),
  KEY `idx_ticket_assignee` (`assignee_id`),
  KEY `idx_ticket_customer` (`customer_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
