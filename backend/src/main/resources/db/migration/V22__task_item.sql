-- ============================================================
-- V22__task_item.sql — 任务表（010-task-reminder）
-- 说明：个人待办任务；owner_id 数据隔离
-- ============================================================

CREATE TABLE `task_item` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `title`       VARCHAR(200) NOT NULL COMMENT '任务标题',
  `due_at`      DATETIME     DEFAULT NULL COMMENT '截止时间',
  `priority`    VARCHAR(20)  NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
  `status`      VARCHAR(20)  NOT NULL DEFAULT 'TODO' COMMENT 'TODO/DONE',
  `linked_type` VARCHAR(20)  DEFAULT NULL COMMENT 'CUSTOMER/LEAD/CONTRACT/ORDER',
  `linked_id`   BIGINT       DEFAULT NULL COMMENT '关联对象 id',
  `remark`      VARCHAR(500) DEFAULT NULL,
  `owner_id`    BIGINT       NOT NULL COMMENT '归属用户',
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_by`  BIGINT       DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_owner_status` (`owner_id`, `status`),
  KEY `idx_task_owner_due` (`owner_id`, `due_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
