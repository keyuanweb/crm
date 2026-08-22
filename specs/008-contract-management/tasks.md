# Tasks: 合同管理模块

**Input**: Design documents from `/specs/008-contract-management/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3/US4

---

## Phase 1: 数据模型与基础

- [x] T001 [P] 创建 Flyway 迁移 V16 in `backend/src/main/resources/db/migration/V16__contract.sql`（contract 表 + active_key 编号唯一 + 索引）
- [x] T002 [P] 创建 Flyway 迁移 V17 in `backend/src/main/resources/db/migration/V17__contract_attachment.sql`（contract_attachment 表）
- [x] T003 [P] 创建 Flyway 迁移 V18 in `backend/src/main/resources/db/migration/V18__contract_template.sql`（contract_template 表）
- [x] T004 [P] 创建 Contract 实体/Mapper in `backend/src/main/java/com/crm/entity/Contract.java` + `backend/src/main/java/com/crm/repository/ContractMapper.java`
- [x] T005 [P] 创建 ContractAttachment 实体/Mapper in `backend/src/main/java/com/crm/entity/ContractAttachment.java` + `backend/src/main/java/com/crm/repository/ContractAttachmentMapper.java`
- [x] T006 [P] 创建 ContractTemplate 实体/Mapper in `backend/src/main/java/com/crm/entity/ContractTemplate.java` + `backend/src/main/java/com/crm/repository/ContractTemplateMapper.java`
- [x] T007 [P] H2 测试 schema 同步三表 in `backend/src/test/resources/schema-h2.sql`
- [x] T008 [P] ErrorCode 新增 CONTRACT_NOT_FOUND/CONTRACT_INVALID_STATE/QUOTE_NOT_APPROVED/ATTACHMENT_NOT_FOUND/ATTACHMENT_INVALID/TEMPLATE_NOT_FOUND in `backend/src/main/java/com/crm/common/ErrorCode.java`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 合同创建与编辑 (P0) 🎯 MVP

**Goal**: 合同创建（可直接/基于已通过报价）+ 草稿编辑 + 提交审批。

**Independent Test**: 基于报价创建合同→金额自动带入→编辑草稿→提交。

### 实现

- [x] T009 [P] [US1] 创建 ContractRequest/ContractResponse DTO in `backend/src/main/java/com/crm/dto/contract/`
- [x] T010 [US1] 创建 ContractService in `backend/src/main/java/com/crm/service/ContractService.java`（创建/编辑/列表/详情/编号生成/基于报价校验/日期校验）
- [x] T011 [US1] 创建 ContractController in `backend/src/main/java/com/crm/controller/ContractController.java`（GET 列表/详情、POST/PUT、submit；类级 ADMIN+SALES）
- [x] T012 [US1] 创建 ContractServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ContractServiceTest.java`（创建/算额/报价未通过 400/日期校验/编号生成）
- [x] T013 [US1] 创建 ContractIT 集成测试 in `backend/src/test/java/com/crm/integration/ContractIT.java`（创建→编辑→提交流程）
- [x] T014 [US1] 前端类型与服务 in `frontend/src/types/contract.ts` + `frontend/src/services/contractService.ts`
- [x] T015 [US1] 合同列表页 in `frontend/src/pages/contracts/ContractListPage.tsx`（ProTable + 创建弹窗含报价/模板选择）
- [x] T016 [US1] 合同详情页 in `frontend/src/pages/contracts/ContractDetailPage.tsx`（基本信息 + 正文 + 状态操作按钮）

**Checkpoint**: US1 可用——合同创建/编辑/提交完整

---

## Phase 3: 用户故事 2 - 合同审批与生效 (P0)

**Goal**: 管理员审批（通过/拒绝）+ 生效/完成/终止流转。

**Independent Test**: 提交→审批通过→生效→完成/终止；拒绝重提。

### 实现

- [x] T017 [US2] ContractService 增加 approve/reject/effective/complete/terminate（状态机校验/记录时间与原因）in `backend/src/main/java/com/crm/service/ContractService.java`
- [x] T018 [US2] ContractController 增加 approve/reject（仅 ADMIN）/effective/complete/terminate 端点 in `backend/src/main/java/com/crm/controller/ContractController.java`
- [x] T019 [US2] ContractServiceTest 增加状态机用例 in `backend/src/test/java/com/crm/service/ContractServiceTest.java`
- [x] T020 [US2] ContractIT 增加审批/生效/终止流程用例（通过/拒绝/重复审批 409/非 ADMIN 403）in `backend/src/test/java/com/crm/integration/ContractIT.java`
- [x] T021 [US2] 前端详情页增加审批/生效/完成/终止操作（按状态与角色显示）in `frontend/src/pages/contracts/ContractDetailPage.tsx`

**Checkpoint**: US2 可用——审批与履约闭环

---

## Phase 4: 用户故事 3 - 合同附件 (P1)

**Goal**: 附件上传/列表/下载/删除（类型白名单 + 20MB）。

**Independent Test**: 上传→列表→下载一致→删除。

### 实现

- [x] T022 [US3] 创建 ContractAttachmentService in `backend/src/main/java/com/crm/service/ContractAttachmentService.java`（上传校验/落盘/下载防路径穿越/物理删除）
- [x] T023 [US3] 创建 ContractAttachmentController in `backend/src/main/java/com/crm/controller/ContractAttachmentController.java`（POST 上传/GET 下载/DELETE）
- [x] T024 [US3] 配置附件存储目录 in `backend/src/main/resources/application.yml`（crm.contract.storage-dir，默认 ./contract-files）
- [x] T025 [US3] ContractIT 增加附件用例（上传 201/类型 400/下载一致/删除）in `backend/src/test/java/com/crm/integration/ContractIT.java`
- [x] T026 [US3] 前端详情页增加附件卡片（上传/下载/删除）in `frontend/src/pages/contracts/ContractDetailPage.tsx`

**Checkpoint**: US3 可用——附件闭环

---

## Phase 5: 用户故事 4 - 合同模板 (P2)

**Goal**: 模板 CRUD + 占位符替换生成正文。

**Independent Test**: 建模板→基于模板建合同→占位符替换正确。

### 实现

- [x] T027 [US4] 创建 ContractTemplate DTO in `backend/src/main/java/com/crm/dto/contract/`（ContractTemplateRequest/ContractTemplateResponse）
- [x] T028 [US4] 创建 ContractTemplateService in `backend/src/main/java/com/crm/service/ContractTemplateService.java`（CRUD/占位符替换 {customerName}/{contractNo}/{amount}）
- [x] T029 [US4] 创建 ContractTemplateController in `backend/src/main/java/com/crm/controller/ContractTemplateController.java`（GET 列表/POST/PUT/DELETE，写仅 ADMIN）
- [x] T030 [US4] 创建 ContractTemplateServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ContractTemplateServiceTest.java`（占位符替换/停用后不可选）
- [x] T031 [US4] 前端模板管理页 in `frontend/src/pages/contract-templates/ContractTemplateListPage.tsx`（ProTable + 弹窗）
- [x] T032 [US4] 前端类型/服务扩展 in `frontend/src/types/contract.ts` + `frontend/src/services/contractService.ts`（模板接口）

**Checkpoint**: US4 可用——模板闭环

---

## Phase 6: 收尾与验证

- [x] T033 前端路由与菜单 in `frontend/src/App.tsx`（合同/合同模板菜单 + 路由）
- [x] T034 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [x] T035 单独运行 `mvn test "-Dtest=ContractIT"` 通过
- [x] T036 Frontend typecheck / lint / test / build 通过
- [x] T037 线上端点验证（模板→合同创建→提交→审批→生效→完成→附件上传下载→权限 403）
- [x] T038 更新契约文档（按实现校正）与 roadmap 008 标记 `[x]`

**Checkpoint**: 模块完整可用
