# Implementation Plan: 批量导入增强（线索/联系人导入）

**Branch**: `024-bulk-import` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/024-bulk-import/spec.md`

## Summary

复用 016 客户导入的 POI 解析与 ImportResult 结构，新增 `LeadExcelService`（线索导入/模板/失败明细）与 `ContactExcelService`（联系人导入/模板/失败明细）；线索/联系人列表页新增"导入/下载模板"按钮。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus（LeadMapper/ContactMapper/CustomerMapper）、POI（已有）、antd 5（Upload）

**Storage**: 无新表；导入结果实时返回（复用 ImportResult）

**Testing**: JUnit 5 + Mockito（单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 千行级导入 < 5 秒（逐行校验 + 批量插入）

**Constraints**: 复用 ImportResult/POI 模式；联系人按客户名称精确匹配（不区分大小写）；导入归属当前用户

**Scale/Scope**: 2 个 Excel 服务 + 4 个端点 + 前端 2 页按钮

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/bulk-import.md 定义导入/模板/失败明细契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（LeadExcelService/ContactExcelService 独立） |
| 原则三：数据完整性、安全与校验 | 服务端校验 | ✅ 满足（必填/枚举/评分校验；文件类型校验） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（ExcelServiceTest + BulkImportIT + 前端渲染测试） |
| 原则五：简洁、可维护与可观测 | 结构化日志 | ✅ 满足（导入记审计 + 结果日志） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/024-bulk-import/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（bulk-import 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── service/LeadExcelService.java               # 新增：线索导入/模板/失败明细（复用 ImportResult）
├── service/ContactExcelService.java            # 新增：联系人导入/模板/失败明细
├── controller/LeadController.java              # 修改：POST /leads/import、GET /leads/import-template、GET /leads/import-failures
├── controller/ContactController.java           # 修改：POST /contacts/import、GET /contacts/import-template、GET /contacts/import-failures

backend/src/test/java/com/crm/
├── service/LeadExcelServiceTest.java           # 新增：解析/校验/成功失败 单元测试
├── service/ContactExcelServiceTest.java        # 新增：客户匹配/校验 单元测试
└── integration/BulkImportIT.java               # 新增：导入 + 模板 + 失败明细 集成测试

frontend/src/
├── pages/leads/LeadListPage.tsx                # 修改：导入/下载模板按钮（toolbar）
├── pages/contacts/ContactListPage.tsx          # 修改：导入/下载模板按钮
├── services/leadService.ts                     # 修改：importLeads/downloadLeadTemplate
├── services/contactService.ts                  # 修改：importContacts/downloadContactTemplate
└── types/importResult.ts                       # 新增：ImportResult 类型
```

**Structure Decision**: 沿用既有分层与 016 导入模式（POI 解析 xlsx + ImportResult 反馈）。LeadExcelService 解析时调用 LeadScoreService 自动评分（可选），ContactExcelService 按客户名称匹配 customerId。

## Complexity Tracking

> 无违规，本表留空。
