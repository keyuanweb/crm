-- ============================================================
-- V2__add_user_audit_columns.sql
-- 修复 user 表缺失的审计字段：BaseEntity 声明了 deleted(@TableLogic)
-- 与 version(@Version)，MyBatis-Plus 会自动携带 deleted=0 条件。
-- V1 已发布，故通过增量迁移补列（Flyway 校验和不受影响）。
-- ============================================================

ALTER TABLE `user`
  ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除（0=正常，1=已删）' AFTER `enabled`,
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本' AFTER `deleted`;
