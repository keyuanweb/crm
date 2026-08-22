-- ============================================================
-- V1__init.sql — CRM 初始版本基础表结构
-- 说明：逻辑删除列 deleted（0=正常，1=已删）、乐观锁列 version
-- 金额单位：分（BIGINT）
-- ============================================================

CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(50)  NOT NULL,
  `password_hash` VARCHAR(100) NOT NULL,
  `display_name`  VARCHAR(50)  NOT NULL,
  `role`          VARCHAR(20)  NOT NULL,
  `enabled`       TINYINT(1)   NOT NULL DEFAULT 1,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `customer` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(100) NOT NULL COMMENT '客户名称',
  `company`       VARCHAR(100) NOT NULL COMMENT '公司',
  `contact_person` VARCHAR(50) DEFAULT NULL COMMENT '联系人',
  `phone`         VARCHAR(30)  DEFAULT NULL COMMENT '电话',
  `email`         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `address`       VARCHAR(255) DEFAULT NULL COMMENT '地址',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `status`        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE',
  `deleted`       TINYINT(1)   NOT NULL DEFAULT 0,
  `version`       INT          NOT NULL DEFAULT 0,
  `created_by`    BIGINT       DEFAULT NULL,
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_customer_deleted_company` (`deleted`, `company`),
  KEY `idx_customer_deleted_name` (`deleted`, `name`),
  KEY `idx_customer_deleted_phone` (`deleted`, `phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `opportunity` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id`        BIGINT       NOT NULL COMMENT '关联客户',
  `name`               VARCHAR(100) NOT NULL COMMENT '商机名称',
  `expected_amount_min` BIGINT       NOT NULL DEFAULT 0 COMMENT '预期金额下限（分）',
  `expected_amount_max` BIGINT       NOT NULL DEFAULT 0 COMMENT '预期金额上限（分）',
  `remark`             VARCHAR(500) DEFAULT NULL,
  `status`             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ARCHIVED',
  `deleted`            TINYINT(1)   NOT NULL DEFAULT 0,
  `version`            INT          NOT NULL DEFAULT 0,
  `created_by`         BIGINT       DEFAULT NULL,
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_opportunity_deleted_customer` (`deleted`, `customer_id`),
  KEY `idx_opportunity_deleted_name` (`deleted`, `name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `sales_opportunity` (
  `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
  `opportunity_id`      BIGINT      NOT NULL COMMENT '所属商机（父）',
  `amount`              BIGINT      NOT NULL DEFAULT 0 COMMENT '金额（分）',
  `stage`               VARCHAR(30) NOT NULL COMMENT 'INITIAL_CONTACT/NEGOTIATING/CLOSED_WON/CLOSED_LOST',
  `expected_close_date` DATE        DEFAULT NULL,
  `close_result`        VARCHAR(20) DEFAULT NULL COMMENT 'WON/LOST',
  `closed_at`           DATETIME    DEFAULT NULL,
  `deleted`             TINYINT(1)  NOT NULL DEFAULT 0,
  `version`             INT         NOT NULL DEFAULT 0,
  `created_by`          BIGINT      DEFAULT NULL,
  `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_so_deleted_stage` (`deleted`, `stage`),
  KEY `idx_so_deleted_opportunity` (`deleted`, `opportunity_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE `follow_up` (
  `id`                BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id`       BIGINT       NOT NULL,
  `opportunity_id`    BIGINT       DEFAULT NULL,
  `method`            VARCHAR(20)  NOT NULL COMMENT 'PHONE/EMAIL/MEETING/OTHER',
  `content`           VARCHAR(2000) NOT NULL,
  `next_follow_up_at` DATETIME     DEFAULT NULL,
  `follow_up_by`      BIGINT       NOT NULL,
  `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_followup_customer_created` (`customer_id`, `created_at`),
  KEY `idx_followup_opportunity` (`opportunity_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
