# Implementation Plan: 定时导出订阅（Scheduled Export Subscription）

**Branch**: `079-scheduled-export` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/079-scheduled-export/spec.md`

## Summary

定时导出订阅模块解决企业用户需要定时自动导出报表/数据并邮件发送的问题。用户可选择导出实体、筛选条件、导出格式，设置执行周期，系统自动执行并邮件推送。

核心能力：
1. **定时任务创建**：选择实体/筛选/格式/周期，自动计算下次执行时间
2. **定时执行与邮件推送**：自动执行导出，生成文件后邮件发送
3. **执行历史与手动触发**：查看执行记录，支持立即执行
4. **任务管理**：暂停/恢复/删除定时任务

技术方法：
- 后端：新增 ScheduledExport/ScheduledExportExecution 实体，Spring Schedule 定时调度，复用 016 导出逻辑 + 030 邮件发送
- 前端：新增定时导出管理页面（列表/创建/执行历史）
- 数据：Flyway 迁移脚本创建新表

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2.x

**Primary Dependencies**: Spring Schedule, Spring Data JPA, Spring Boot Test

**Storage**: MySQL 8.0 + Flyway 迁移（schema 版本 72）

**Testing**: JUnit 5 + Spring Boot Test（后端）；Jest + React Testing Library（前端）

**Target Platform**: Web 应用（Spring Boot + React）

**Project Type**: 双端项目（后端 REST API + 前端 React SPA）

**Performance Goals**: 定时任务执行延迟 ≤ 5 分钟；执行历史查询 ≤ 2 秒

**Constraints**: 单用户最多 10 个活跃任务；单次导出最多 10 万行

**Scale/Scope**: 支持 1000+ 用户，每个用户最多 10 个定时任务

**Project Structure**: 双端项目（backend/ + frontend/）

## Constitution Check

**GATE**: 必须通过才能进入 Phase 0 研究。Phase 1 设计后重新检查。

| 原则 | 合规性 | 说明 |
|------|--------|------|
| 一、契约优先 API 设计 | ✅ 合规 | 新增 REST 端点，OpenAPI 契约先行 |
| 二、分层架构 | ✅ 合规 | Controller → Service → Repository 分层 |
| 三、数据完整性与安全 | ✅ 合规 | DTO 校验；授权服务端强制执行；事务在 Service 层 |
| 四、测试优先 | ✅ 合规 | JUnit 5 + Spring Boot Test |
| 五、简洁可维护 | ✅ 合规 | 列表端点分页；N+1 查询避免；结构化日志 |

**结论**: 所有合规项通过，无需要豁免的违规。

## Project Structure

### Documentation (this feature)

```text
specs/079-scheduled-export/
├── plan.md              # 本文件（/speckit-plan 命令输出）
├── research.md          # Phase 0 输出（/speckit-plan 命令）
├── data-model.md        # Phase 1 输出（/speckit-plan 命令）
├── quickstart.md        # Phase 1 输出（/speckit-plan 命令）
├── contracts/           # Phase 1 输出（/speckit-plan 命令）
└── tasks.md             # Phase 2 输出（/speckit-tasks 命令，非 /speckit-plan 创建）
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/crm/
│   ├── model/entity/
│   │   ├── ScheduledExport.java
│   │   └── ScheduledExportExecution.java
│   ├── repository/
│   │   ├── ScheduledExportRepository.java
│   │   └── ScheduledExportExecutionRepository.java
│   ├── service/
│   │   ├── ScheduledExportService.java
│   │   └── ScheduledExportScheduler.java
│   └── controller/
│       └── ScheduledExportController.java
├── src/main/resources/db/migration/
│   └── V72__scheduled_export.sql
└── src/test/java/com/crm/
    ├── service/
    │   └── ScheduledExportServiceTest.java
    └── controller/
        └── ScheduledExportControllerTest.java

frontend/
├── src/
│   ├── pages/
│   │   └── exports/
│   │       ├── ScheduledExportListPage.tsx
│   │       └── ScheduledExportCreatePage.tsx
│   └── services/
│       └── api/
│           └── scheduledExportApi.ts
└── tests/
    └── exports/
        └── ScheduledExportListPage.test.tsx
```

**Structure Decision**: 双端项目结构，后端新增 2 个实体 + 2 个 Service + 1 个 Controller；前端新增 2 个页面 + 1 个 API 客户端。复用现有 016 导出中心的导出逻辑和 030 邮件营销的邮件发送能力。

## Complexity Tracking

> 本功能无 Constitution Check 违规，无需复杂度追踪。
