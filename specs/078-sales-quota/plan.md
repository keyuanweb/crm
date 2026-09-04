# Implementation Plan: 销售配额分解（Sales Quota Decomposition）

**Branch**: `078-sales-quota` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/078-sales-quota/spec.md`

## Summary

销售配额分解模块解决 020 销售目标仅有月度维度、无法按团队/季度/区域分解的问题。管理者需要将年度/季度目标逐层分解到团队和个人，并跟踪达成率。

核心能力：
1. **配额逐层分解**：年度→季度→团队→个人，自动校验总和一致性
2. **达成率跟踪**：实时计算各层级达成率，低达成预警
3. **配额调整与版本管理**：支持中途调整，保留历史版本
4. **多维度对比分析**：团队/个人/时间维度对比

技术方法：
- 后端：新增 SalesQuota/SalesQuotaVersion/SalesQuotaBreakdown/SalesQuotaAchievement 实体，RESTful API
- 前端：新增配额管理页面（列表/分解/跟踪/对比），复用现有 ProTable + 统计卡片布局
- 数据：Flyway 迁移脚本创建新表，与现有 Opportunity/Department 表关联

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2.x

**Primary Dependencies**: Spring Security, Spring Data JPA, Spring Boot Test

**Storage**: MySQL 8.0 + Flyway 迁移（schema 版本 71）

**Testing**: JUnit 5 + Spring Boot Test（后端）；Jest + React Testing Library（前端）

**Target Platform**: Web 应用（Spring Boot + React）

**Project Type**: 双端项目（后端 REST API + 前端 React SPA）

**Performance Goals**: 达成率计算延迟 ≤ 1 秒；配额分解校验 ≤ 500ms

**Constraints**: 分解总和必须严格一致（误差 ≤ 0.01）；配额调整不影响已关闭期间数据

**Scale/Scope**: 支持 100+ 团队、1000+ 销售人员的配额管理

**Project Structure**: 双端项目（backend/ + frontend/）

## Constitution Check

**GATE**: 必须通过才能进入 Phase 0 研究。Phase 1 设计后重新检查。

| 原则 | 合规性 | 说明 |
|------|--------|------|
| 一、契约优先 API 设计 | ✅ 合规 | 新增 REST 端点，OpenAPI 契约先行，前后端 DTO 由契约生成 |
| 二、分层架构 | ✅ 合规 | Controller → Service → Repository 分层，业务逻辑在 Service 层 |
| 三、数据完整性与安全 | ✅ 合规 | DTO 声明 Jakarta Bean Validation；授权服务端强制执行；事务在 Service 层 |
| 四、测试优先 | ✅ 合规 | JUnit 5 + Spring Boot Test；测试金字塔：单元测试为主 |
| 五、简洁可维护 | ✅ 合规 | 列表端点分页；N+1 查询避免；结构化日志 SLF4J |

**结论**: 所有合规项通过，无需要豁免的违规。

## Project Structure

### Documentation (this feature)

```text
specs/078-sales-quota/
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
│   │   ├── SalesQuota.java
│   │   ├── SalesQuotaVersion.java
│   │   ├── SalesQuotaBreakdown.java
│   │   └── SalesQuotaAchievement.java
│   ├── repository/
│   │   ├── SalesQuotaRepository.java
│   │   ├── SalesQuotaVersionRepository.java
│   │   ├── SalesQuotaBreakdownRepository.java
│   │   └── SalesQuotaAchievementRepository.java
│   ├── service/
│   │   ├── SalesQuotaService.java
│   │   └── SalesQuotaAchievementService.java
│   └── controller/
│       └── SalesQuotaController.java
├── src/main/resources/db/migration/
│   └── V71__sales_quota.sql
└── src/test/java/com/crm/
    ├── service/
    │   └── SalesQuotaServiceTest.java
    └── controller/
        └── SalesQuotaControllerTest.java

frontend/
├── src/
│   ├── pages/
│   │   └── quotas/
│   │       ├── QuotaListPage.tsx
│   │       ├── QuotaBreakdownPage.tsx
│   │       └── QuotaAchievementPage.tsx
│   └── services/
│       └── api/
│           └── quotaApi.ts
└── tests/
    └── quotas/
        ├── QuotaListPage.test.tsx
        └── QuotaBreakdownPage.test.tsx
```

**Structure Decision**: 双端项目结构，后端新增 4 个实体 + 2 个 Service + 1 个 Controller；前端新增 3 个页面 + 1 个 API 客户端。复用现有 020 销售目标的统计卡片 + ProTable 布局模式。

## Complexity Tracking

> 本功能无 Constitution Check 违规，无需复杂度追踪。
