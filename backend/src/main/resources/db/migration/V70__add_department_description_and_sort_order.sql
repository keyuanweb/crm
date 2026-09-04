-- ============================================================
-- V70__add_department_description_and_sort_order.sql
-- 说明：为部门表添加描述和排序号字段
-- ============================================================

ALTER TABLE `department` 
  ADD COLUMN `description` VARCHAR(500) DEFAULT NULL COMMENT '部门描述' AFTER `parent_id`,
  ADD COLUMN `sort_order` INT DEFAULT 0 COMMENT '排序号（值越小越靠前）' AFTER `description`;
