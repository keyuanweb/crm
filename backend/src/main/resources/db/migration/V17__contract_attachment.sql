-- ============================================================
-- V17__contract_attachment.sql — 合同附件表（008-contract-management）
-- 说明：附件元数据；文件存本地磁盘（crm.contract.storage-dir）
-- ============================================================

CREATE TABLE `contract_attachment` (
  `id`           BIGINT       NOT NULL AUTO_INCREMENT,
  `contract_id`  BIGINT       NOT NULL COMMENT '合同',
  `file_name`    VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_path`    VARCHAR(500) NOT NULL COMMENT '相对存储路径',
  `file_size`    BIGINT       NOT NULL COMMENT '大小（字节）',
  `content_type` VARCHAR(100) DEFAULT NULL COMMENT 'MIME 类型',
  `uploaded_by`  BIGINT       DEFAULT NULL COMMENT '上传人',
  `deleted`      TINYINT(1)   NOT NULL DEFAULT 0,
  `version`      INT          NOT NULL DEFAULT 0,
  `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_attachment_contract` (`contract_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
