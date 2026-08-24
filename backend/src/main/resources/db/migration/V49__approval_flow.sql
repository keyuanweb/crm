-- ============================================================
-- V49__approval_flow.sql — 通用多级审批流（033-approval-flow）
-- 说明：approval_flow（定义）+ approval_instance + approval_task + approval_log
-- ============================================================

CREATE TABLE `approval_flow` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(50)  NOT NULL COMMENT '流程名',
  `business_type` VARCHAR(20)  NOT NULL COMMENT 'CONTRACT / QUOTE',
  `nodes`         TEXT         NOT NULL COMMENT 'JSON 节点序列',
  `condition_json` TEXT        DEFAULT NULL COMMENT '条件分支（金额阈值 + 追加节点）',
  `enabled`       TINYINT(1)   NOT NULL DEFAULT 1,
  `deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  `version`       INT          NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `approval_instance` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `flow_id`        BIGINT       NOT NULL,
  `business_type`  VARCHAR(20)  NOT NULL,
  `business_id`    BIGINT       NOT NULL,
  `title`          VARCHAR(200) NOT NULL COMMENT '审批标题',
  `status`         VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/CANCELED',
  `current_task_id` BIGINT      DEFAULT NULL,
  `initiator`      BIGINT       NOT NULL,
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  `version`        INT          NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_instance_biz` (`business_type`, `business_id`),
  KEY `idx_instance_initiator` (`initiator`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `approval_task` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `instance_id`   BIGINT       NOT NULL,
  `node_name`     VARCHAR(50)  NOT NULL,
  `approver_type` VARCHAR(20)  NOT NULL COMMENT 'ROLE / USER / MANAGER',
  `approver_value` VARCHAR(100) DEFAULT NULL COMMENT '角色码或 userId',
  `approver_id`   BIGINT       DEFAULT NULL COMMENT '实际审批人',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED/TRANSFERRED',
  `comment`       VARCHAR(500) DEFAULT NULL,
  `seq`           INT          NOT NULL DEFAULT 0,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_task_instance` (`instance_id`),
  KEY `idx_task_approver` (`approver_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `approval_log` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `instance_id` BIGINT       NOT NULL,
  `task_id`     BIGINT       DEFAULT NULL,
  `action`      VARCHAR(20)  NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/TRANSFER/RENEW',
  `operator`    BIGINT       NOT NULL,
  `comment`     VARCHAR(500) DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_log_instance` (`instance_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
