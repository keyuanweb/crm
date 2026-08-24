# Implementation Plan: 在线表单线索收集

**Branch**: `036-online-forms` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增在线表单：`form`（表单定义：字段配置 JSON/成功提示/来源）+ `form_submission`（提交快照）；`FormService`（表单 CRUD + 发布链接 + 匿名提交建线索 + 防重复 + 频控）；`FormController`（管理端点 + 公开提交端点）；前端表单管理页（字段配置器 + 预览 + 外链 + 提交记录）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: MyBatis-Plus、LeadService（建线索）、012 数据权限

**Storage**: 2 表 + Flyway V51

**Testing**: JUnit 5 + Mockito（表单 CRUD/提交建线索/防重复/频控）、集成（FormIT）、前端（表单配置页渲染）

**Target Platform**: Web

**Project Type**: Web 应用

**Performance Goals**: 提交建线索 ≤ 200ms；公开端点频控（IP + 时间窗口）

**Constraints**: 公开提交无需登录（字段白名单 + 长度校验 + 防注入）；防重复（邮箱/手机已存在线索拦截）；来源映射

**Scale/Scope**: 2 表 + 1 迁移 + 1 Service + 1 Controller + 前端表单管理页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/online-forms.md） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（FormService 独立） |
| 原则三：数据完整性、安全与校验 | 公开端点安全 | ✅ 满足（字段白名单/长度/频控 + form:manage 权限） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（FormServiceTest/FormIT + 前端测试） |
| 原则五：简洁、可维护与可观测 | 避免过度设计 | ✅ 满足（无第三方表单平台） |

**结论**: 无门禁违规。

## Project Structure

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/Form.java / FormSubmission.java
├── repository/FormMapper.java / FormSubmissionMapper.java
├── dto/form/FormRequest.java / FormResponse.java / SubmitRequest.java
├── service/FormService.java         # 表单 CRUD + 发布 + 匿名提交（建线索 + 防重复 + 频控）
├── controller/FormController.java   # /api/v1/forms（管理）+ /api/v1/public/forms/{id}/submit（公开）
└── security/RequirePermission       # form:manage

backend/src/main/resources/db/migration/V51__online_forms.sql
backend/src/test/java/com/crm/
├── service/FormServiceTest.java
└── integration/FormIT.java

frontend/src/
├── types/form.ts / services/formService.ts
├── pages/marketing/OnlineFormPage.tsx  # 表单管理（字段配置器 + 预览 + 外链 + 提交记录）
└── App.tsx                             # 路由（营销分组）
```

**Structure Decision**: 表单字段 JSON：`[{ "field":"name", "label":"姓名", "required":true, "type":"TEXT|TEL|EMAIL|TEXTAREA" }, ...]`（映射 lead 内置字段 + 自定义字段留 payload）。提交：校验必填/长度 → 防重复（email 或 phone 已存在 lead 拦截）→ 建 lead（name/company/phone/email → source=表单来源）→ 存 form_submission 快照（含 IP）。频控：Redis/内存 IP 窗口（简化：同 IP 1 分钟最多 3 次，内存 ConcurrentHashMap）。

## Complexity Tracking

> 无违规，本表留空。
