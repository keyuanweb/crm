# Research: 工单满意度调查（CSAT/NPS）模块

**Branch**: `051-csat-nps` | **Date**: 2026-08-25

## 1. 评分模型

**Decision**: `ticket_survey` 表（ticket_id 唯一、rating 1-5、comment ≤500、created_by、created_at）。工单 CLOSED 后可提交；一张工单一次评分（唯一约束）；仅 CLOSED 状态可评（422）。

**Rationale**: 工单关闭即服务完成节点，此时评分最合理；唯一约束防重复。

**Alternatives considered**: 工单表加 rating 字段——统计需扫描工单、评语冗余；独立表更清晰。

## 2. NPS 分档

**Decision**: 评分 1-5，NPS 按比例映射：1-3 → 贬损者、4 → 中立者、5 → 推荐者。NPS = (推荐% - 贬损%)。CSAT 均值 = rating 平均分（1-5）。

**Rationale**: 单一评分体系（1-5）同时支撑 CSAT 与 NPS，避免双评分（0-10 vs 1-5）混淆；映射与国际惯例（0-6/7-8/9-10）同比例对应。

## 3. 统计接口

**Decision**: `GET /surveys/stats?from&to` 返回：sampleCount、csatAverage（1-5）、npsScore（-100~100）、promoter/passive/detractor 数量与占比。一次查询聚合。

**Rationale**: 聚合 SQL 一次返回全部统计，前端直接展示；时间范围可选。
