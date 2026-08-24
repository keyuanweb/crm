-- ============================================================
-- V48__email_marketing.sql — 邮件营销触达（030-email-marketing）
-- 说明：email_template + email_campaign + email_send_log + email_track
-- ============================================================

CREATE TABLE `email_template` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `name`        VARCHAR(50)  NOT NULL COMMENT '模板名（唯一）',
  `subject`     VARCHAR(200) NOT NULL COMMENT '邮件主题（可含变量）',
  `content`     TEXT         NOT NULL COMMENT 'HTML 正文（含变量占位）',
  `category`    VARCHAR(30)  DEFAULT 'NOTICE' COMMENT 'WELCOME/PROMOTION/FOLLOW_UP/NOTICE',
  `created_by`  BIGINT       DEFAULT NULL,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_email_template_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `email_campaign` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `template_id` BIGINT       NOT NULL COMMENT '模板',
  `name`        VARCHAR(100) NOT NULL COMMENT '活动名',
  `source_type` VARCHAR(20)  NOT NULL COMMENT 'SEGMENT / CUSTOMER_IDS',
  `source_ref`  VARCHAR(255) DEFAULT NULL COMMENT '细分 id 或客户 id JSON',
  `total_count` INT          NOT NULL DEFAULT 0,
  `sent_count`  INT          NOT NULL DEFAULT 0,
  `failed_count` INT         NOT NULL DEFAULT 0,
  `open_count`  INT          NOT NULL DEFAULT 0,
  `click_count` INT          NOT NULL DEFAULT 0,
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
  `created_by`  BIGINT       DEFAULT NULL,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_campaign_creator` (`created_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `email_send_log` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `campaign_id`   BIGINT       NOT NULL COMMENT '批次',
  `customer_id`   BIGINT       DEFAULT NULL,
  `email`         VARCHAR(100) NOT NULL,
  `subject`       VARCHAR(200) NOT NULL,
  `content`       TEXT         NOT NULL,
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'SENT' COMMENT 'SENT/FAILED',
  `error_message` VARCHAR(255) DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_send_log_campaign` (`campaign_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `email_track` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `send_log_id` BIGINT       NOT NULL,
  `track_type`  VARCHAR(10)  NOT NULL COMMENT 'OPEN / CLICK',
  `click_url`   VARCHAR(500) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_track_log` (`send_log_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
