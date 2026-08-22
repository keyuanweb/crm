-- ============================================================
-- V10__contact.sql — 联系人表（005-contact-management）
-- 说明：客户下可维护多个联系人（一对多）；逻辑删除 + 乐观锁
-- ============================================================

CREATE TABLE `contact` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `customer_id` BIGINT       NOT NULL COMMENT '所属客户',
  `name`        VARCHAR(50)  NOT NULL COMMENT '姓名',
  `title`       VARCHAR(50)  DEFAULT NULL COMMENT '职位',
  `phone`       VARCHAR(30)  DEFAULT NULL COMMENT '电话',
  `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  `role`        VARCHAR(30)  NOT NULL DEFAULT 'OTHER' COMMENT 'DECISION_MAKER/INFLUENCER/EVALUATOR/CHAMPION/OTHER',
  `remark`      VARCHAR(500) DEFAULT NULL,
  `deleted`     TINYINT(1)   NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_by`  BIGINT       DEFAULT NULL,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_contact_deleted_customer` (`deleted`, `customer_id`),
  KEY `idx_contact_deleted_name` (`deleted`, `name`),
  KEY `idx_contact_deleted_phone` (`deleted`, `phone`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
