-- ============================================================
-- V50__field_visit.sql — 外勤拜访管理（035-field-visit）
-- 说明：拜访计划 + 签到记录
-- ============================================================

CREATE TABLE `field_visit` (
  `id`               BIGINT         NOT NULL AUTO_INCREMENT,
  `customer_id`      BIGINT         NOT NULL COMMENT '客户',
  `theme`            VARCHAR(100)   NOT NULL COMMENT '拜访主题',
  `visit_time`       DATETIME       NOT NULL COMMENT '计划拜访时间',
  `duration_minutes` INT            DEFAULT NULL COMMENT '预计时长（分）',
  `status`           VARCHAR(20)    NOT NULL DEFAULT 'PLANNED' COMMENT 'PLANNED/DONE/CANCELED',
  `latitude`         DECIMAL(10, 7) DEFAULT NULL COMMENT '签到纬度',
  `longitude`        DECIMAL(10, 7) DEFAULT NULL COMMENT '签到经度',
  `location_text`    VARCHAR(255)   DEFAULT NULL COMMENT '签到地址/坐标文本',
  `check_in_time`    DATETIME       DEFAULT NULL COMMENT '签到时间',
  `summary`          VARCHAR(1000)  DEFAULT NULL COMMENT '拜访小结',
  `late_flag`        TINYINT(1)     NOT NULL DEFAULT 0 COMMENT '补签标记',
  `created_by`       BIGINT         DEFAULT NULL,
  `deleted`          TINYINT(1)     NOT NULL DEFAULT 0,
  `version`          INT            NOT NULL DEFAULT 0,
  `created_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`       DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_visit_customer` (`customer_id`),
  KEY `idx_visit_creator` (`created_by`),
  KEY `idx_visit_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
