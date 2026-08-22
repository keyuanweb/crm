-- ============================================================
-- V25__user_data_scope.sql — 用户部门与数据权限（012-data-permission）
-- ============================================================

ALTER TABLE `user`
  ADD COLUMN `department_id` BIGINT DEFAULT NULL COMMENT '所属部门' AFTER `role`;

ALTER TABLE `user`
  ADD COLUMN `data_scope` VARCHAR(20) NOT NULL DEFAULT 'SELF' COMMENT 'SELF/DEPT/DEPT_AND_CHILD/ALL' AFTER `department_id`;

ALTER TABLE `user`
  ADD KEY `idx_user_department` (`department_id`);
