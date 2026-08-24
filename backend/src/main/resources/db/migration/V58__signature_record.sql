-- ============================================================
-- V58__signature_record.sql — 电子签署记录（047-e-signature）
-- ============================================================

CREATE TABLE `signature_record` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT,
  `business_type`   VARCHAR(20)  NOT NULL COMMENT 'QUOTE/CONTRACT',
  `business_id`     BIGINT       NOT NULL COMMENT '报价/合同 id',
  `signer_id`       BIGINT       NOT NULL COMMENT '签署人（用户 id）',
  `signer_name`     VARCHAR(50)  DEFAULT NULL COMMENT '签署人姓名（冗余）',
  `signature_image` MEDIUMTEXT   NOT NULL COMMENT '签名图 base64',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_signature_business` (`business_type`, `business_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
