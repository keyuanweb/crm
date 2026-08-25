# Implementation Plan: 性能与数据完整性模块

**Branch**: `064-performance-integrity` | **Date**: 2026-08-25 | **Spec**: [spec.md](./spec.md)

## Summary

1) atRiskCustomers 改为批量健康聚合（扩展 Customer360Service.healthLevelsBatch 返回 score）+ SQL 分页；2) CustomerService.create / LeadService.create 默认 owner=当前用户；3) 关键只读方法补 @Transactional(readOnly=true)。

## Technical Context

**Language/Version**: Java 17

**Primary Dependencies**: Customer360Service（批量评估）、DataPermissionService

**Storage**: 无迁移

**Testing**: CustomerServiceTest 扩展 + CustomerAtRiskIT + 全量 verify

**Target Platform**: Web（无新页面；接口行为加固）

**Project Type**: 性能/正确性修复

**Performance Goals**: 流失预警从 N+1（7N SQL）→ 固定批量查询

**Constraints**: 判定规则不变；导入路径不设 owner

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则四：测试优先与质量门禁 | 批量/owner 测试先行 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 复用批量评估 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
specs/064-performance-integrity/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── contracts/performance-integrity.md

backend/src/main/java/com/crm/
├── service/Customer360Service.java（healthLevelsBatch 扩展返回 score）
├── service/CustomerService.java（atRiskCustomers 批量聚合 + 分页；create 默认 owner）
├── service/LeadService.java（create 默认 owner）
├── service/（只读方法补 readOnly：CustomerService.page/detail、LeadService.page/detail 等）

backend/src/test/java/com/crm/
├── service/CustomerServiceTest.java（+默认 owner、批量聚合断言）
├── integration/CustomerAtRiskIT.java（预警分页 + 判定一致）
```

**Structure Decision**: healthLevelsBatch 增加返回 score 的重载；atRiskCustomers 分页后批量取分。

## Complexity Tracking

无违规，本表留空。
