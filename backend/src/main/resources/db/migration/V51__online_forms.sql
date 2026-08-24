-- ============================================================
-- V51__online_forms.sql — 在线表单线索收集（036-online-forms）
-- 说明：form（表单定义）+ form_submission（提交快照）
-- ============================================================

CREATE TABLE `form` (
  `id`               BIGINT       NOT NULL AUTO_INCREMENT,
  `name`             VARCHAR(50)  NOT NULL COMMENT '表单名',
  `fields`           TEXT         NOT NULL COMMENT 'JSON 字段配置',
  `success_message`  VARCHAR(200) DEFAULT NULL COMMENT '提交成功提示',
  `source`           VARCHAR(30)  NOT NULL DEFAULT 'WEBSITE' COMMENT '线索来源',
  `status`           VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  `submission_count` INT          NOT NULL DEFAULT 0,
  `created_by`       BIGINT       DEFAULT NULL,
  `deleted`          TINYINT(1)   NOT NULL DEFAULT 0,
  `version`          INT          NOT NULL DEFAULT 0,
  `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `form_submission` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT,
  `form_id`    BIGINT       NOT NULL,
  `payload`    TEXT         NOT NULL COMMENT '提交字段快照 JSON',
  `client_ip`  VARCHAR(45)  DEFAULT NULL,
  `lead_id`    BIGINT       DEFAULT NULL COMMENT '生成的线索',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_submission_form` (`form_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
