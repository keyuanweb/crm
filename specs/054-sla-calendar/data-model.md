# 数据模型：SLA 工作时间与节假日日历模块

**Branch**: `054-sla-calendar` | **Date**: 2026-08-25

## 1. sla_calendar_config（SLA 日历配置，Flyway V61）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| id | BIGINT | PK, AUTO | 全局单条（约定 id=1） |
| work_slots | TEXT | NULL | 工作时间段 JSON：`[{"start":"09:00","end":"18:00"}]` |
| work_days | TEXT | NULL | 工作周 JSON：`[1,2,3,4,5]`（1=周一） |
| holidays | TEXT | NULL | 节假日 JSON：`["2026-10-01"]` |
| enabled | TINYINT | NOT NULL DEFAULT 0 | 启用 |
| created_at / updated_at | DATETIME | NOT NULL | |

## 2. 业务规则

- 无启用配置：`deadline = now + hours`（回退旧行为）。
- 启用配置：SLA 时长仅在 工作时段 ∩ 工作日 ∩ 非节假日 内消耗。
- 仅新工单生效（TicketService.create 时 applySla）。
- 时间粒度为分钟。
