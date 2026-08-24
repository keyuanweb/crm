# Implementation Plan: 外勤拜访管理

**Branch**: `035-field-visit` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增外勤拜访：`field_visit` 表（计划：客户/时间/主题/状态 + 签到：经纬度/地址/时间/小结）；`FieldVisitService`（计划 CRUD + 签到打卡（防重复）+ 小结自动生成跟进记录 + 拜访统计）；`FieldVisitController`（/api/v1/field-visits + /check-in + /stats）；前端拜访列表页 + PWA 移动端签到（Geolocation 定位）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端，PWA 复用 027）

**Primary Dependencies**: MyBatis-Plus、FollowUpMapper（小结转跟进）、026 通知（拜访提醒）、027 PWA

**Storage**: 1 表 + Flyway V50

**Testing**: JUnit 5 + Mockito（计划 CRUD/签到防重复/小结转跟进/统计）、集成（FieldVisitIT）、前端（拜访列表渲染）

**Target Platform**: Web（+ PWA 移动端签到）

**Project Type**: Web 应用

**Performance Goals**: 签到 ≤ 100ms；统计聚合 ≤ 200ms

**Constraints**: 签到防重复（同计划一次）；签到记录经纬度/地址/时间；小结自动写跟进；拜访提醒复用 026

**Scale/Scope**: 1 表 + 1 迁移 + 1 Service + 1 Controller + 前端拜访列表/日历 + PWA 签到

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/field-visit.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（VisitService 独立） |
| 原则三：数据完整性、安全与校验 | 防重复/权限 | ✅ 满足（签到幂等 + visit:manage + 数据范围） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（FieldVisitServiceTest/FieldVisitIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（Geolocation 记录坐标，不做地理围栏） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/FieldVisit.java
├── repository/FieldVisitMapper.java
├── dto/visit/VisitRequest.java / VisitResponse.java / CheckInRequest.java / VisitStatsResponse.java
├── service/FieldVisitService.java       # 计划 CRUD + 签到（防重复）+ 小结转跟进 + 统计
├── controller/FieldVisitController.java # /api/v1/field-visits + /check-in + /stats
└── security/RequirePermission           # visit:manage（或复用 lead 权限）

backend/src/main/resources/db/migration/V50__field_visit.sql
backend/src/test/java/com/crm/
├── service/FieldVisitServiceTest.java
└── integration/FieldVisitIT.java

frontend/src/
├── types/visit.ts / services/visitService.ts
├── pages/visits/VisitListPage.tsx       # 拜访列表（状态/筛选 + 签到入口 + 统计卡）
└── App.tsx                               # 路由（销售分组）
```

**Structure Decision**: 计划状态机：PLANNED →（签到）DONE / CANCELED；签到写入经纬度/地址/时间 + 可选小结；小结生成 follow_up（type=VISIT）→ 客户时间线可见。统计：按销售/月份拜访次数与完成率（SQL group）。PWA 移动端在拜访详情页提供"立即签到"（Geolocation API 取坐标 + 反向地址用第三方逆地址或记录坐标原文）。

## Complexity Tracking

> 无违规，本表留空。
