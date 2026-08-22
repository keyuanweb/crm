-- ============================================================
-- V23__customer_owner.sql — 客户归属字段（011-customer-pool）
-- 说明：owner_id 空 = 公海；配合 idx_customer_deleted_owner 索引
-- ============================================================

ALTER TABLE `customer`
  ADD COLUMN `owner_id` BIGINT DEFAULT NULL COMMENT '归属销售（空=公海）' AFTER `created_by`;

ALTER TABLE `customer`
  ADD KEY `idx_customer_deleted_owner` (`deleted`, `owner_id`);
