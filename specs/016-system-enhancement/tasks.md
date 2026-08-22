# Tasks: 系统增强模块

**Input**: Design documents from `/specs/016-system-enhancement/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V38 in `backend/src/main/resources/db/migration/V38__custom_field.sql`（custom_field 表 + uk_field_entity_name/idx_field_entity_enabled）
- [x] T002 [P] 创建 Flyway 迁移 V39 in `backend/src/main/resources/db/migration/V39__custom_field_value.sql`（custom_field_value 表 + uk_field_entity_value/idx_value_entity）
- [x] T003 [P] 创建 Flyway 迁移 V40 in `backend/src/main/resources/db/migration/V40__export_job.sql`（export_job 表 + idx_export_user）
- [x] T004 [P] 创建 Flyway 迁移 V41 in `backend/src/main/resources/db/migration/V41__notification.sql`（notification 表 + 从 workflow_notification 迁移数据 + 索引）
- [x] T005 [P] H2 测试 schema 同步（custom_field/custom_field_value/export_job/notification 表 + 索引）in `backend/src/test/resources/schema-h2.sql`
- [x] T006 [P] ErrorCode 新增 CUSTOM_FIELD_*/NOTIFICATION_*/EXPORT_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [x] T007 [P] 创建 CustomField/CustomFieldValue/ExportJob/Notification 实体 + 4 个 Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 自定义字段 (P0) 🎯 MVP

**Goal**: 字段定义 CRUD + 实体值读写 + 列表筛选。

**Independent Test**: 配置字段→实体携带值→筛选命中。

### 实现

- [x] T008 [P] [US1] 创建 CustomFieldRequest/Response/CustomFieldValueRequest/CustomFieldValueResponse DTO in `backend/src/main/java/com/crm/dto/customfield/`
- [x] T009 [US1] 创建 CustomFieldService in `backend/src/main/java/com/crm/service/CustomFieldService.java`（定义 CRUD/名称唯一/类型-选项校验/删除清理值/按实体列定义）
- [x] T010 [US1] 创建 CustomFieldController in `backend/src/main/java/com/crm/controller/CustomFieldController.java`（/custom-fields CRUD；仅 ADMIN）
- [x] T011 [US1] Lead/Customer/Opportunity/Ticket 请求与响应接入 customFieldValues 读写（保存时 upsert、详情回显）in `backend/src/main/java/com/crm/service/`（LeadService/CustomerService/OpportunityService/TicketService + 对应 Request/Response DTO）
- [x] T012 [US1] 列表筛选支持 `cf_<fieldId>` 参数（文本 LIKE/下拉 EQ）in `backend/src/main/java/com/crm/service/`（LeadService/CustomerService/OpportunityService/TicketService）
- [x] T013 [US1] 创建 CustomFieldServiceTest 单元测试 in `backend/src/test/java/com/crm/service/CustomFieldServiceTest.java`（定义 CRUD/重复 409/类型校验/必填 422）
- [x] T014 [US1] 创建 SystemEnhancementIT 集成测试（字段部分）in `backend/src/test/java/com/crm/integration/SystemEnhancementIT.java`
- [x] T015 [US1] 前端类型/服务 in `frontend/src/types/customField.ts` + `frontend/src/services/customFieldService.ts`
- [x] T016 [US1] 自定义字段配置页 in `frontend/src/pages/settings/CustomFieldListPage.tsx`（按实体 CRUD；仅 ADMIN 菜单）
- [x] T017 [US1] 线索/客户/商机/工单创建弹窗与详情接入自定义字段 in `frontend/src/pages/`（LeadListPage/CustomerListPage/OpportunityListPage/TicketListPage + 详情页）

**Checkpoint**: US1 可用——自定义字段

---

## Phase 3: 用户故事 2 - 通知中心 (P1)

**Goal**: 通知列表/已读/未读计数 + 工单通知接入。

**Independent Test**: 分配工单→通知出现→标记已读→角标更新。

### 实现

- [x] T018 [P] [US2] 创建 NotificationResponse/NotificationType DTO in `backend/src/main/java/com/crm/dto/notification/`
- [x] T019 [US2] 创建 NotificationService in `backend/src/main/java/com/crm/service/NotificationService.java`（列表未读优先/单条已读/全部已读/未读计数/写入通知/保留 100 条清理）
- [x] T020 [US2] 创建 NotificationController in `backend/src/main/java/com/crm/controller/NotificationController.java`（/notifications 列表/unread-count/read/read-all；仅本人）
- [x] T021 [US2] TicketService 分配/回复时写入通知（TICKET_ASSIGN/TICKET_REPLY，本人除外）in `backend/src/main/java/com/crm/service/TicketService.java`
- [x] T022 [US2] 删除/替换 WorkflowNotification 引用到 Notification in `backend/src/main/java/com/crm/service/`（WorkflowEngine 写入点改造）
- [x] T023 [US2] 创建 NotificationServiceTest 单元测试 in `backend/src/test/java/com/crm/service/NotificationServiceTest.java`（列表/已读/未读计数/清理）
- [x] T024 [US2] SystemEnhancementIT 增加通知用例 in `backend/src/test/java/com/crm/integration/SystemEnhancementIT.java`
- [x] T025 [US2] 前端类型/服务 in `frontend/src/types/notification.ts` + `frontend/src/services/notificationService.ts`
- [x] T026 [US2] 顶栏通知角标 + 通知抽屉（列表/已读/全部已读）in `frontend/src/components/NotificationCenter.tsx` + `frontend/src/App.tsx`

**Checkpoint**: US2 可用——通知中心

---

## Phase 4: 用户故事 3 - 数据导出 (P1)

**Goal**: 导出任务异步生成 Excel + 历史与下载。

**Independent Test**: 发起导出→任务 DONE→下载行数一致。

### 实现

- [x] T027 [P] [US3] 创建 ExportRequest/ExportJobResponse DTO in `backend/src/main/java/com/crm/dto/export/`
- [x] T028 [US3] 创建 ExportJobService in `backend/src/main/java/com/crm/service/ExportJobService.java`（创建任务/状态查询/下载鉴权/保留 50 条清理/线程池执行）
- [x] T029 [US3] 创建 ExportExecutor in `backend/src/main/java/com/crm/service/ExportExecutor.java`（POI 生成 xlsx，按类型查询数据 + 自定义字段列）
- [x] T030 [US3] 创建 ExportController in `backend/src/main/java/com/crm/controller/ExportController.java`（POST /exports、GET 列表、GET /{id}/download）
- [x] T031 [US3] 创建 ExportJobServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ExportJobServiceTest.java`（创建/状态/下载鉴权/清理）
- [x] T032 [US3] SystemEnhancementIT 增加导出用例 in `backend/src/test/java/com/crm/integration/SystemEnhancementIT.java`
- [x] T033 [US3] 前端类型/服务 in `frontend/src/types/export.ts` + `frontend/src/services/exportService.ts`
- [x] T034 [US3] 导出中心页 in `frontend/src/pages/exports/ExportCenterPage.tsx`（发起导出/历史/下载）+ 各列表页导出按钮

**Checkpoint**: US3 可用——数据导出

---

## Phase 5: 收尾与验证

- [x] T035 前端路由与菜单 in `frontend/src/App.tsx`（设置-自定义字段仅 ADMIN、导出中心、通知角标）
- [x] T036 移动端响应式优化（窄屏表单/列表布局）in `frontend/src/App.tsx` + `frontend/src/`（antd responsive）
- [x] T037 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T038 单独运行 `mvn test "-Dtest=SystemEnhancementIT"` 通过
- [x] T039 Frontend typecheck / lint / test / build 通过
- [x] T040 线上端点验证（字段配置→值读写→筛选→通知→导出下载→权限 403）
- [x] T041 更新契约文档（按实现校正）与 roadmap 016 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖，可并行
- **Phase 2 (US1)**: 依赖 Phase 1；值读写需接入 4 个既有 Service
- **Phase 3 (US2)**: 依赖 Phase 1；通知写入需改造 TicketService/WorkflowEngine
- **Phase 4 (US3)**: 依赖 Phase 1；导出含自定义字段列（依赖 US1 的字段定义）
- **Phase 5**: 依赖全部用户故事完成

### Parallel Opportunities

- Phase 1 的 T001~T007 全部 [P] 可并行
- US1 字段定义服务与 US2/US3 独立实体任务可并行
- 同一故事内 DTO/测试（[P]）可并行

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 数据模型
2. 完成 Phase 2: US1 自定义字段（定义 CRUD + 值读写 + 筛选）
3. **STOP and VALIDATE**: 字段配置/填写/筛选可独立使用
4. 继续 US2 通知中心、US3 数据导出

### 增量交付

1. Phase 1 → 数据模型就绪
2. US1 自定义字段 → 独立验证（MVP）
3. US2 通知中心 → 独立验证
4. US3 数据导出 → 独立验证
5. Phase 5 收尾：移动端响应式/路由/菜单/全量验证/文档

## Notes

- 自定义字段值字符串存储；必填校验在 Service 层按字段定义执行
- 通知统一表迁移 013 workflow_notification 数据（V41 含 INSERT SELECT）
- 导出任务同 JVM 线程池异步执行；文件落盘 backend/contract-files/exports/
- 提交规范：每个逻辑组提交一次（Conventional Commits）
