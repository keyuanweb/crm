-- ============================================================
-- V91__field_permission_builtin_fields.sql — 内置字段权限（102-builtin-field-permission）
--
-- 本批把 056 的字段级权限（FLS）从「只作用于自定义字段」扩到**内置字段**
-- （用户裁决 2026-09-17：复用同一张 field_permission 表，不新开表）。
--
-- 形状：
--   · 加 field_key —— 内置字段的**属性名**（如 phone / expectedAmountMin）；
--   · field_id 改可空 —— 它此后只表示**自定义字段** id；
--   · 两者二选一：自定义行 field_key IS NULL，内置行 field_id IS NULL。
--
-- ⚠️ 为什么保留旧唯一索引、并加第二条：
--   · uk_field_perm (role_code, entity_type, field_id) 继续守自定义字段的
--     「同一角色 + 同一实体 + 同一字段只有一条配置」；
--   · uk_field_perm_builtin (role_code, entity_type, field_key) 守内置字段的同一不变式。
--   · 两条索引在「不属于自己」的行上都是全程 NULL 参与比较——MySQL 与 H2 2.2.224
--     实测都视 NULL 彼此不等，故内置行不会与自定义行互撞（实测读数见
--     specs/102-builtin-field-permission/data-model.md）。
--
-- ⚠️ 不做 CHECK 约束（「field_id 与 field_key 恰好一列非空」）：
--   照 V79__opportunity_stage.sql:15 的同一口径——约束的未来代价高于它当下的收益；
--   且 H2 与 MySQL 实测**都允许两列皆空的行**，即这个不变式本来就没有 DB 级强制。
--   它由 FieldPermissionService.upsert 在写入路径上保证（违反则 422 FIELD_PERMISSION_INVALID）。
--
-- 幂等性：Flyway 每条迁移只执行一次，故本文件不需要 DROP/IF EXISTS。
-- 既有数据不受影响：现存行都是自定义字段配置，field_key 落为 NULL，旧唯一索引语义不变。
-- ============================================================

ALTER TABLE `field_permission`
  MODIFY COLUMN `field_id` BIGINT DEFAULT NULL COMMENT '自定义字段 id；内置字段行为 NULL',
  ADD COLUMN `field_key` VARCHAR(64) DEFAULT NULL COMMENT '内置字段名（属性名）；自定义字段行为 NULL' AFTER `field_id`,
  ADD UNIQUE KEY `uk_field_perm_builtin` (`role_code`, `entity_type`, `field_key`);
