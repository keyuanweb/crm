-- ============================================================
-- V43__lead_score_config.sql — 线索评分配置（019-lead-scoring）
-- 说明：评分维度分值可配置，规则引擎实时计算写回 lead.score
-- ============================================================

CREATE TABLE `lead_score_config` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `rule_key`       VARCHAR(50)  NOT NULL COMMENT '规则键：SOURCE/INFO/FOLLOWUP/FRESHNESS/THRESHOLD',
  `rule_label`     VARCHAR(100) NOT NULL COMMENT '规则中文名',
  `params_json`    VARCHAR(500) DEFAULT NULL COMMENT '参数 JSON（分值映射等）',
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_order`     INT          NOT NULL DEFAULT 0,
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  `version`        INT          NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lead_score_rule` (`rule_key`),
  KEY `idx_lead_score_enabled` (`enabled`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 默认种子（research.md R1）：
-- SOURCE=30   来源渠道（REFERRAL=30/WEBSITE=25/EXHIBITION=20/AD=15/COLD_CALL=10/OTHER=10）
-- INFO=30     信息完整度（company/title/phone/email 每项 7.5）
-- FOLLOWUP=25 跟进活跃度（最近 7 天满分，递减，无跟进 0）
-- FRESHNESS=15 互动时效（创建 ≤7 天满分，>30 天 0）
-- THRESHOLD   颜色阈值（red=40, yellow=70）
INSERT INTO `lead_score_config` (`rule_key`, `rule_label`, `params_json`, `enabled`, `sort_order`)
VALUES
  ('SOURCE', '来源渠道', '{"REFERRAL":30,"WEBSITE":25,"EXHIBITION":20,"AD":15,"COLD_CALL":10,"OTHER":10}', 1, 1),
  ('INFO', '信息完整度', '{"fields":["company","title","phone","email"],"each":7.5}', 1, 2),
  ('FOLLOWUP', '跟进活跃度', '{"activeDays":7,"maxScore":25}', 1, 3),
  ('FRESHNESS', '互动时效', '{"activeDays":7,"zeroDays":30,"maxScore":15}', 1, 4),
  ('THRESHOLD', '颜色阈值', '{"red":40,"yellow":70}', 1, 5);
