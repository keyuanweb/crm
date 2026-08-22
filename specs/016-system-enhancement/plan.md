# Implementation Plan: 系统增强模块

**Branch**: `016-system-enhancement` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

## Summary

四项系统能力：1）自定义字段（CustomField 定义 + CustomFieldValue 值，适用 LEAD/CUSTOMER/OPPORTUNITY/TICKET，类型 TEXT/TEXTAREA/NUMBER/DATE/SELECT，ADMIN 配置，业务填写，列表筛选）；2）通知中心（复用 013 WorkflowNotification 迁移为统一 notification 表，新增工单通知类型，已读/未读/全部已读/未读计数角标）；3）数据导出（ExportJob 后台任务，Excel 导出线索/客户/商机/工单，含自定义字段列，导出中心历史与下载）；4）移动端适配（响应式布局优化）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: MyBatis-Plus、Spring Validation、Apache POI（既有）、AuditService、Security

**Storage**: MySQL 新增 `custom_field`/`custom_field_value`/`export_job` 三张表 + 通知表迁移（Flyway V38~V41）

**Testing**: JUnit 5 + Spring Boot Test（CustomFieldServiceTest/NotificationServiceTest/ExportJobServiceTest 单元、SystemEnhancementIT 集成）

**Target Platform**: Web（自定义字段配置页、通知中心抽屉/页、导出中心、移动端响应式）

**Project Type**: 现有 Web 应用新增模块

**Performance Goals**: 自定义字段配置 1s 内生效（SC-S01）；导出 5000 行 ≤30s（SC-S05）

**Constraints**: 字段类型枚举固定；SELECT 必有选项其余必空；字段名实体内唯一；字段删除物理清理关联值；通知保留最近 100 条/用户；导出记录保留 50 条/用户；通知仅本人；导出按实体既有读权限

**Scale/Scope**: 自定义字段 ≤ 数十/实体；通知 ≤ 数千；导出任务 ≤ 数百

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | Controller→Service→持久层 | ✅ 满足（复用既有分层） |
| 原则三：数据完整性、安全与校验 | DTO 校验、服务端授权 | ✅ 满足（Bean Validation + @PreAuthorize） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | 无 N+1、审计、导出异步 | ✅ 满足（批量装配 + AuditService + 线程池） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/016-system-enhancement/
├── spec.md / plan.md / tasks.md / research.md / data-model.md / quickstart.md
├── contracts/custom-fields.md + notifications.md + exports.md

backend/src/main/java/com/crm/
├── entity/CustomField.java + CustomFieldValue.java + ExportJob.java
├── entity/Notification.java（替代 WorkflowNotification）
├── repository/（对应 3 个新 Mapper + NotificationMapper）
├── dto/customfield/（CustomFieldRequest/Response/CustomFieldValueRequest）
├── dto/notification/（NotificationResponse/MarkReadRequest）
├── dto/export/（ExportRequest/ExportJobResponse）
├── service/CustomFieldService.java（定义 CRUD + 值读写 + 筛选）
├── service/NotificationService.java（列表/已读/未读计数/聚合）
├── service/ExportJobService.java（任务创建/状态/下载 + 导出执行器）
├── service/ExportExecutor.java（POI 生成 Excel，含自定义字段列）
├── controller/CustomFieldController.java + NotificationController.java + ExportController.java
├── common/ErrorCode.java（新增 CUSTOM_FIELD_*/NOTIFICATION_*/EXPORT_* 错误码）
└── resources/db/migration/V38__custom_field.sql + V39__custom_field_value.sql + V40__export_job.sql + V41__notification.sql（含 013 数据迁移）

backend/src/test/java/com/crm/
├── service/CustomFieldServiceTest.java + NotificationServiceTest.java + ExportJobServiceTest.java
├── integration/SystemEnhancementIT.java

frontend/src/
├── types/customField.ts + services/customFieldService.ts + types/notification.ts
├── services/notificationService.ts + types/export.ts + services/exportService.ts
├── pages/settings/CustomFieldListPage.tsx（字段配置）
├── components/NotificationCenter.tsx（顶栏角标 + 抽屉列表）
├── pages/exports/ExportCenterPage.tsx（导出历史 + 下载）
├── 实体详情页/创建弹窗接入自定义字段（lead/customer/opportunity/ticket）
├── 移动端响应式优化（表单/列表窄屏布局）
└── App.tsx（设置菜单 + 通知角标）
```

**Structure Decision**: 沿用既有前后端目录结构（Option 2 Web 应用）。自定义字段值为字符串存储（实体表不加列，避免迁移风暴）；通知统一表迁移 013 数据；导出任务同 JVM 线程池异步执行。

## Complexity Tracking

无违规，本表留空。
