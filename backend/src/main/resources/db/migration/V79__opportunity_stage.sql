-- ============================================================
-- V79__opportunity_stage.sql — 商机阶段可配置化（1.2-stage-configurable）
-- 说明：
--   此前「商机阶段词汇」在代码里散落成四份硬编码清单，彼此没有共享来源：
--     · SalesOpportunityService.STAGES         —— 全部合法阶段（写入校验用）
--     · SalesOpportunityService.ACTIVE_STAGES  —— 进行中的阶段（看板列 + 能否流转）
--     · StageConversionService.DEFAULT_PROBABILITY —— 各阶段赢率（预测用）
--     · OpportunityStatsService.STAGE_ORDER    —— 漏斗顺序
--   后果是活跃阶段只有 2 个（看板只有两列），且任何一处增删阶段都要改四处代码。
--   本表把「阶段」与「赢率」落成数据，上述四处改为读本表。
--
-- 与 sales_opportunity.stage 的关系：
--   那一列是 VARCHAR(30) 自由文本，本表是它的**取值字典**。刻意不加外键——
--   阶段可被停用或改名，而历史商机必须仍能显示，加外键会把「改配置」变成「动数据」。
--   同理不做 CHECK 约束：停用一个阶段不应让历史行变成非法数据。
-- ============================================================

CREATE TABLE IF NOT EXISTS `opportunity_stage` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT,
  `code`        VARCHAR(64)  NOT NULL COMMENT '阶段编码（如 INITIAL_CONTACT）。一经使用不要再改——历史商机的 stage 列按它关联',
  `name`        VARCHAR(64)  NOT NULL COMMENT '显示名，可随时改（改它不影响历史数据）',
  `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '看板列序与漏斗顺序，小者在前',
  `probability` DECIMAL(5,4) NOT NULL DEFAULT 0.0000 COMMENT '预测赢率 0~1，历史样本不足（<10）时的回退值',
  `stage_type`  VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE 进行中 / WON 赢单 / LOST 输单',
  `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '停用后不再出现于下拉与看板列，历史商机仍照常显示',
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  `version`     INT          NOT NULL DEFAULT 0,
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  -- 与 sla_policy 的 uk_sla_policy_priority (priority, deleted) 同构：把 deleted 并入唯一键，
  -- 使「删掉一个阶段再建同名阶段」不被历史软删行挡住。
  UNIQUE KEY `uk_opportunity_stage_code` (`code`, `deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商机阶段字典（可配置）';

-- ---------- 种子数据 ----------
-- ① 已有 4 个阶段：编码与赢率**逐字沿用代码里的原值**（INITIAL_CONTACT 0.2 / NEGOTIATING 0.5 /
--    WON 1.0 / LOST 0.0）。这一点不能改：历史样本不足（<10）的阶段在预测里回退到本列的值，
--    改这些数字等于直接改预测结果——上线瞬间预测就会跳变。
-- ② 新增 3 个活跃阶段，赢率插在既有值之间且保持单调递增，故既有阶段的相对次序不受影响。
--
-- 关于「商务谈判」：本表没有新增该名称，而是复用了既有的 NEGOTIATING（显示名「谈判中」）——
-- 两者是同一个语义位置。再建一个同名阶段会让看板出现两个含义重叠的列。
INSERT INTO `opportunity_stage` (`code`, `name`, `sort_order`, `probability`, `stage_type`, `enabled`)
VALUES
  ('INITIAL_CONTACT', '初步接触',  10, 0.2000, 'ACTIVE', 1),
  ('NEEDS_CONFIRMED', '需求确认',  20, 0.3000, 'ACTIVE', 1),
  ('PROPOSAL_QUOTED', '方案报价',  30, 0.4000, 'ACTIVE', 1),
  ('NEGOTIATING',     '谈判中',    40, 0.5000, 'ACTIVE', 1),
  ('CLOSED_WON',      '已赢单',    90, 1.0000, 'WON',    1),
  ('CLOSED_LOST',     '已输单',   100, 0.0000, 'LOST',   1);
