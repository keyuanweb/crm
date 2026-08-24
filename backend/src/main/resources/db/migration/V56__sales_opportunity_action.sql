-- ============================================================
-- V56__sales_opportunity_action.sql — 销售机会动作完成（045-sales-playbook）
-- ============================================================

CREATE TABLE `sales_opportunity_action` (
  `id`             BIGINT   NOT NULL AUTO_INCREMENT,
  `opportunity_id` BIGINT   NOT NULL COMMENT '销售机会 id',
  `template_id`    BIGINT   NOT NULL COMMENT '动作模板 id',
  `completed_by`   BIGINT   NOT NULL COMMENT '完成人',
  `completed_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_opp_action` (`opportunity_id`, `template_id`),
  KEY `idx_opp_action_template` (`template_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
