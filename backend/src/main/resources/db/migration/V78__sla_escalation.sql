-- ============================================================
-- V78__sla_escalation.sql — SLA 自动升级（1.3-sla-escalation）
-- 说明：
--   在 015 已建模的 SLA（sla_policy / sla_calendar_config / ticket.sla_*）之上补三件事：
--     ① 让「响应超时」在数据上可判定（此前无 sla_responded_at，OVERDUE 只认 resolve）；
--     ② 让「解决时间」可判定（此前无 resolved_at，达成率无从计算）；
--     ③ 给分级升级提供幂等载体（escalate_level / last_escalated_at）。
--   升级作业的扫描条件（未关闭 AND deadline 进入预警窗口）此前无索引可用。
-- ============================================================

ALTER TABLE `ticket`
  ADD COLUMN `sla_responded_at` DATETIME DEFAULT NULL COMMENT 'SLA 首次响应时间（首条回复时刻），NULL 表示尚未响应',
  ADD COLUMN `resolved_at` DATETIME DEFAULT NULL COMMENT '解决时刻（流转至 RESOLVED/CLOSED 时写入；历史行的近似回填见本文件末尾）',
  ADD COLUMN `escalate_level` TINYINT NOT NULL DEFAULT 0 COMMENT 'SLA 升级级别 0 无/1 警告/2 超时/3+ 持续超时，单调递增不回退',
  ADD COLUMN `last_escalated_at` DATETIME DEFAULT NULL COMMENT '最近一次升级时刻，用于计算下一次升级间隔';

-- 扫描索引：升级作业按「deleted = 0 AND status IN (未关闭) AND deadline <= now + 预警窗口」取候选集。
-- 前两列是等值/IN 条件，第三列是范围条件，故顺序为 (deleted, status, deadline)。
ALTER TABLE `ticket`
  ADD KEY `idx_ticket_sla_scan` (`deleted`, `status`, `sla_resolve_deadline`);

-- ---------- 历史数据回填 ----------

-- ① 响应时间：已有回复的工单回填为「首条回复的时刻」。
--    ⚠️ 必须回填：computeSlaStatus 新增的 respond 侧 OVERDUE 判定以 sla_responded_at IS NULL 为前提，
--    不回填会让所有「已答复过」的历史工单在 respond deadline 过期后被批量误判为响应超时。
--    （语义近似：首条回复未必是本工单的「首次响应」，工单没有指派/认领时刻可用。）
UPDATE `ticket` t
SET t.`sla_responded_at` = (
  SELECT MIN(r.`created_at`) FROM `ticket_reply` r WHERE r.`ticket_id` = t.`id`
)
WHERE EXISTS (SELECT 1 FROM `ticket_reply` r WHERE r.`ticket_id` = t.`id`);

-- ② 解决时间：对已结束（RESOLVED/CLOSED）的历史行用 updated_at 近似回填。
--    ⚠️ 这是近似值：本系统没有工单流转历史表，无法还原真实的解决时刻。
--    后续任何以 resolved_at 计算 SLA 解决达成率的口径，都必须知道这批历史值是近似值
--    （updated_at 可能被解决之后的编辑/加备注等写入刷新）。
UPDATE `ticket`
SET `resolved_at` = `updated_at`
WHERE `status` IN ('RESOLVED', 'CLOSED') AND `resolved_at` IS NULL;
