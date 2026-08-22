-- ============================================================
-- V32__marketing_campaign.sql — 营销活动表（014-marketing）
-- ============================================================

CREATE TABLE `marketing_campaign` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(100) NOT NULL COMMENT '活动名称',
  `channel`    VARCHAR(20)  NOT NULL COMMENT 'WEBSITE/AD/EXHIBITION/REFERRAL/EMAIL/SOCIAL/OTHER',
  `budget`     BIGINT       NOT NULL DEFAULT 0 COMMENT '预算（分）',
  `cost`       BIGINT       NOT NULL DEFAULT 0 COMMENT '成本（分）',
  `start_date` DATE         DEFAULT NULL COMMENT '开始日期',
  `end_date`   DATE         DEFAULT NULL COMMENT '结束日期',
  `status`     VARCHAR(20)  NOT NULL DEFAULT 'PLANNING' COMMENT 'PLANNING/RUNNING/ENDED',
  `deleted`    TINYINT(1)   NOT NULL DEFAULT 0,
  `version`    INT          NOT NULL DEFAULT 0,
  `created_by` BIGINT       DEFAULT NULL,
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_campaign_channel_status` (`channel`, `status`),
  KEY `idx_campaign_deleted_name` (`deleted`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
