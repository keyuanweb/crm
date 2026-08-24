# Tasks: 电子签署模块

**Input**: Design documents from `/specs/047-e-signature/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V58 in `backend/src/main/resources/db/migration/V58__signature_record.sql`（signature_record 表 + uk_signature_business 唯一）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] 创建 SignatureRecord 实体 + Mapper in `backend/src/main/java/com/crm/entity/` + `backend/src/main/java/com/crm/repository/`
- [ ] T004 [P] ErrorCode 新增 SIGNATURE_* 错误码 in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] 创建 SignRequest/SignatureRecordResponse DTO in `backend/src/main/java/com/crm/dto/signature/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 报价单电子签署 (P0)

**Goal**: 报价 APPROVED→SIGNED，签署记录留存。

**Independent Test**: 报价审批→签署→SIGNED→记录可查。

### 实现

- [ ] T006 [US1] QuoteService 增加 SIGNED 状态与签署后不可编辑 in `backend/src/main/java/com/crm/service/QuoteService.java`
- [ ] T007 [US1] 创建 SignatureService（签署/记录查询/校验）in `backend/src/main/java/com/crm/service/SignatureService.java`
- [ ] T008 [US1] SignatureController 报价签署端点（/quotes/{id}/sign）in `backend/src/main/java/com/crm/controller/SignatureController.java`
- [ ] T009 [US1] 创建 SignatureServiceTest 单元测试 in `backend/src/test/java/com/crm/service/SignatureServiceTest.java`

**Checkpoint**: US1 可用——报价签署

---

## Phase 3: 用户故事 2 - 合同电子签署 (P0)

**Goal**: 合同 APPROVED→SIGNED→EFFECTIVE，未签署不可生效。

**Independent Test**: 合同审批→签署→SIGNED→生效；未签生效 422。

### 实现

- [ ] T010 [US2] ContractService 增加 SIGNED 状态、生效前置校验（未签署 422）、签署后不可编辑 in `backend/src/main/java/com/crm/service/ContractService.java`
- [ ] T011 [US2] SignatureService 支持 CONTRACT 类型 in `backend/src/main/java/com/crm/service/SignatureService.java`
- [ ] T012 [US2] SignatureController 合同签署端点（/contracts/{id}/sign）in `backend/src/main/java/com/crm/controller/SignatureController.java`
- [ ] T013 [US2] 创建 ESignatureIT 集成测试 in `backend/src/test/java/com/crm/integration/ESignatureIT.java`
- [ ] T014 [US2] 前端签名组件（canvas 手绘 + 上传）in `frontend/src/components/SignaturePad.tsx`
- [ ] T015 [US2] 报价/合同详情接入签署区块与记录展示 in `frontend/src/pages/quotes/QuoteDetailPage.tsx` + `frontend/src/pages/contracts/ContractDetailPage.tsx`
- [ ] T016 [US2] 前端签名类型/服务 in `frontend/src/types/signature.ts` + `frontend/src/services/signatureService.ts`

**Checkpoint**: US2 可用——合同签署

---

## Phase 4: 收尾与验证

- [ ] T017 App.tsx 点亮"电子签署"占位项为路由 in `frontend/src/App.tsx`
- [ ] T018 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T019 单独运行 `mvn test "-Dtest=ESignatureIT"` 通过
- [ ] T020 Frontend typecheck / lint / test / build 通过
- [ ] T021 线上端点验证（报价签署→重复 409→合同签署→生效→未签拦截）
- [ ] T022 更新契约文档（按实现校正）与 roadmap 047 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1；合同生效校验需改 ContractService
- **Phase 4**: 依赖全部完成

### Parallel Opportunities

- Phase 1 的 T001~T005 全部 [P]
- US1 报价签署与 US2 合同签署共享 SignatureService，可并行扩展

## Notes

- 签名图 base64 存文本列（MEDIUMTEXT）
- 不接第三方电子签名服务（v1 内部代签）
- 提交规范：Conventional Commits
