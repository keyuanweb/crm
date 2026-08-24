-- ============================================================
-- V54__search_indexes.sql — 全局搜索性能优化
-- 说明：为搜索 LIKE 字段补齐缺失索引（lead.name / ticket.title /
--       customer.contact_person / customer.email / lead.email / lead.phone）
-- ============================================================

ALTER TABLE `lead`
  ADD KEY `idx_lead_deleted_name` (`deleted`, `name`),
  ADD KEY `idx_lead_deleted_phone` (`deleted`, `phone`),
  ADD KEY `idx_lead_deleted_email` (`deleted`, `email`);

ALTER TABLE `ticket`
  ADD KEY `idx_ticket_deleted_title` (`deleted`, `title`);

ALTER TABLE `customer`
  ADD KEY `idx_customer_deleted_contact_person` (`deleted`, `contact_person`),
  ADD KEY `idx_customer_deleted_email` (`deleted`, `email`);
