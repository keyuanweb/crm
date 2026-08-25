# Tasks: 性能与数据完整性模块

**Input**: Design documents from `/specs/064-performance-integrity/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2/US3

---

## Phase 1: 流失预警性能（US1，P0）

**Goal**: 批量聚合 + 分页，消除 N+1。

**Independent Test**: 批量聚合调用次数固定；分页正确。

### 实现

- [x] T001 [P] Customer360Service 增加 healthScoresBatch(customerIds)（返回 Map<Long,Integer>）
- [x] T002 [P] CustomerService.atRiskCustomers 改为分页候选 + 批量取分过滤
- [x] T003 [P] CustomerServiceTest 扩展（批量聚合调用断言/判定一致）

**Checkpoint**: US1 可用——预警性能

---

## Phase 2: 创建默认负责人（US2，P0）

**Goal**: 客户/线索创建默认 owner=当前用户。

**Independent Test**: SALES 创建 → owner=当前用户。

### 实现

- [x] T004 [P] CustomerService.create：未指定 owner 且非 ADMIN → owner=当前用户
- [x] T005 [P] LeadService.create：未指定 owner 且非 ADMIN → owner=当前用户
- [x] T006 [P] CustomerServiceTest/LeadServiceTest 扩展（owner 断言）

**Checkpoint**: US2 可用——默认负责人

---

## Phase 3: 只读事务（US3，P1）

**Goal**: 只读方法补 readOnly=true。

**Independent Test**: 标记后行为不变。

### 实现

- [x] T007 [P] 主要只读方法（Customer/Lead/Contact/Opportunity/Ticket 的 page/detail/stats）补 @Transactional(readOnly = true)

**Checkpoint**: US3 可用——只读事务

---

## Phase 4: 验证与收尾

- [x] T008 创建 CustomerAtRiskIT（预警分页 + 判定一致 + owner 默认）in `backend/src/test/java/com/crm/integration/CustomerAtRiskIT.java`
- [x] T009 Backend `mvn verify` 通过（含既有测试无回归、spotless、JaCoCo）
- [x] T010 线上端点验证（预警分页/owner 默认）
- [x] T011 更新契约文档（按实现校正）与 roadmap 064 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1**: 依赖 Customer360Service
- **Phase 2**: 无依赖
- **Phase 3**: 无依赖
- **Phase 4**: 依赖全部完成

## Notes

- 复用 Customer360Service 批量评估；无迁移
- 导入路径不设 owner（保持现状）
- 提交规范：Conventional Commits
