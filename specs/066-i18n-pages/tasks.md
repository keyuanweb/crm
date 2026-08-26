# Tasks: 更多页面国际化模块

**Input**: Design documents from `/specs/066-i18n-pages/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel

---

## Phase 1: 公共文案与框架（US1，P0）

**Goal**: 提取公共按钮/状态/消息到 common.json，注册命名空间。

### 实现

- [ ] T001 [P] 创建 `frontend/src/i18n/resources/common.json`（按钮/状态/消息/占位符）
- [ ] T002 [P] zh-CN.ts / en.ts 注册新命名空间
- [ ] T003 [P] CustomerListPage 列标题/按钮/消息 → t()

**Checkpoint**: US1 可用——公共文案 + 客户列表

---

## Phase 2: 核心业务页面（US2，P0）

**Goal**: 覆盖产品/商机/合同/工单/线索/联系人页面。

### 实现

- [ ] T004 [P] product.json + ProductListPage 迁移
- [ ] T005 [P] opportunity.json + OpportunityListPage 迁移
- [ ] T006 [P] contract.json + ContractListPage 迁移
- [ ] T007 [P] ticket.json + TicketListPage 迁移
- [ ] T008 [P] lead.json + LeadListPage 迁移
- [ ] T009 [P] contact.json + ContactListPage 迁移

**Checkpoint**: US2 可用——7+ 核心页面覆盖

---

## Phase 3: 详情页与表单（US3，P1）

**Goal**: 客户/产品/商机/合同详情页迁移。

### 实现

- [ ] T010 [P] customer-detail.json + CustomerDetailPage 迁移
- [ ] T011 [P] product-detail.json + ProductDetailPage 迁移
- [ ] T012 [P] opportunity-detail.json + OpportunityDetailPage 迁移
- [ ] T013 [P] contract-detail.json + ContractDetailPage 迁移

**Checkpoint**: US3 可用——详情页覆盖

---

## Phase 4: 验证与收尾

- [ ] T014 Frontend typecheck/lint/build 通过
- [ ] T015 手动语言切换验证（抽查每页 10 处文案无中文残留）
- [ ] T016 更新 roadmap 066 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 无依赖
- **Phase 2**: 依赖 Phase 1
- **Phase 3**: 依赖 Phase 1
- **Phase 4**: 依赖全部完成

## Notes

- 金额格式化不翻译（浏览器 locale 自动处理）
- 错误码保持英文 code，message 由后端返回
- 提交规范：Conventional Commits
