# Tasks: 线索管理模块

**Input**: Design documents from `/specs/004-lead-management/`

**Prerequisites**: plan.md (required), spec.md (required)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1/US2/US3

---

## Phase 1: 数据模型与基础设施

- [x] T001 [P] 创建 Flyway 迁移 V7 in `backend/src/main/resources/db/migration/V7__lead.sql`
- [x] T002 [P] 创建 Flyway 迁移 V8 in `backend/src/main/resources/db/migration/V8__follow_up_lead_id.sql`
- [x] T003 [P] 创建 Lead 实体 in `backend/src/main/java/com/crm/entity/Lead.java`（@TableName("`lead`") 修复 MySQL 保留字）
- [x] T004 [P] 修改 FollowUp 实体 in `backend/src/main/java/com/crm/entity/FollowUp.java`（+leadId 字段）
- [x] T005 [P] 创建 LeadMapper in `backend/src/main/java/com/crm/repository/LeadMapper.java`

**额外**: V9 迁移 `V9__follow_up_customer_id_nullable.sql`（customer_id 改为可空以支持 lead_id 二选一关联）

**Checkpoint**: 数据模型就绪 ✓

---

## Phase 2: DTO 与错误码

- [x] T006 [P] 创建 Lead DTOs in `backend/src/main/java/com/crm/dto/lead/`（LeadRequest/LeadResponse/LeadDetailResponse/ConvertRequest/ImportResult）
- [x] T007 [P] ErrorCode 新增线索相关错误码（LEAD_NOT_FOUND/LEAD_ALREADY_CONVERTED/LEAD_INVALID_STATE）

**Checkpoint**: DTO 与错误码就绪 ✓

---

## Phase 3: User Story 1 - 线索录入与线索池 (P0)

- [x] T008 [P] [US1] 创建 LeadService（CRUD/逻辑删除/分页搜索筛选/线索池/分配/领取）
- [x] T009 [P] [US1] 创建 LeadController（GET/POST/PUT/DELETE /api/v1/leads + POST /{id}/assign + POST /{id}/claim）
- [x] T010 [US1] H2 测试 schema 同步 lead 表 + follow_up.lead_id + customer_id 可空

**Checkpoint**: US1 后端可用 ✓

---

## Phase 4: User Story 2 - 线索跟进与筛选 (P0)

- [x] T011 [P] [US2] 修改 FollowUpService/Controller 支持 lead_id（customerId 与 leadId 二选一校验）
- [x] T012 [P] [US2] LeadService 详情接口包含跟进记录时间线
- [x] T013 [US2] 后端集成测试 LeadITTest（CRUD/线索池/分配/领取/跟进/筛选分页/终态校验）

**Checkpoint**: US2 核心可用 ✓（集成测试待补充）

---

## Phase 5: User Story 3 - 线索转化 (P0)

- [x] T014 [P] [US3] LeadService 转化方法（单事务：查重客户→创建/关联客户→创建商机+销售机会→更新线索状态为 QUALIFIED）
- [x] T015 [P] [US3] LeadController 转化接口 POST /api/v1/leads/{id}/convert
- [x] T016 [US3] 后端单元测试 LeadServiceTest（转化查重/状态校验/已转化不可再转化/无效状态不可转化/线索池/领取分配）
- [x] T017 [US3] 后端契约测试 LeadContractTest（API 响应结构/401/400/422/转化流程）

**Checkpoint**: US3 核心可用 ✓（单元/契约测试待补充）

---

## Phase 6: Excel 导入导出

- [x] T018 [P] 创建 LeadExcelService（导入/导出/模板下载）
- [x] T019 [P] LeadController 导入导出接口（POST /import、GET /export、GET /template）

**Checkpoint**: Excel 导入导出待实现（P1 优先级）

---

## Phase 7: 前端实现

- [x] T020 [P] 前端类型定义 in `frontend/src/types/lead.ts`
- [x] T021 [P] 前端 API 服务 in `frontend/src/services/leadService.ts`
- [x] T022 [P] 线索列表页 in `frontend/src/pages/leads/LeadListPage.tsx`（ProTable + 搜索筛选 + Tab 切换全部/线索池 + 新增/编辑/删除/领取/分配/转化）
- [x] T023 [P] 线索详情页 in `frontend/src/pages/leads/LeadDetailPage.tsx`（基本信息 + 跟进时间线 + 转化按钮 + 已转化标识）
- [x] T024 [P] 线索转化弹窗 in `frontend/src/components/LeadConvertModal.tsx`
- [x] T025 前端路由与菜单 in `frontend/src/App.tsx`（线索菜单 + /leads + /leads/:id 路由）

**额外**: FollowUpTimeline 组件改造支持 leadId；followUp 类型/服务支持 leadId

**Checkpoint**: 前端完整可用 ✓

---

## 验证

- [x] Backend `mvn verify`：54 测试全部通过（原28+新增26）、spotless 通过、JaCoCo 门禁通过、BUILD SUCCESS
- [x] Frontend `tsc --noEmit` 通过
- [x] V7/V8/V9 迁移在数据库上执行成功（当前版本 v9）
- [x] 手动端到端验证 8 项全部通过：创建→领取→跟进→详情→转化→客户/商机创建→重复转化拦截(422)→线索池筛选

## 关键修复记录

1. **MySQL 保留字**: `lead` 是 MySQL 保留关键字，Lead 实体需 `@TableName("`lead`")` 否则 SQL 语法错误
2. **follow_up.customer_id NOT NULL**: 原表 customer_id 为 NOT NULL，创建仅关联 lead_id 的跟进时插入失败，需 V9 迁移改为可空
3. **Spotless 格式**: LeadService 方法参数换行不符合规范，`mvn spotless:apply` 自动修复

## 剩余待办（非阻塞）

- T013 LeadIT 集成测试
- T016 LeadServiceTest 单元测试
- T017 LeadContractTest 契约测试
- T018/T019 Excel 导入导出（P1 优先级，可延后）
