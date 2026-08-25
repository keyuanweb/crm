# Tasks: 权限体系加固模块

**Input**: Design documents from `/specs/063-security-hardening/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 行级数据权限（US1，P0）

**Goal**: Lead/Contact/FollowUp/Comment 服务层行级过滤与写校验。

**Independent Test**: SALES 看不到他人线索；越权更新 403。

### 实现

- [x] T001 [P] LeadService：page 加 owner 可见集过滤（resolveVisibleOwnerIds IN）；detail/update/delete/assign/convert 加 checkWritePermission
- [x] T002 [P] ContactService：按所属客户可见性过滤（客户 owner ∈ 可见集 或 共享）；写操作校验
- [x] T003 [P] FollowUpService/CommentService：按关联实体可见性过滤 + 写校验
- [x] T004 [P] Lead/Contact/FollowUp/Comment/CustomerShare Controller 补 @PreAuthorize（ADMIN/SALES/SUPPORT）
- [x] T005 [P] LeadServiceTest/ContactServiceTest 扩展越权场景单测（≥4）

**Checkpoint**: US1 可用——行级权限覆盖

---

## Phase 2: 导出安全（US2，P0）

**Goal**: 导出过滤 + 脱敏。

**Independent Test**: SALES 导出仅含本人数据 + 手机号脱敏。

### 实现

- [x] T006 [P] ExportExecutor：各导出类型接 resolveVisibleOwnerIds 过滤（非 ADMIN）
- [x] T007 [P] ExportExecutor：非 ADMIN 导出手机/邮箱 MaskingUtil 脱敏

**Checkpoint**: US2 可用——导出安全

---

## Phase 3: 异常加固（US3，P1）

**Goal**: 400/404/409 正确语义。

**Independent Test**: 坏 JSON→400；唯一冲突→409；路径不存在→404。

### 实现

- [x] T008 [P] GlobalExceptionHandler 补 HttpMessageNotReadable/MethodArgumentTypeMismatch/MissingParam → 400；NoResourceFound → 404；DuplicateKey/DataIntegrity → 409
- [x] T009 [P] IllegalArgumentException 不再回传内部 message（统一 500 文案）

**Checkpoint**: US3 可用——异常语义

---

## Phase 4: 验证与收尾

- [x] T010 创建 SecurityHardeningIT（越权/导出端到端）in `backend/src/test/java/com/crm/integration/SecurityHardeningIT.java`
- [x] T011 创建 ExceptionHandlerIT（400/404/409）in `backend/src/test/java/com/crm/integration/ExceptionHandlerIT.java`
- [x] T012 Backend `mvn verify` 通过（含既有 135+ 测试无回归、spotless、JaCoCo）
- [x] T013 线上端点验证（越权 403/导出过滤脱敏/400-404-409）
- [x] T014 更新契约文档（按实现校正）与 roadmap 063 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 依赖 012 DataPermissionService
- **Phase 2**: 依赖 012 + MaskingUtil
- **Phase 3**: 无依赖
- **Phase 4**: 依赖全部完成

## Notes

- 复用现有组件（DataPermissionService/CustomerShare/MaskingUtil），无迁移
- 行级过滤 SQL IN（不内存过滤）
- 提交规范：Conventional Commits
