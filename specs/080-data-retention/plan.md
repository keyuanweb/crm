# Implementation Plan: 数据保留策略（Data Retention Policy）

**Branch**: `080-data-retention` | **Date**: 2026-08-27 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/080-data-retention/spec.md`

## Summary

数据保留策略模块解决企业客户需要满足 GDPR/等保合规要求，对不同类型数据设置保留期限，到期自动归档或删除的问题。

核心能力：
1. **策略配置**：为不同数据实体设置保留期限（年/月/日）
2. **自动归档与删除**：定时扫描到期数据，归档到归档表，超期删除
3. **合规导出**：导出指定时间段内的数据，支持 CSV/Excel
4. **策略管理与审计**：查看/修改/删除策略，记录审计日志

技术方法：
- 后端：新增 DataRetentionPolicy/DataRetentionExecution/DataArchiveLog 实体，Spring Schedule 定时调度，动态创建归档表
- 前端：新增数据保留管理页面（策略列表/创建/执行日志）
- 数据：Flyway 迁移脚本创建策略表，归档表动态创建

## Technical Context

**Language/Version**: Java 21 + Spring Boot 3.2.x

**Primary Dependencies**: Spring Schedule, Spring Data JPA, Spring Boot Test

**Storage**: MySQL 8.0 + Flyway 迁移（schema 版本 73）

**Testing**: JUnit 5 + Spring Boot Test（后端）；Jest + React Testing Library（前端）

**Target Platform**: Web 应用（Spring Boot + React）

**Project Type**: 双端项目（后端 REST API + 前端 React SPA）

**Performance Goals**: 归档任务执行延迟 ≤ 1 小时；合规导出响应 ≤ 30 秒（10 万行）

**Constraints**: 归档操作不影响在线业务（并发访问等待 ≤ 5 秒）

**Scale/Scope**: 支持 10+ 实体类型的策略配置，归档表动态创建

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
specs/080-data-retention/
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
│   │   ├── DataRetentionPolicy.java
│   │   ├── DataRetentionExecution.java
│   │   └── DataArchiveLog.java
│   ├── repository/
│   │   ├── DataRetentionPolicyRepository.java
│   │   ├── DataRetentionExecutionRepository.java
│   │   └── DataArchiveLogRepository.java
│   ├── service/
│   │   ├── DataRetentionPolicyService.java
│   │   └── DataRetentionScheduler.java
│   └── controller/
│       └── DataRetentionPolicyController.java
├── src/main/resources/db/migration/
│   └── V74__data_retention.sql
└── src/test/java/com/crm/
    ├── service/
    │   └── DataRetentionPolicyServiceTest.java
    └── controller/
        └── DataRetentionPolicyControllerTest.java

frontend/
├── src/
│   ├── pages/
│   │   └── settings/
│   │       ├── DataRetentionPolicyListPage.tsx
│   │       └── DataRetentionPolicyCreatePage.tsx
│   └── services/
│       └── api/
│           └── dataRetentionApi.ts
└── tests/
    └── settings/
        └── DataRetentionPolicyListPage.test.tsx
```

**Structure Decision**: 双端项目结构，后端新增 3 个实体 + 2 个 Service + 1 个 Controller；前端新增 2 个页面 + 1 个 API 客户端。归档表通过 Flyway 迁移脚本动态创建（实体 + _archive 后缀）。

## Complexity Tracking

> 本功能无 Constitution Check 违规，无需复杂度追踪。
