-- ============================================================
-- V42__health_score_config.sql — 客户健康度评分配置（018-customer-360）
-- 说明：维度权重可配置（合计 100），评分实时计算不落库；
--       默认种子 5 维度，管理员可调整权重/阈值
-- ============================================================

CREATE TABLE `health_score_config` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `dimension_key`  VARCHAR(50)  NOT NULL COMMENT '维度键：FOLLOWUP/PAYMENT/TICKET/DEPTH/ACTIVITY',
  `dimension_label` VARCHAR(100) NOT NULL COMMENT '维度中文名（跟进活跃度等）',
  `weight`         INT          NOT NULL COMMENT '权重（合计 100）',
  `params_json`    VARCHAR(500) DEFAULT NULL COMMENT '参数 JSON（天数阈值等）',
  `enabled`        TINYINT(1)   NOT NULL DEFAULT 1,
  `sort_order`     INT          NOT NULL DEFAULT 0,
  `deleted`        TINYINT(1)   NOT NULL DEFAULT 0,
  `version`        INT          NOT NULL DEFAULT 0,
  `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_health_score_dimension` (`dimension_key`),
  KEY `idx_health_score_enabled` (`enabled`, `sort_order`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 默认 5 维度种子（research.md R2）：
-- FOLLOWUP=30 跟进活跃度（最近 30 天有跟进满分，>90 天 0）
-- PAYMENT=25  回款及时性（逾期按比例扣分）
-- TICKET=20   工单/投诉（OPEN/OVERDUE 扣分）
-- DEPTH=15    合作深度（按累计成交金额分档）
-- ACTIVITY=10 近期互动（任意业务活动递减）
INSERT INTO `health_score_config`
  (`dimension_key`, `dimension_label`, `weight`, `params_json`, `enabled`, `sort_order`)
VALUES
  ('FOLLOWUP',  '跟进活跃度', 30, '{"activeDays":30,"zeroDays":90}', 1, 1),
  ('PAYMENT',   '回款及时性', 25, '{}', 1, 2),
  ('TICKET',    '工单服务',   20, '{}', 1, 3),
  ('DEPTH',     '合作深度',   15, '{"tiers":[{"amount":1000000,"score":15},{"amount":100000,"score":10}]}', 1, 4),
  ('ACTIVITY',  '近期互动',   10, '{"activeDays":14}', 1, 5);
