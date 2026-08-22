-- ============================================================
-- V33__attribution_columns.sql — 营销归因字段（014-marketing）
-- 说明：线索/客户关联营销活动（campaign_id 可选）
-- ============================================================

ALTER TABLE `lead`
  ADD COLUMN `campaign_id` BIGINT DEFAULT NULL COMMENT '营销归因活动' AFTER `converted_at`,
  ADD KEY `idx_lead_campaign` (`campaign_id`);

ALTER TABLE `customer`
  ADD COLUMN `campaign_id` BIGINT DEFAULT NULL COMMENT '营销归因活动' AFTER `owner_id`,
  ADD KEY `idx_customer_campaign` (`campaign_id`);
