-- ============================================================
-- V14__quote_item.sql — 报价单行明细表（007-product-cpq）
-- 说明：行内保存产品名/单价快照，产品变更不影响历史报价
-- ============================================================

CREATE TABLE `quote_item` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `quote_id`     BIGINT        NOT NULL COMMENT '报价单',
  `product_id`   BIGINT        DEFAULT NULL COMMENT '产品（快照引用）',
  `product_name` VARCHAR(100)  NOT NULL COMMENT '产品名快照',
  `unit_price`   BIGINT        NOT NULL COMMENT '单价快照（分）',
  `quantity`     INT           NOT NULL COMMENT '数量（≥1）',
  `discount`     DECIMAL(5,4)  NOT NULL DEFAULT 1.0000 COMMENT '折扣 0~1',
  `line_total`   BIGINT        NOT NULL COMMENT '行小计（分）',
  `deleted`      TINYINT(1)    NOT NULL DEFAULT 0,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_quote_item_quote` (`quote_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
