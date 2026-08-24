# Implementation Plan: 客户查重合并

**Branch**: `034-customer-merge` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增查重合并：`CustomerMergeService`（查重扫描：名称归一化 LIKE + 电话/邮箱精确 → 疑似重复对含相似度与关联计数；合并：主/从记录选择 → 关联数据（订单/商机/联系人/跟进/工单/标签/共享）customer_id 转移 → 字段冲突主优先 → 从记录逻辑删除进回收站 + 审计）；`CustomerMergeController`（/api/v1/customers/duplicates + /merge）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: MyBatis-Plus、025 回收站（RecycleBinService）、012 数据权限

**Storage**: 无新表（查重临时计算；合并审计复用 audit；回收站复用 025）

**Testing**: JUnit 5 + Mockito（查重规则/合并转移）、集成（MergeIT：建重复对→查重检出→合并转移+回收站）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 查重扫描 ≤ 1s（分批 500）；合并事务 ≤ 200ms

**Constraints**: 名称归一化（去空格/大小写/全半角）；电话/邮箱精确；合并关联数据全转移；主记录保留

**Scale/Scope**: 1 Service + 1 Controller + 前端查重合并页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/customer-merge.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（MergeService 独立） |
| 原则三：数据完整性、安全与校验 | 事务/审计 | ✅ 满足（合并单事务 + 审计 + 回收站） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（CustomerMergeServiceTest/MergeIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（SQL LIKE + 归一化，不引模糊匹配库） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── dto/merge/DuplicateGroupResponse.java   # { id, primary, duplicates: [{ customerId, similarity, relatedCount }] }
├── dto/merge/MergeRequest.java             # { primaryId, duplicateId }
├── service/CustomerMergeService.java       # 查重扫描 + 合并（关联数据转移 + 回收站 + 审计）
├── controller/CustomerMergeController.java # GET /api/v1/customers/duplicates + POST /api/v1/customers/merge

backend/src/test/java/com/crm/
├── service/CustomerMergeServiceTest.java
└── integration/MergeIT.java

frontend/src/
├── types/merge.ts / services/customerMergeService.ts
├── pages/customers/DuplicateMergePage.tsx  # 查重合并页（扫描 + 重复对列表 + 合并确认）
└── App.tsx                                 # 路由
```

**Structure Decision**: 查重：名称按归一化后分组（名称+公司相同或高度相似）用 GROUP BY + 归一化函数；电话/邮箱重复用精确匹配。生成重复对：同组客户两两组合（或主 + 从列表），含相似度（0-100，名称编辑距离简化：相同=100，模糊=按前缀/包含估算）与关联计数（订单+商机+联系人+跟进+工单数）。合并：单事务内批量 UPDATE 各关联表 customer_id=主，标签/共享合并去重，字段冲突主优先（主空则取从），从记录逻辑删除 + 进回收站（025）+ 审计。

## Complexity Tracking

> 无违规，本表留空。
