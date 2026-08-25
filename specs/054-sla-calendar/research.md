# Research: SLA 工作时间与节假日日历模块

**Branch**: `054-sla-calendar` | **Date**: 2026-08-25

## 1. 日历模型

**Decision**: `sla_calendar_config` 单表：`work_slots` JSON（如 [{"start":"09:00","end":"18:00"}]）、`work_days` JSON（[1..5] 周一至周五）、`holidays` JSON（["2026-10-01",...]）、enabled。全局单条（id=1）。

**Rationale**: 全局一条日历满足 v1（不按策略细分）；JSON 存储灵活支持多时间段。

## 2. 计算算法

**Decision**: `advanceWorkingTime(LocalDateTime from, double hours)`：
1. 无启用配置 → `from.plusHours(hours)`（回退旧行为）。
2. 否则：从 from 开始，逐分钟/逐小时推进，仅在 工作时段 ∩ 工作日 ∩ 非节假日 内消耗 SLA 时长；到休息时刻跳到下一工作时段/下一工作日。

**Rationale**: 分钟粒度（或 15 分钟粒度）足够精确且实现简单；纯函数可测。

**Alternatives considered**: 第三方工作日历库——引入依赖且时区语义复杂；自实现更可控。

## 3. 生效范围

**Decision**: 仅新工单（create 时 applySla）使用日历；既有工单 deadline 不变。节假日为固定日期（不解析年度重复）。

**Rationale**: 追溯改动既有工单会产生不可预期影响；年度重复规则复杂度高（v1 可手动维护）。
