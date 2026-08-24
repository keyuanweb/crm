# Tasks: 合同续约管理模块

**Input**: Design documents from `/specs/046-contract-renewal/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 数据模型与基础

- [ ] T001 [P] 创建 Flyway 迁移 V57 in `backend/src/main/resources/db/migration/V57__contract_renewal.sql`（contract.renewed_from_id + 索引）
- [ ] T002 [P] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`
- [ ] T003 [P] Contract 实体新增 renewedFromId in `backend/src/main/java/com/crm/entity/Contract.java`
- [ ] T004 [P] ErrorCode 新增 CONTRACT_RENEWAL_GROUP_INVALID in `backend/src/main/java/com/crm/common/ErrorCode.java`
- [ ] T005 [P] ContractRequest/Response 新增 renewedFromId/renewedFromNo/renewedBy in `backend/src/main/java/com/crm/dto/contract/`

**Checkpoint**: 数据模型就绪

---

## Phase 2: 用户故事 1 - 合同到期提醒 (P0)

**Goal**: 续约漏斗视图（即将到期/已到期未续）。

**Independent Test**: 生效合同按到期归类正确。

### 实现

- [ ] T006 [P] [US1] 创建 ContractRenewalResponse DTO in `backend/src/main/java/com/crm/dto/contract/`
- [ ] T007 [US1] 创建 ContractRenewalService（分组查询/搜索/分页/daysToExpire/去向装配）in `backend/src/main/java/com/crm/service/ContractRenewalService.java`
- [ ] T008 [US1] 创建 ContractRenewalController（/contracts/renewal-overview，ADMIN+SALES）in `backend/src/main/java/com/crm/controller/ContractRenewalController.java`
- [ ] T009 [US1] 创建 ContractRenewalServiceTest 单元测试 in `backend/src/test/java/com/crm/service/ContractRenewalServiceTest.java`

**Checkpoint**: US1 可用——到期提醒

---

## Phase 3: 用户故事 2 - 续约关系与漏斗 (P0)

**Goal**: 合同创建选续约来源 + 详情来源/去向 + 已续约分组。

**Independent Test**: 续约创建 → 来源/去向展示 → 漏斗归类。

### 实现

- [ ] T010 [US2] ContractService.create 应用 renewedFromId（校验来源存在）in `backend/src/main/java/com/crm/service/ContractService.java`
- [ ] T011 [US2] ContractService 详情装配 renewedFromNo/renewedBy in `backend/src/main/java/com/crm/service/ContractService.java`
- [ ] T012 [US2] ContractRenewalService 增加 RENEWED 分组（续约去向）in `backend/src/main/java/com/crm/service/ContractRenewalService.java`
- [ ] T013 [US2] 创建 ContractRenewalIT 集成测试 in `backend/src/test/java/com/crm/integration/ContractRenewalIT.java`
- [ ] T014 [US2] 前端 contractService 扩展续约接口 in `frontend/src/services/contractService.ts`
- [ ] T015 [US2] 续约漏斗页 in `frontend/src/pages/contracts/ContractRenewalPage.tsx`（分组切换/搜索/跳转）
- [ ] T016 [US2] 合同创建弹窗增加"续约自"选择；详情展示来源/去向 in `frontend/src/pages/contracts/`

**Checkpoint**: US2 可用——续约链与漏斗

---

## Phase 4: 收尾与验证

- [ ] T017 App.tsx 点亮"续约管理"占位项为路由 in `frontend/src/App.tsx`（/contract-renewal）
- [ ] T018 Backend `mvn verify` 通过（含新增测试、spotless、JaCoCo）
- [ ] T019 单独运行 `mvn test "-Dtest=ContractRenewalIT"` 通过
- [ ] T020 Frontend typecheck / lint / test / build 通过
- [ ] T021 线上端点验证（到期归类→续约创建→漏斗→详情跳转）
- [ ] T022 更新契约文档（按实现校正）与 roadmap 046 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2 (US1)**: 依赖 Phase 1
- **Phase 3 (US2)**: 依赖 Phase 1/2（RENEWED 分组依赖续约字段）
- **Phase 4**: 依赖全部完成

### Parallel Opportunities

- Phase 1 的 T001~T005 全部 [P]
- US1 视图服务与合同 CRUD 扩展可并行

## Notes

- 续约阈值为 90 天常量
- 复用合同既有 CRUD，最小侵入
- 提交规范：Conventional Commits
