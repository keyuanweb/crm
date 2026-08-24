# Implementation Plan: 自定义报表

**Branch**: `021-custom-reports` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/021-custom-reports/spec.md`

## Summary

新增自定义报表能力：`ReportService` 按维度（销售/产品/线索来源/商机阶段/时间）+ 指标（数量/金额）+ 时间范围聚合业务数据，返回表格结果；支持导出 Excel（复用 POI）与保存报表模板（P3，report_template 表）。前端新增报表页（维度/指标/时间选择 + 结果表格 + 导出 + 模板）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus（SalesOpportunityMapper/QuoteItemMapper/LeadMapper/UserMapper/ProductMapper）、POI（已有）、antd 5

**Storage**: 报表模板存 `report_template` 表（Flyway V45，P3）；报表结果实时计算不持久化

**Testing**: JUnit 5 + Mockito（单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 报表查询 ≤ 1s（万级数据，内存分组）

**Constraints**: 维度/指标限定有意义组合；聚合用现有 Mapper + 内存分组；复用 016 POI 导出；遵循 012 数据权限

**Scale/Scope**: 1 个报表服务 + 1 个模板实体/表 + 1 个控制器 + 前端报表页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/custom-reports.md 定义查询/导出/模板契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（ReportService 聚合 + ReportTemplateService 模板） |
| 原则三：数据完整性、安全与校验 | 服务端校验与权限 | ✅ 满足（维度/时间校验；报表按 012 权限过滤） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（ReportServiceTest + CustomReportsIT + 前端渲染测试） |
| 原则五：简洁、可维护与可观测 | 无 N+1、结构化日志 | ✅ 满足（内存分组一次遍历；聚合记 DEBUG 日志） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/021-custom-reports/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（custom-reports 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── service/ReportService.java                     # 新增：维度/指标/时间聚合
├── service/ReportTemplateService.java             # 新增：模板保存/加载（P3）
├── controller/ReportController.java               # 新增：POST /api/v1/reports/query、GET /reports/export、模板 CRUD
├── entity/ReportTemplate.java                     # 新增（P3）
├── repository/ReportTemplateMapper.java           # 新增（P3）
├── dto/report/ReportQuery.java                    # 新增：维度/指标/时间/分页
├── dto/report/ReportRow.java                      # 新增：聚合行（dimensionValue/count/amount/ratio）
├── dto/report/ReportResult.java                   # 新增：rows + 合计
└── resources/db/migration/V45__report_template.sql  # 新增（P3）

backend/src/test/java/com/crm/
├── service/ReportServiceTest.java                 # 新增：各维度聚合正确性
└── integration/CustomReportsIT.java               # 新增：查询 + 导出 + 权限

frontend/src/
├── types/report.ts                                # 新增：ReportQuery/ReportRow 类型
├── services/reportService.ts                      # 新增：queryReport/exportReport
├── pages/reports/ReportCenterPage.tsx             # 新增：报表页（维度/指标/时间 + 表格 + 导出）
└── App.tsx                                        # 修改：注册报表路由（数据分析分组）
```

**Structure Decision**: 沿用既有分层。维度聚合用现有 Mapper selectList + 内存 LinkedHashMap 分组（数据量万级）；导出复用 016 POI（生成 xlsx 响应流）；模板存 report_template 表（P3，本期基础实现）。报表数据按 012 权限过滤（SALES 仅自身）。

## Complexity Tracking

> 无违规，本表留空。
