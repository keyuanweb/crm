-- ============================================================
-- V60__email_unsubscribe.sql — 邮件退订 + A/B 测试（052-email-advanced）
-- ============================================================

CREATE TABLE `email_unsubscribe` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `email`           VARCHAR(255) NOT NULL,
  `campaign_id`     BIGINT       DEFAULT NULL COMMENT '来源活动（可空）',
  `unsubscribed_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unsubscribe_email` (`email`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE `email_campaign`
  ADD COLUMN `variant` VARCHAR(10) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/A/B',
  ADD COLUMN `subject_b` VARCHAR(255) DEFAULT NULL COMMENT 'B 变体主题',
  ADD COLUMN `winner` VARCHAR(10) DEFAULT NULL COMMENT 'A/B/NONE（测试后标记更优）';

ALTER TABLE `email_send_log`
  ADD COLUMN `variant` VARCHAR(10) DEFAULT NULL COMMENT '该封邮件所属变体（A/B）';
