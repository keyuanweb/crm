-- ============================================================
-- V31__workflow_log_updated_at.sql — 执行日志补充 updated_at 列（013）
-- 说明：WorkflowExecutionLog 继承 BaseEntity 含 updated_at，补列
-- ============================================================

ALTER TABLE `workflow_execution_log`
  ADD COLUMN `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER `created_at`;
