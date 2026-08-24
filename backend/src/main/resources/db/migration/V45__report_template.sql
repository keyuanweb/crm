-- ============================================================
-- V45__report_template.sql — 自定义报表模板（021-custom-reports）
-- 说明：保存常用报表配置（维度/指标/粒度/时间范围），仅 ADMIN
-- ============================================================

CREATE TABLE `report_template` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(100) NOT NULL COMMENT '模板名',
  `dimension`     VARCHAR(30)  NOT NULL COMMENT '维度：SALES/PRODUCT/SOURCE/STAGE/TIME',
  `metric`        VARCHAR(20)  NOT NULL COMMENT '指标：COUNT/AMOUNT',
  `granularity`   VARCHAR(10)  DEFAULT NULL COMMENT '时间粒度：DAY/MONTH（TIME 维度时）',
  `start_date`    DATE         DEFAULT NULL,
  `end_date`      DATE         DEFAULT NULL,
  `stage_filter`  VARCHAR(30)  DEFAULT NULL COMMENT '阶段过滤（可选）',
  `created_by`    BIGINT       DEFAULT NULL,
  `deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  `version`       INT          NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_report_template_creator` (`created_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
