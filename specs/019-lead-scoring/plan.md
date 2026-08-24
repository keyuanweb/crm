# Implementation Plan: 智能线索评分与销售预测校准

**Branch**: `019-lead-scoring` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/019-lead-scoring/spec.md`

## Summary

为线索引入自动评分：新增 `LeadScoreService`（规则引擎，来源渠道/信息完整度/跟进活跃度/互动时效可配置），在线索创建/更新/跟进后自动计算并写回 `lead.score`，列表按评分排序。为销售预测引入校准：`StageConversionService` 按历史销售机会统计各阶段转化率，样本充足用历史值、不足回退默认概率，替代 `DashboardStatsService` 的硬编码 `STAGE_PROBABILITY`。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus（复用 LeadMapper/FollowUpMapper/SalesOpportunityMapper）、antd 5

**Storage**: 评分规则存 `lead_score_config` 表（Flyway V43，仿 health_score_config 模式）；转化率实时统计，Redis 短缓存（可选）

**Testing**: JUnit 5 + Mockito（单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 评分单次计算 < 10ms（纯内存规则）；转化率统计缓存 5 分钟

**Constraints**: 复用 Lead 已有 score 字段（004）；不引入 ML 依赖；CLOSED_WON=1.0/CLOSED_LOST=0.0 固定不校准

**Scale/Scope**: 1 个评分服务 + 1 个转化率服务 + LeadService 接入 + DashboardStatsService 改造 + 前端排序/标识

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/lead-scoring.md 定义评分返回与预测校准响应） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（LeadScoreService/StageConversionService 独立，业务逻辑在 Service） |
| 原则三：数据完整性、安全与校验 | 服务端校验 | ✅ 满足（评分写回事务内；规则配置校验权重） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（LeadScoreServiceTest + StageConversionServiceTest + LeadIT 扩展 + 前端测试） |
| 原则五：简洁、可维护与可观测 | 无 N+1、结构化日志 | ✅ 满足（转化率一次聚合统计；评分/校准记 DEBUG 日志） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/019-lead-scoring/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（lead-scoring 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── service/LeadScoreService.java               # 新增：规则评分引擎
├── service/StageConversionService.java         # 新增：历史转化率统计
├── service/LeadService.java                    # 修改：create/update 后自动评分
├── service/FollowUpService.java                # 修改：跟进后触发线索重算
├── service/DashboardStatsService.java          # 修改：computeForecast 用校准概率
├── entity/LeadScoreConfig.java                 # 新增：评分配置实体
├── repository/LeadScoreConfigMapper.java       # 新增
├── dto/stats/ForecastItem.java                 # 修改：加 probabilitySource 字段
└── resources/db/migration/V43__lead_score_config.sql  # 新增：默认评分规则种子

backend/src/test/java/com/crm/
├── service/LeadScoreServiceTest.java           # 新增：评分维度/边界 单元测试
├── service/StageConversionServiceTest.java     # 新增：转化率/样本不足 单元测试
└── integration/LeadScoringIT.java              # 新增：评分+预测校准 集成测试

frontend/src/
├── types/lead.ts                               # 修改：LeadScore 相关类型
├── pages/leads/LeadListPage.tsx                # 修改：列表按评分排序 + 池评分展示
└── pages/leads/LeadListPage.render.test.tsx    # 修改：评分渲染断言
```

**Structure Decision**: 沿用既有分层。评分规则存配置表（Flyway V43 种子），评分实时计算写回 lead.score（创建/更新/跟进后触发）；转化率统计从 sales_opportunity 表一次聚合（阶段 → 进入下一阶段或赢单数/总量），样本 <10 回退默认。预测概率来源（HISTORICAL/DEFAULT）随 ForecastItem 返回。

## Complexity Tracking

> 无违规，本表留空。
