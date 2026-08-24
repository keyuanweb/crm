# Implementation Plan: 客户标签与细分

**Branch**: `031-customer-tags` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增标签体系与动态细分：`tag` + `customer_tag`（+lead_tag/contact_tag 预留）表 + `segment`（JSON 条件）；`TagService`（标签 CRUD/打标/筛选）+ `SegmentService`（动态细分条件解析实时计算成员）；Controller 提供标签/细分端点；前端客户列表标签列/筛选 + 标签管理页 + 细分管理页（条件编辑器）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: MyBatis-Plus、antd 5（Tag/Form/Modal）

**Storage**: 3 表 + Flyway V47；细分条件 JSON 存 segment 表

**Testing**: JUnit 5 + Mockito（TagService/SegmentService）、集成（TagIT：打标/筛选/细分成员）、前端（标签/细分页渲染）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 细分成员计算 ≤ 1s（中小数据量）；打标接口 ≤ 100ms

**Constraints**: 细分条件支持（标签 + 金额 + 最近跟进 + AND/OR）；成员实时计算；标签适用客户（预留线索/联系人）

**Scale/Scope**: 3 表 + 1 迁移 + 2 Service + 2 Controller + 前端标签管理/细分管理 + 客户列表标签

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/customer-tags.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（TagService/SegmentService 分离） |
| 原则三：数据完整性、安全与校验 | 服务端权限 | ✅ 满足（tag:manage + @RequirePermission；打标按数据范围） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（TagServiceTest/SegmentServiceTest/TagIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（细分 JSON 条件 + 实时计算，不引规则引擎） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/Tag.java / CustomerTag.java / Segment.java        # 新增
├── repository/TagMapper.java / CustomerTagMapper.java / SegmentMapper.java
├── dto/tag/TagRequest.java / TagResponse.java / SegmentRequest.java / SegmentResponse.java
├── service/TagService.java          # 标签 CRUD + 打标/移除 + 客户标签查询
├── service/SegmentService.java      # 细分 CRUD + 条件解析 + 成员计算（标签/金额/跟进）
├── controller/TagController.java    # /api/v1/tags + /api/v1/tags/customers/{id}（打标）
├── controller/SegmentController.java # /api/v1/segments + /members + /count
└── security/RequirePermission       # 复用（tag:manage 入字典）

backend/src/main/resources/db/migration/V47__tags_segments.sql
backend/src/test/java/com/crm/
├── service/TagServiceTest.java
├── service/SegmentServiceTest.java
└── integration/TagIT.java

frontend/src/
├── types/tag.ts / services/tagService.ts / services/segmentService.ts
├── pages/tags/TagListPage.tsx                    # 标签管理（系统管理分组）
├── pages/tags/SegmentListPage.tsx                # 细分管理（条件编辑器）
├── pages/customers/CustomerListPage.tsx          # 修改：标签列 + 标签筛选
└── App.tsx                                       # 注册路由
```

**Structure Decision**: 标签多对多（customer_tag）；细分条件 JSON（{ filters: [{ field: 'amount'|'tag'|'lastFollowUpDays', op, value }], logic: AND/OR }）；成员计算在 SegmentService 内按条件转 SQL 查询（多条件组合）；打标/筛选按数据范围（复用 012）。

## Complexity Tracking

> 无违规，本表留空。
