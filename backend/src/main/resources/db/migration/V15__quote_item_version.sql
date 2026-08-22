-- ============================================================
-- V15__quote_item_version.sql — 报价行增加 version 列（007-product-cpq）
-- 说明：QuoteItem 继承 BaseEntity（@Version 乐观锁），补加 version 列
-- ============================================================

ALTER TABLE `quote_item`
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER `deleted`;
