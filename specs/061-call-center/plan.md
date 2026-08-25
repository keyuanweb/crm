# Implementation Plan: 通话记录管理模块

**Branch**: `061-call-center` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

新增 `call_record` 表（客户/联系人可空、方向、时长秒、结果、备注）。CallRecordService（CRUD/归属校验/统计）+ CallRecordController。统计按方向/时间聚合。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用）

**Primary Dependencies**: CustomerMapper、ContactMapper（归属校验）

**Storage**: MySQL 新增 `call_record` 表（V68）

**Testing**: JUnit 5（CallRecordServiceTest 单元、CallCenterIT 集成）

**Target Platform**: Web（通话记录页：列表/录入/统计）

**Project Type**: 平台能力（新增）

**Performance Goals**: 记录/统计 ≤50ms

**Constraints**: 方向/结果枚举校验；联系人归属客户校验

**Scale/Scope**: 记录 ≤ 数千

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 独立服务 | ✅ 满足 |
| 原则三：数据完整性、安全与校验 | 枚举/归属校验 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/061-call-center/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/call-center.md

backend/src/main/java/com/crm/
├── entity/CallRecord.java + repository/CallRecordMapper.java
├── dto/call/（CallRecordRequest/Response/CallStatsResponse）
├── service/CallRecordService.java（CRUD/归属校验/统计）
├── controller/CallRecordController.java（/call-records + /call-records/stats）
├── common/ErrorCode.java（新增 CALL_* 错误码）
└── resources/db/migration/V68__call_record.sql

backend/src/test/java/com/crm/
├── service/CallRecordServiceTest.java
├── integration/CallCenterIT.java

frontend/src/
├── services/callRecordService.ts + types/callRecord.ts
├── pages/calls/CallRecordPage.tsx（列表/录入/统计）
└── App.tsx（工作台组新增"通话记录"菜单 → 路由）
```

**Structure Decision**: CallRecordService 独立；统计聚合查询；归属校验（联系人 customerId == 客户 id）。

## Complexity Tracking

无违规，本表留空。
