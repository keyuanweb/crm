# Implementation Plan: 客户 360 画像与健康度评分

**Branch**: `018-customer-360` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-customer-360/spec.md`

## Summary

扩展客户详情为 360 全景：在现有 `GET /customers/{id}` 聚合（商机/跟进/联系人）基础上，补充订单与回款、合同、工单及金额汇总；新增健康度评分服务（规则引擎，可配置权重/阈值），计算 0-100 分 + 红黄绿标识 + 失分原因；新增流失预警列表接口，支持一键跟进。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus 3.5.5（复用现有 Mapper）、antd 5（Descriptions/Statistic/Timeline/Tabs）、@tanstack/react-query

**Storage**: 无新表。健康度评分规则存配置表 `health_score_config`（Flyway 迁移）或沿用配置项；评分实时计算不持久化

**Testing**: JUnit 5 + Mockito（后端单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 客户 360 聚合查询 ≤ 500ms（批量装配，无 N+1）

**Constraints**: 复用现有数据权限（012）与跟进流程（001）；评分纯规则引擎（非 ML）；不引入新依赖

**Scale/Scope**: 1 个聚合 DTO 扩展 + 1 个评分服务 + 1 个预警接口 + 前端详情页扩展 + 预警列表页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/customer-360.md 定义聚合与评分/预警契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（Customer360Service 聚合 + HealthScoreService 评分，Controller 薄） |
| 原则三：数据完整性、安全与校验 | 服务端强制权限 | ✅ 满足（评分计算基于 checkViewPermission 过滤后的数据） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（HealthScoreServiceTest + Customer360IT + 前端渲染测试） |
| 原则五：简洁、可维护与可观测 | 无 N+1、结构化日志 | ✅ 满足（批量装配复用现有模式；评分计算记 DEBUG 日志） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/018-customer-360/
├── plan.md              # 本文件
├── research.md          # Phase 0（评分维度与规则设计）
├── data-model.md        # Phase 1（聚合 DTO + 评分配置）
├── quickstart.md        # Phase 1
├── contracts/           # Phase 1（customer-360 契约）
└── tasks.md             # Phase 2（/speckit-tasks）
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── dto/customer/Customer360Response.java      # 新增：聚合 DTO（含金额汇总）
├── dto/customer/HealthScoreDTO.java            # 新增：评分 + 颜色 + 失分原因
├── dto/customer/CustomerHealthBrief.java       # 新增：预警列表项
├── service/HealthScoreService.java             # 新增：规则评分引擎
├── service/Customer360Service.java             # 新增：聚合订单/合同/工单/金额
├── service/CustomerService.java                # 修改：detail 组合 360 聚合
├── controller/CustomerController.java          # 修改：GET /customers/{id} 扩展 + GET /customers/health/at-risk
├── entity/HealthScoreConfig.java               # 新增：评分配置实体（维度/权重/阈值）
├── repository/HealthScoreConfigMapper.java     # 新增
└── resources/db/migration/V42__health_score_config.sql  # 新增：默认配置种子

backend/src/test/java/com/crm/
├── service/HealthScoreServiceTest.java         # 新增：评分维度/阈值/边界 单元测试
└── integration/Customer360IT.java              # 新增：聚合 + 评分 + 预警 集成测试

frontend/src/
├── types/customer.ts                           # 修改：Customer360Response/HealthScore 类型
├── services/customerService.ts                 # 修改：fetchCustomer360/fetchAtRiskCustomers
├── pages/customers/CustomerDetailPage.tsx      # 修改：客户 360 区块（Tabs 聚合 + 评分标识）
├── pages/customers/AtRiskCustomersPage.tsx     # 新增：流失预警列表页
└── App.tsx                                     # 修改：注册预警页路由
```

**Structure Decision**: 沿用既有前后端分层与 DTO 风格。健康度评分规则存配置表（Flyway V42 迁移 + 默认种子），评分实时计算不落库（规则变更立即生效）。聚合复用现有 Mapper 批量装配模式，避免 N+1。

## Complexity Tracking

> 无违规，本表留空。
