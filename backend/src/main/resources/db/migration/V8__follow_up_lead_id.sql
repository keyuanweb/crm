-- ============================================================
-- V8__follow_up_lead_id.sql — 跟进记录增加线索关联
-- 说明：follow_up 支持客户(customer_id)和线索(lead_id)两种关联，二选一
-- ============================================================

ALTER TABLE `follow_up`
  ADD COLUMN `lead_id` BIGINT DEFAULT NULL COMMENT '关联线索（与 customer_id 二选一）' AFTER `opportunity_id`,
  ADD KEY `idx_followup_lead` (`lead_id`);
