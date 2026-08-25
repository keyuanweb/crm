-- ============================================================
-- V62__landing_page.sql — 托管落地页 + UTM（053-landing-page）
-- ============================================================

CREATE TABLE `landing_page` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(200) NOT NULL,
  `subtitle`    VARCHAR(500) DEFAULT NULL,
  `description` TEXT         DEFAULT NULL,
  `theme_color` VARCHAR(20)  DEFAULT NULL,
  `form_id`     BIGINT       NOT NULL,
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_by`  BIGINT       DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE `form_submission`
  ADD COLUMN `utm_source`   VARCHAR(100) DEFAULT NULL,
  ADD COLUMN `utm_medium`   VARCHAR(100) DEFAULT NULL,
  ADD COLUMN `utm_campaign` VARCHAR(100) DEFAULT NULL,
  ADD COLUMN `utm_term`     VARCHAR(100) DEFAULT NULL,
  ADD COLUMN `utm_content`  VARCHAR(100) DEFAULT NULL;
