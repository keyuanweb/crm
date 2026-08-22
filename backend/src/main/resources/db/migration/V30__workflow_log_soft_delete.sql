-- ============================================================
-- V30__workflow_log_soft_delete.sql — 执行日志补充软删除/乐观锁列（013）
-- 说明：WorkflowExecutionLog 继承 BaseEntity（@TableLogic/@Version），补列
-- ============================================================

ALTER TABLE `workflow_execution_log`
  ADD COLUMN `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER `error_message`,
  ADD COLUMN `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁' AFTER `deleted`;
