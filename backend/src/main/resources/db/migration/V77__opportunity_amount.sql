-- V77: add amount column to opportunity table
ALTER TABLE `opportunity` ADD COLUMN `amount` BIGINT NOT NULL DEFAULT 0 COMMENT '商机金额（分）' AFTER `expected_amount_max`;
