-- ============================================================
-- V6__customer_unique_constraint.sql — 客户 name+company 唯一性约束提升到数据库层
-- 说明：
--   1. 先清理非删除记录中的重复数据（保留 id 最小的记录，其余逻辑删除）
--   2. MySQL 不支持部分唯一索引（WHERE deleted=0），使用生成列 active_key 实现：
--      - 非删除记录：active_key = CONCAT(name, '||', company)，参与唯一约束
--      - 已删除记录：active_key = NULL，不参与唯一约束（MySQL 唯一索引允许多个 NULL）
--   3. 生成列由 MySQL 自动维护，MyBatis-Plus 实体无需映射该列
-- ============================================================

-- Step 1: 清理非删除记录中的重复数据（保留 id 最小的记录，其余逻辑删除）
UPDATE customer c1
INNER JOIN customer c2
  ON c1.name = c2.name
  AND c1.company = c2.company
  AND c1.deleted = 0
  AND c2.deleted = 0
  AND c1.id > c2.id
SET c1.deleted = 1;

-- Step 2: 新增生成列 active_key（STORED，非删除时为 name||company，删除时为 NULL）
ALTER TABLE customer
  ADD COLUMN active_key VARCHAR(255)
  GENERATED ALWAYS AS (CASE WHEN deleted = 0 THEN CONCAT(name, '||', company) ELSE NULL END) STORED
  COMMENT '用于非删除记录的 name+company 唯一约束（生成列，自动维护）';

-- Step 3: 创建唯一索引
ALTER TABLE customer
  ADD UNIQUE KEY uk_customer_active_name_company (active_key);
