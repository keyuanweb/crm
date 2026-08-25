-- ============================================================
-- V65__multi_currency.sql — 多币种（057-multi-currency）
-- ============================================================

CREATE TABLE `currency_rate` (
  `id`         BIGINT        NOT NULL AUTO_INCREMENT,
  `code`       VARCHAR(10)   NOT NULL,
  `name`       VARCHAR(50)   NOT NULL,
  `rate`       DECIMAL(18,6) NOT NULL DEFAULT 1,
  `is_base`    TINYINT(1)    NOT NULL DEFAULT 0,
  `enabled`    TINYINT(1)    NOT NULL DEFAULT 1,
  `version`    INT           NOT NULL DEFAULT 0,
  `created_by` BIGINT        DEFAULT NULL,
  `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_currency_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO `currency_rate` (`code`, `name`, `rate`, `is_base`) VALUES ('CNY', '人民币', 1, 1);

CREATE TABLE `product_price` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `product_id`    BIGINT      NOT NULL,
  `currency_code` VARCHAR(10) NOT NULL,
  `price`         BIGINT      NOT NULL COMMENT '价格（分）',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_currency` (`product_id`, `currency_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
