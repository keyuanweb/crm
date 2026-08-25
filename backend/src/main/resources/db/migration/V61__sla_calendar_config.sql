-- ============================================================
-- V61__sla_calendar_config.sql — SLA 工作日历（054-sla-calendar）
-- ============================================================

CREATE TABLE `sla_calendar_config` (
  `id`         BIGINT    NOT NULL AUTO_INCREMENT,
  `work_slots` TEXT      DEFAULT NULL COMMENT '工作时间段 JSON：[{"start":"09:00","end":"18:00"}]',
  `work_days`  TEXT      DEFAULT NULL COMMENT '工作周 JSON：[1,2,3,4,5]',
  `holidays`   TEXT      DEFAULT NULL COMMENT '节假日 JSON：["2026-10-01"]',
  `enabled`    TINYINT(1) NOT NULL DEFAULT 0,
  `created_at` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME  NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
