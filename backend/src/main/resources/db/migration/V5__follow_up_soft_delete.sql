-- ============================================================
-- V5__follow_up_soft_delete.sql — 修复 follow_up 缺失的软删除/乐观锁列
-- 说明：FollowUp 实体继承 BaseEntity（@TableLogic/@Version），
-- 但 V1 建表遗漏了 deleted/version 两列，导致真实环境查询报
-- "Unknown column 'deleted'"（H2 测试 schema 有、生产 schema 无）。
-- ============================================================

ALTER TABLE `follow_up`
  ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常，1=已删）' AFTER `follow_up_by`,
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER `deleted`;
