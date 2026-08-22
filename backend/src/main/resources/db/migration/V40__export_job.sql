-- ============================================================
-- V40__export_job.sql — 导出任务表（016-system-enhancement）
-- ============================================================

CREATE TABLE `export_job` (
  `id`            BIGINT        NOT NULL AUTO_INCREMENT,
  `export_type`   VARCHAR(30)   NOT NULL COMMENT 'LEAD/CUSTOMER/OPPORTUNITY/TICKET',
  `filter`        TEXT          COMMENT '筛选条件（JSON）',
  `status`        VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED',
  `file_path`     VARCHAR(500)  DEFAULT NULL COMMENT '生成文件路径',
  `row_count`     BIGINT        DEFAULT NULL COMMENT '导出行数',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '失败原因',
  `deleted`       TINYINT(1)    NOT NULL DEFAULT 0,
  `version`       INT           NOT NULL DEFAULT 0,
  `created_by`    BIGINT        DEFAULT NULL,
  `created_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `completed_at`  DATETIME      DEFAULT NULL COMMENT '完成时间',
  PRIMARY KEY (`id`),
  KEY `idx_export_user` (`created_by`, `created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
