-- ============================================================
-- V57__contract_renewal.sql — 合同续约（046-contract-renewal）
-- ============================================================

ALTER TABLE `contract`
  ADD COLUMN `renewed_from_id` BIGINT DEFAULT NULL COMMENT '续约来源合同 id（自引用）' AFTER `remark`;

CREATE INDEX `idx_contract_renewed_from` ON `contract` (`renewed_from_id`);
