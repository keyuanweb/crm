-- ============================================================
-- V54__search_indexes.sql — 全局搜索性能优化
-- 说明：为搜索 LIKE 字段补齐缺失索引（ticket.title /
--       customer.contact_person / customer.email / lead.email / lead.phone）
--
-- 注意：`lead.name` **不在**其列——该列的索引 `idx_lead_deleted_name` 已由
--       V7__lead.sql 建立。MySQL 没有 `ADD KEY IF NOT EXISTS`，而本文件原先
--       重复创建了它，后果是**全新数据库**上执行到这一条即报
--       SQL State 42000 / Error Code 1061 Duplicate key name 'idx_lead_deleted_name'，
--       整套 docker compose 编排起不来（缺陷 D1，见
--       specs/083-engineering-consolidation/tasks.md 的 T079）。
--       之所以长期无人发现：开发库是增量长出来的，不会重放整条迁移链；
--       这也是新增守卫 MigrationDdlCollisionIT 要拦的形态。
-- ============================================================

ALTER TABLE `lead`
  -- `idx_lead_deleted_name` 不在此处重复创建：V7__lead.sql 已建（见文件头说明）
  ADD KEY `idx_lead_deleted_phone` (`deleted`, `phone`),
  ADD KEY `idx_lead_deleted_email` (`deleted`, `email`);

ALTER TABLE `ticket`
  ADD KEY `idx_ticket_deleted_title` (`deleted`, `title`);

ALTER TABLE `customer`
  ADD KEY `idx_customer_deleted_contact_person` (`deleted`, `contact_person`),
  ADD KEY `idx_customer_deleted_email` (`deleted`, `email`);
