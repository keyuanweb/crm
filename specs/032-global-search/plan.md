# Implementation Plan: 全局搜索

**Branch**: `032-global-search` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增全局搜索：`SearchService` 聚合查询（客户/线索/联系人/商机/工单/产品，关键字 LIKE 匹配，按数据权限过滤）；`SearchController`（/api/v1/search 下拉 Top N 分组 + /api/v1/search/full 结果页分页）；前端 Header 全局搜索框（防抖即时下拉 + 回车结果页）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: MyBatis-Plus、antd 5（AutoComplete/Input.Search）、react-router

**Storage**: 无新表（复用现有实体查询）

**Testing**: JUnit 5 + Mockito（SearchService）、集成（SearchIT）、前端（Header 搜索框渲染测试）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 搜索响应 ≤ 500ms；每实体 Top 5 下拉 / 20 结果页

**Constraints**: LIKE 匹配 + 多关键字 AND；数据权限过滤（ADMIN 全量/其他本人）；不引 ES

**Scale/Scope**: 1 Service + 1 Controller + DTO + Header 搜索框 + 结果页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/global-search.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（SearchService 聚合多实体） |
| 原则三：数据完整性、安全与校验 | 数据权限 | ✅ 满足（搜索结果按 012 数据范围过滤） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（SearchServiceTest/SearchIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（LIKE 查询，不引 ES） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── dto/search/SearchResponse.java          # { keyword, groups: [{ type, label, items: [{ id, title, subtitle, path }] }] }
├── service/SearchService.java              # 多实体 LIKE 聚合（客户/线索/联系人/商机/工单/产品）+ 数据权限
├── controller/SearchController.java        # GET /api/v1/search（下拉）+ GET /api/v1/search/full（结果页）

backend/src/test/java/com/crm/
├── service/SearchServiceTest.java
└── integration/SearchIT.java

frontend/src/
├── services/searchService.ts               # searchAll(keyword) / searchFull(keyword, type, page)
├── components/GlobalSearch.tsx             # Header 搜索框（防抖下拉 + 回车结果页）
├── pages/search/SearchResultPage.tsx       # 结果页（Tab 分组 + 高亮 + 分页）
└── App.tsx                                 # Header 集成 GlobalSearch + 路由 /search
```

**Structure Decision**: SearchService 对各实体执行 LIKE 查询（名称/公司/电话等字段），按数据权限过滤（ADMIN 全量 / 其他按 created_by/owner），合并为分组响应；下拉 Top 5/实体，结果页分页 20；前端 Header 搜索框防抖 300ms + AutoComplete 下拉 + 回车导航 /search?q=。

## Complexity Tracking

> 无违规，本表留空。
