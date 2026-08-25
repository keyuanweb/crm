# Research: 通话记录管理模块

**Branch**: `061-call-center` | **Date**: 2026-08-25

## 1. 记录模型

**Decision**: `call_record` 表（customer_id 可空、contact_id 可空、direction INBOUND/OUTBOUND、duration_seconds、result CONNECTED/NO_ANSWER/BUSY/FAILED、remark、recorded_by/at）。联系人提供时校验其 customerId 匹配所选客户。

**Rationale**: 客户/联系人可空支持外部号码直录；归属校验保证数据一致。

## 2. 统计

**Decision**: `GET /call-records/stats?from&to&direction` 聚合：total_count、total_duration、avg_duration（按方向筛选）。一次查询。

**Rationale**: 聚合 SQL 一次返回；方向/时间筛选满足常用视图。

## 3. CTI 集成预留

**Decision**: v1 手工/接口录入（POST /call-records 即 CTI 自动创建的接入点）；数据模型含方向/时长/结果/时间，CTI 硬件侧自动创建记录时只需 POST 调用。录音链接占位于备注。

**Rationale**: 数据模型与接口就绪，硬件集成零改造接入。
