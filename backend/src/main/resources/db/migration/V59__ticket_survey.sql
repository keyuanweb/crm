-- ============================================================
-- V59__ticket_survey.sql — 工单满意度调查（051-csat-nps）
-- ============================================================

CREATE TABLE `ticket_survey` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `ticket_id`  BIGINT       NOT NULL COMMENT '工单 id（唯一）',
  `rating`     INT          NOT NULL COMMENT '评分 1-5',
  `comment`    VARCHAR(500) DEFAULT NULL COMMENT '评语',
  `created_by` BIGINT       NOT NULL COMMENT '提交人',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_survey_ticket` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
