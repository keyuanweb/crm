-- ============================================================
-- V7__lead.sql — 线索表
-- 说明：线索是销售链路入口，可转化为客户+联系人+商机
-- 状态：NEW(新线索)/WORKING(跟进中)/QUALIFIED(已转化)/DISQUALIFIED(无效)
-- 来源：WEBSITE/AD/EXHIBITION/REFERRAL/COLD_CALL/OTHER
-- ============================================================

CREATE TABLE `lead` (
  `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
  `name`                 VARCHAR(50)  NOT NULL COMMENT '姓名',
  `company`              VARCHAR(100) NOT NULL COMMENT '公司',
  `title`                VARCHAR(50)  DEFAULT NULL COMMENT '职位',
  `phone`                VARCHAR(30)  DEFAULT NULL COMMENT '电话',
  `email`                VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `source`               VARCHAR(20)  NOT NULL DEFAULT 'OTHER' COMMENT '来源：WEBSITE/AD/EXHIBITION/REFERRAL/COLD_CALL/OTHER',
  `status`               VARCHAR(20)  NOT NULL DEFAULT 'NEW' COMMENT '状态：NEW/WORKING/QUALIFIED/DISQUALIFIED',
  `score`                INT          NOT NULL DEFAULT 0 COMMENT '评分 0-100',
  `owner_id`             BIGINT       DEFAULT NULL COMMENT '负责人（空=线索池）',
  `converted_customer_id` BIGINT      DEFAULT NULL COMMENT '转化后的客户ID',
  `converted_at`         DATETIME     DEFAULT NULL COMMENT '转化时间',
  `remark`               VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `deleted`              TINYINT(1)   NOT NULL DEFAULT 0,
  `version`              INT          NOT NULL DEFAULT 0,
  `created_by`           BIGINT       DEFAULT NULL,
  `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_lead_deleted_status` (`deleted`, `status`),
  KEY `idx_lead_deleted_owner` (`deleted`, `owner_id`),
  KEY `idx_lead_deleted_company` (`deleted`, `company`),
  KEY `idx_lead_deleted_name` (`deleted`, `name`),
  KEY `idx_lead_deleted_source` (`deleted`, `source`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
