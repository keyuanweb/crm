-- ============================================================
-- V44__sales_target_user_id.sql — 销售目标支持个人维度（020-sales-targets）
-- 说明：sales_target 加 user_id（NULL=全局目标，非 NULL=个人目标）；
--       唯一键改为按 (user_id, target_month)（user_id 空时按 target_month）
-- ============================================================

ALTER TABLE `sales_target`
  ADD COLUMN `user_id` BIGINT DEFAULT NULL COMMENT '目标归属用户（NULL=全局目标）' AFTER `target_amount`;

CREATE INDEX `idx_sales_target_user_month` ON `sales_target` (`user_id`, `target_month`);

-- 重建唯一键：删除旧的 active_key 生成列与唯一索引，改为按 user_id+target_month 唯一
ALTER TABLE `sales_target` DROP INDEX `uk_sales_target_active_month`;
ALTER TABLE `sales_target` DROP COLUMN `active_key`;
ALTER TABLE `sales_target`
  ADD COLUMN `active_key` VARCHAR(64) GENERATED ALWAYS AS (
    CASE WHEN deleted = 0
         THEN CONCAT(COALESCE(user_id, 0), '||', target_month)
         ELSE NULL END) STORED COMMENT '非删除记录按 (user_id,target_month) 唯一',
  ADD UNIQUE KEY `uk_sales_target_active` (`active_key`);
