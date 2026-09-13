---
description: "任务清单：验证门禁转绿（085）"
---

# 任务清单：验证门禁转绿（陈旧集成测试失败背后的真实缺陷）

**Input**: Design documents from `/specs/085-verify-green/`

**Prerequisites**: [plan.md](./plan.md)（必需）、[spec.md](./spec.md)（必需，用户故事来源）、[research.md](./research.md)、[data-model.md](./data-model.md)、[quickstart.md](./quickstart.md)

**Tests**: **必需**。FR-V11 明文要求"上述每一项修复必须附带一个**先红后绿**的测试"。故每个用户故事都以测试任务开头。

**Organization**: 按用户故事分组，每个故事可独立实现与独立验收。

## 格式：`[ID] [P?] [Story] 说明`

- **[P]**：可并行（不同文件、无未完成依赖）
- **[Story]**：所属用户故事（US1/US2/US3/US4）
- 每项任务均含**确切文件路径**

> **编号消歧（重要）**：本文档的 `T0xx` 编号**属于本规格**，与 `specs/083-engineering-consolidation/tasks.md` 的同名编号**无关**。引用 083 的任务时**一律写作「083 T0xx」**。本规格正文多处引用 083 的 T067/T068/T069/T073/T077/T078/T079，不加前缀即会产生歧义。

## 路径约定

沿用仓库既有的 backend / frontend 双模块布局（plan.md 的 Structure Decision）：

- **后端**：`backend/src/main/java/com/crm/`、`backend/src/test/java/com/crm/`
- **前端**：`frontend/src/`

---

## Phase 1: Setup（共享前置）

**Purpose**：固定验收命令、复现基线，使后续每一步都有可对照的"改造前"。

- [X] T001 复现并记录基线：以 `cd backend && mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify` 取得退出码、surefire 计数、failsafe 计数与失败清单，写入本文件「实施记录」。**必须先跑一次**，不得沿用 spec.md 的既有数字而不复现
- [X] T002 [P] 确认验收命令的两条纪律在本次环境成立：①开发后端占用 `target/*.jar` 时构建会在 `spring-boot:repackage` 失败（须用跳过开关）；②`target/jacoco.exec` 存在且 `jacoco:report` 输出为 `Loading execution data file` 而非 `Skipping`。记录实际观察到的现象至本文件「实施记录」

---

## Phase 2: Foundational（阻塞性前置，任何用户故事开始前必须完成）

**Purpose**：判定范围守卫是否会被触发、核定影响面。**这两件事决定了后续任务是否会越界。**

- [X] T003 **[范围守卫，FR-V14]** 读取 `backend/src/main/resources/db/migration/V63__open_platform.sql` 与 `backend/src/main/java/com/crm/entity/WebhookDelivery.java`，逐列确认修③所需的「重试次数 / HTTP 状态 / 错误摘要」**是否已有可用列**。**若缺任一列 → 立即停止实施，回到规格层重新裁决**（不得就地新增迁移，也不得默默把字段塞进其他列）。把核对结论（列名清单 + 判定）写入本文件「实施记录」
- [X] T004 [P] 核定三处修复的**影响面清单**，逐条给出命令与结果：①`webhook_delivery` 的插入点是否全仓唯一；②`status` 的渲染点是否全仓唯一（预期仅 `IntegrationHubPage.tsx:195`）；③是否存在第二处「读实体 → 改非业务字段 → `updateById`」路径（预期无，`UserService.java:136/179/199` 属正确用法/有意业务更新，**不动**）。结论写入本文件「实施记录」

**Checkpoint**：范围守卫通过（无迁移需求）、影响面清单确认 —— 用户故事可以开始。

---

## Phase 3: User Story 1 — 编辑用户不再被"该用户恰好登录过"误拒（Priority: P1）

**Goal**：登录不再消费该用户的乐观锁令牌，同时"最后登录时间"仍被更新。

**Independent Test**：管理员创建用户 → 该用户登录一次 → 管理员以**登录前**拿到的版本标识编辑该用户 → 期望 **200**（改造前必然 409）。

### Tests for User Story 1（先写，确认**失败**）

- [X] T005 [P] [US1] 使 `backend/src/test/java/com/crm/integration/UserIT.java` 的 `userLifecycle:74` 与 `disableUserRevokesAccess:117` 的**预期 409 → 200** 明确对应 FR-V01 的场景（若它们已表达该场景则只需确认语义，**不要**为了让绿而改动断言语义）。另新增一条**直述本故事场景**的用例：读用户 → 该用户登录 → 以读到的版本提交编辑 → 断言 200 **且** `lastLoginAt` 确已变化
- [X] T006 [P] [US1] 在 `backend/src/test/java/com/crm/integration/UserIT.java` 新增**并发编辑仍须 409** 的守卫断言（FR-V02）——防止"修过头"把并发保护一起关掉
- [X] T007 [US1] 跑 `mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify -Dit.test=UserIT`，**记录失败输出作为"红"的证据**（须含 `Status expected:<200> but was:<409>`）

### Implementation for User Story 1

- [X] T008 [US1] 修改 `backend/src/main/java/com/crm/service/AuthService.java`（现 `:89-91`）：把 `user.setLastLoginAt(now); userMapper.updateById(user);` 改为**定向单列更新**——`update(null, new LambdaUpdateWrapper<User>().eq(User::getId, user.getId()).set(User::getLastLoginAt, now))`，使乐观锁插件不介入（实体为 `null`）。**保留** `userStateCache.put(...)` 与 `issueTokens(user)` 原样
- [X] T009 [US1] 确认定向更新**只写目标列**（不整行回写），并在代码处留一行注释说明"登录是读语义操作，不得消费乐观锁令牌"。确认 `lastLoginAt` 的**内存态**与库内一致（若 `issueTokens` 或响应体读取该字段，须避免拿到旧值）
- [X] T010 [US1] 跑 T007 的同一命令，确认 T005/T006 全部**转绿**，并把"红→绿"两次输出摘录写入本文件「实施记录」

**Checkpoint**：US1 可独立验收——`UserIT` 全绿，且并发编辑仍 409。

---

## Phase 4: User Story 2 — 关闭销售机会的失败语义回到契约（Priority: P1）

**Goal**：关闭端点缺结果时返回契约约定的 **422 `CLOSE_RESULT_REQUIRED`**；其他端点的 400 语义**不变**。

**Independent Test**：以合法身份请求关闭一个未关闭的销售机会，请求体不含结果字段 → 断言 **422** 且错误码为 `CLOSE_RESULT_REQUIRED`。

### Tests for User Story 2（先写，确认**失败**）

- [X] T011 [P] [US2] 确认 `backend/src/test/java/com/crm/integration/OpportunityIT.java` 的 `closeWithoutResultReturns422:175` 已表达该契约断言；跑一次并**记录失败输出作为"红"的证据**（须含 `Status expected:<422> but was:<400>`）
- [X] T012 [P] [US2] **新增 FR-V15 的守卫测试**（Q1 方案 B 相对方案 A 多出来的那部分风险）：对**另一个**带 `@Valid` 的端点提交非法请求体 → 断言**仍是 400**。放在 `backend/src/test/java/com/crm/integration/` 下合适的既有 IT 类中（与所测端点同模块），**须先红**——若非红，说明该断言没有区分度，须换一个真正会走 `handleValidation` 的端点
- [X] T013 [P] [US2] 在 `backend/src/test/java/com/crm/integration/OpportunityIT.java` 补齐契约的三条验收场景（FR-V04）：①合法结果关闭**成功**；②已关闭再关闭返回 `ALREADY_CLOSED`；③**非空但非法**的结果（如 `"MAYBE"`）返回 422 —— ③用于钉住"改造前后同一端点两条路径语义不一致"这件事已被消除
- [X] T014 [P] [US2] 在 `backend/src/test/java/com/crm/integration/OpportunityIT.java` 补一条**边界情况**断言（spec.md 边界节）：同时"缺结果"且"版本过期"时返回 **409 `VERSION_CONFLICT`**（版本冲突优先）——若实测不是 409，**不要**为了让绿而改断言，须记录实测结果并回到规格层确认取舍

### Implementation for User Story 2

- [X] T015 [US2] 修改 `backend/src/main/java/com/crm/exception/GlobalExceptionHandler.java`（现 `:31-38`）：在 `handleValidation` 中**新增一个按「绑定目标 DTO 类型 + 违规字段名」限定**的分支——当绑定目标是 `CloseRequest` 且违规字段是 `closeResult` 时，返回 **422** 且错误码复用**既有**的 `ErrorCode.CLOSE_RESULT_REQUIRED`（`ErrorCode.java:29`，**不新增错误码**）。**兜底分支原样保留为 400**
- [X] T016 [US2] 在该分支处留注释，写明：本分支是 **HTTP 关注点的映射**而非业务判定（章程原则二），业务规则仍在 `SalesOpportunityService`（`:136/:140` 的 `ALREADY_CLOSED` 与 `CLOSE_RESULT_REQUIRED` **保持不动**，作为第二道防线）；并**显式声明**不得扩张为全局 400→422
- [X] T017 [US2] 保留 `backend/src/main/java/com/crm/dto/opportunity/CloseRequest.java` 的 `@NotBlank` **原样**（章程原则三，Q1 裁决 B 的硬性要求）——确认本次改动**未**触碰该文件
- [X] T018 [US2] 跑 `verify -Dit.test=OpportunityIT`，确认 T011–T014 全部**转绿**（含 T012 的 400 守卫），把"红→绿"两次输出摘录写入本文件「实施记录」

**Checkpoint**：US2 可独立验收——`OpportunityIT` 全绿，且其他端点的 400 语义经测试证明未被改变。

---

## Phase 5: User Story 3 — 投递记录在投递过程中就可见，且不会静默丢失（Priority: P2）

**Goal**：投递记录从**派发**那一刻起就存在（状态"投递中"），结束时原地更新为终态；中断残留有可判定的归宿。

**Independent Test**：对指向不可达地址的订阅触发投递，**1.5 秒内**查询投递记录 → 该条已存在且状态为"投递中"。

### Tests for User Story 3（先写，确认**失败**）

- [X] T019 [P] [US3] 跑 `verify -Dit.test=IntegrationHubIT`，**记录 `integrationFlow:93` 的失败输出作为"红"的证据**（须含 `JSON path "$.data.total" expected:<1> but was:<0>`）。**注意：该用例本身不改动**——它是修对了根因后自然转绿的证据（spec.md 澄清 Q2 的理由）
- [X] T020 [P] [US3] 在 `backend/src/test/java/com/crm/integration/IntegrationHubIT.java` 新增断言：投递**结束时**状态确已转为终态，且重试次数/HTTP 状态反映最终结果（FR-V06，"投递中 → 终态"的转换**必须确实发生**）
- [X] T021 [P] [US3] 在 `backend/src/test/java/com/crm/integration/IntegrationHubIT.java` 新增断言：一次投递**只有一条**记录（FR-V08，防止"先落库再更新"被写成每次重试插一行）
- [X] T022 [P] [US3] 为 FR-V07（中断残留）新增测试：构造一条早于时间阈值的 `PENDING` 记录 → 触发清扫 → 断言其被判定为终态且带明确原因。若该测试在 `*IT` 形态下不便构造，改放单元测试，但**必须存在**

### Implementation for User Story 3

- [X] T023 [US3] 修改 `backend/src/main/java/com/crm/service/WebhookDeliverer.java`：在**重试循环之前**插入一条状态为 `PENDING` 的记录，并保留其生成的 id（`application.yml:93` 的 `id-type: auto` 保证插入后实体带 id）
- [X] T024 [US3] 修改 `WebhookDeliverer.java`：循环结束后以 `updateById` **原地更新**同一条记录为终态（`SUCCESS`/`FAILED`），带上重试次数、HTTP 状态与错误摘要。**删除**原先在循环后 `insert` 的路径（现 `:103` 的 `record(...)`，`:164` 的 `insert`），确保**全仓仍只有一个插入点**
- [X] T025 [US3] 使 `WebhookDeliverer.java` 中 **URL 被拒**的路径（现 `:60-66`）与重试耗尽路径**复用同一条记录**（更新为 `FAILED`），不得另插一行——否则违反 FR-V08
- [X] T026 [US3] **事务边界（章程原则三）**：确认插入与更新**各自独立、未被包在同一事务中**。两者间隔最长 36 秒的重试窗口，包进一个事务会**持锁 36 秒**。在代码处留注释写明这是**刻意的选择**，不是遗漏
- [X] T027 [US3] 新增"中断残留"清扫：把 `created_at` 早于「最长退避总和 + 余量」的 `PENDING` 记录判定为 `FAILED`（带明确原因）。**归属**：倾向新建一个 `ApplicationRunner` 而非塞进 `WebhookDeliverer`（职责分离）。**阈值必须显著大于 36 秒**，且**不得**做成"启动时无条件清空全部 PENDING"（多实例下会误伤真实在途记录，见 data-model.md INV-3）
- [X] T028 [P] [US3] 修改 `frontend/src/pages/settings/IntegrationHubPage.tsx:195`：把二元渲染改为**三态**——`SUCCESS` 绿/成功、`FAILED` 红/失败、`PENDING` **中性色/投递中**（取色参照既有先例 `frontend/src/pages/exports/ExportCenterPage.tsx:40` 的 `'processing'`）
- [X] T029 [P] [US3] 在 `frontend/src/i18n/zh-CN.ts` 与 `frontend/src/i18n/en.ts` 的 `pages.integrationHub` 块中**同步**新增"投递中"键（**两侧必须同步**，否则 `pnpm run i18n:check` 转红——等于用一处新缺陷换掉一处旧缺陷）
- [X] T030 [US3] 跑 `verify -Dit.test=IntegrationHubIT`，确认 T019–T022 **全部转绿**（特别是 `integrationFlow` **在未改动用例的前提下**转绿），把输出摘录写入本文件「实施记录」
- [ ] T031 [US3] 手工时序验证（SC-V05/SC-V06）：对指向 `http://127.0.0.1:9/` 的订阅触发投递 → **1.5 秒内**记录已存在且显示"投递中" → 约 36 秒后原地变"失败"且**仍只有一条** → 投递中 kill 并重启后端，确认悬空记录被清扫为终态。逐步结果写入本文件「实施记录」

**Checkpoint**：US3 可独立验收——记录派发即存在、终态确实发生、残留有归宿、恰好一条。

---

## Phase 6: User Story 4 — 合并门禁从名义变为事实（Priority: P1）

**Goal**：`mvn -B verify` 退出码 0，且覆盖率门槛**在该次构建中被实际判定**。

**Independent Test**：执行完整构建校验，断言退出码 0，且构建日志中**出现覆盖率门槛的判定动作及其结论**（而非"因前序失败未到达"）。

> **依赖说明（如实登记）**：本故事按**重要性**是 P1，按**依赖**却排在最后——它要求前三项先落地。这不需要调和，但必须写明，以免被读成"P1 却排在 P2 之后"是排序错误。

- [X] T032 [US4] 跑完整构建校验：`cd backend && mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify`，断言**退出码 0**、surefire 与 failsafe **零失败**（SC-V01）
- [X] T033 [US4] 确认 `jacoco:check (coverage-check)` 在该次构建中**有实际判定输出**（含 `INSTRUCTION covered ... / ...` 与阈值比较），而非零命中（SC-V02 / FR-V09）
- [X] T034 [US4] **实测并记录本次的覆盖率比值**（`target/site/jacoco/index.html` 或构建日志）。**不得沿用 083 的 0.8018**——本规格新增了代码行，分母已变，必须重新实测
- [X] T035 [US4] **FR-V10 反向验证（不可省略）**：把 `backend/pom.xml` 的覆盖率阈值**临时调高到高于 T034 实测值** → 跑同一命令 → 断言构建**失败且失败点正是 `jacoco:check`** → **还原阈值** → 重跑一次确认回到绿。三次实测值（原阈值、临时阈值、当前 covered 比值）写入本文件「实施记录」
- [X] T036 [US4] 撤回 083 的 T068 偏差登记（SC-V07）：在 `specs/083-engineering-consolidation/tasks.md` 中**追加**一条订正记录（说明该偏差批准所覆盖的 4 例失败已由本规格修复、该偏差可以撤回），**不得删改原批准记录的文字**（遵守"订正不静默、原文留痕"）

**Checkpoint**：门禁从名义变为事实——有退出码、有判定输出、有反向验证、有偏差撤回。

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**：收尾、范围守卫核验、跨规格留痕。

- [X] T037 [P] 前端门禁全套核验：`cd frontend && pnpm run typecheck && npx eslint . && pnpm run i18n:check && pnpm run menu:check && pnpm run test:coverage`，全部退出码 0（重点看 `i18n:check`）
- [X] T038 范围守卫终检（FR-V12 / FR-V14）：`git status --porcelain backend/src/main/resources/db/migration/` **无输出**；`git diff --stat specs/001-crm-core/contracts/ specs/058-*/contracts/ specs/055-*/contracts/` **无输出**。两者任一有输出即说明越界，须回到规格层处理
- [X] T039 [P] 按 [quickstart.md](./quickstart.md) 逐条执行验证 1–6，并**回填实测结果**（含"改造前表现"的对照）；若某项无法执行，如实写明原因，**不得留空或记为通过**
- [X] T040 [P] 在 `specs/README.md` 与 `specs/roadmap.md` 登记 085（若本仓库的登记惯例要求）；本规格**无迁移**，故迁移对照表不动——**核对**这一点，不要凭印象
- [X] T041 提交：先跑 `ListAgents` 确认无并行会话在编辑同一文件，再以**显式路径**暂存（**严禁** `git add -A`），提交信息遵循 Conventional Commits，末尾附 `Co-Authored-By: Claude Code <noreply@anthropic.com>`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**：无依赖，可立即开始
- **Foundational (Phase 2)**：依赖 Setup —— **阻塞所有用户故事**。特别是 **T003 是硬门**：若修③确需迁移，整个 US3 必须在规格层重新裁决后才能继续
- **User Stories (Phase 3–6)**：
  - **US1（Phase 3）与 US2（Phase 4）互相独立**，可在 Foundational 后**并行**
  - **US3（Phase 5）独立于 US1/US2**，同样可在 Foundational 后并行
  - **US4（Phase 6）依赖 US1 + US2 + US3 全部完成**——它验证的是前三者合起来的结果，无法提前
- **Polish (Phase 7)**：依赖全部用户故事完成

### User Story Dependencies

- **US1（P1）**：Foundational 后即可开始，无跨故事依赖
- **US2（P1）**：Foundational 后即可开始，无跨故事依赖
- **US3（P2）**：Foundational 后即可开始，**但 T003 的结论是它的先决条件**
- **US4（P1）**：**依赖 US1、US2、US3 全部完成**（重要性 P1、依赖排在最后，见 Phase 6 的依赖说明）

### Within Each User Story

- 测试**必须先写并确认失败**（FR-V11），再动实现
- 每个故事结束时**必须**留下"红→绿"两次输出的摘录（T010/T018/T030）——没有红过，就没有证据证明测试钉住了被修的行为
- 故事完成后才进入下一个优先级

### Parallel Opportunities

- T002 与 T004 可并行（不同关注点，均为只读核查）
- **Foundational 完成后，US1 / US2 / US3 三条线可完全并行**（不同文件：`AuthService` / `GlobalExceptionHandler`+`CloseRequest` / `WebhookDeliverer`+前端）
- 每个故事内部：测试任务（标 [P]）可并行编写
- US3 内部：T028（tsx）与 T029（i18n）可并行，但**必须同时完成**，否则 `i18n:check` 转红
- T037 / T039 / T040 可并行

---

## Parallel Example: 三条线并行（Foundational 完成后）

```bash
# 开发者 A —— US1（认证/乐观锁）
Task: "T005/T006 写 UserIT 用例（先红）；T008 改 AuthService 定向更新；T010 取证"

# 开发者 B —— US2（契约对齐）
Task: "T011-T014 写 OpportunityIT 用例（先红）；T015 改 GlobalExceptionHandler；T018 取证"

# 开发者 C —— US3（投递记录）
Task: "T019-T022 写 IntegrationHubIT 用例（先红）；T023-T027 改 WebhookDeliverer + 清扫；T028/T029 前端三态；T031 时序取证"
```

三者触及的文件集**互不相交**，可安全并行。

---

## Implementation Strategy

### MVP Scope

**MVP = Foundational + US1 + US2**（两个 P1，互相独立）。完成后 `verify` 的 4 例失败中**有 3 例**转绿（`UserIT` 两例 + `OpportunityIT` 一例），两个有明确用户可见伤害的缺陷先被消除。

**但 MVP 不足以让 `verify` 转绿**——第 4 例（`IntegrationHubIT`）仍在，故 US4 仍不成立。这一点必须说清楚：本规格**没有**可独立交付的"绿"的中间态，US4 只能在 US3 也完成后验收。

### Incremental Delivery

1. Setup + Foundational → 范围守卫与影响面确认
2. US1 → 独立验收（`UserIT` 全绿 + 并发编辑仍 409）
3. US2 → 独立验收（`OpportunityIT` 全绿 + 其他端点仍 400）
4. US3 → 独立验收（记录派发即存在 + 时序观察 + 残留清扫）
5. US4 → `verify` 转绿 + 覆盖率门槛实测判定 + 反向验证 + 偏差撤回
6. Polish → 前端门禁、范围守卫终检、跨规格留痕、提交

### 风险与已知阻断

| # | 风险 | 处置 |
|---|---|---|
| **R1** | 修③所需的列不存在 → 触发 FR-V14 范围守卫 | **T003 前置判定**。若触发，**停止**并回到规格层重新裁决，不得就地扩围 |
| **R2** | "修过头"——为修①把乐观锁一起关掉 | T006 的**并发编辑仍 409** 守卫断言，必须**先红后绿**地存在 |
| **R3** | 全局 400→422 污染所有端点 | T012 的 FR-V15 守卫断言 + T016 的注释约束；T038 范围守卫终检 |
| **R4** | 构建噪声被误读为代码缺陷 | T001/T002 固定验收命令并复现基线；quickstart.md 的两条取证纪律 |
| **R5** | 出现**第 5 例**失败（基线外） | **不属本规格**。但**必须记录并重新裁决范围**，不得顺手修掉而不留痕（spec.md「假设」的明文要求） |
| **R6** | 阈值反向验证后忘记还原 | T035 把"还原并重跑"写进同一项任务，且三次数值均须留痕 |

---

## Notes

- **[P]** = 不同文件、无未完成依赖
- **[Story]** 标签用于把任务映射回 `spec.md` 的用户故事，保证可追溯
- 每个故事**都必须**能独立完成与独立验收
- **实施前先确认测试失败** —— 这是 FR-V11 的硬性要求，不是建议
- 每个任务或逻辑分组后提交；提交**只用显式路径**（本仓库常有并行会话共用工作区）
- **避免**：模糊任务、同文件冲突、破坏故事独立性的跨故事依赖

---

## 实施记录

> 本节由实施过程中的**实测结果**回填。**订正不静默、原文留痕**：若某项结论后被发现不成立，**追加**订正说明，不改写原记录。

### T001 —— 复现基线（**实测，非沿用 spec.md 数字**）

命令：`cd backend && mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify`

| 指标 | 实测值 |
|---|---|
| 退出码 | **1** |
| surefire（`*Test`） | 551 例 / 0 失败 / 0 错误 |
| failsafe（`*IT`） | **275 例 / 4 失败 / 0 错误** |
| `jacoco:check (coverage-check)` | **零命中** —— 覆盖率门槛**从未被判定过** |

4 例失败的逐条证据（取自 `target/failsafe-reports/*.txt`，**失败集中在 3 个类**）：

| 类 | 计数 | 失败信息 |
|---|---|---|
| `UserIT` | 4 例 / **2 失败** | `AssertionError: Status expected:<200> but was:<409>` ×2（`userLifecycle:74`、`disableUserRevokesAccess:117`） |
| `OpportunityIT` | 4 例 / **1 失败** | `AssertionError: Status expected:<422> but was:<400>`（`closeWithoutResultReturns422:175`） |
| `IntegrationHubIT` | 2 例 / **1 失败** | `AssertionError: JSON path "$.data.total" expected:<1> but was:<0>`（`integrationFlow:93`） |

> **关键事实（决定了 US1/US2/US3 的"红"证据形态）**：这 4 例的**期望值本来就是修好之后的目标值**（200 / 422 / total=1）。也就是说，用例早已写对，是**生产代码**没跟上。故 FR-V11 要求的"先红后绿"在本规格中**无需回退代码制造红**——上表就是"红"。

### T002 —— 验收命令的两条纪律（**实测观察**）

- **纪律①（jar 占用）**：实测 `target/crm-backend-0.1.0-SNAPSHOT.jar` 存在（3772428 字节，15:01）；`netstat -ano | grep LISTENING.*:8081` **无输出** —— 取证时**没有**开发后端在跑，故 T001 那次构建中 `spring-boot:repackage` 并未真的撞锁（jar 时间戳 15:01 即该次构建产物）。`-Dspring-boot.repackage.skip=true` 在本环境是**预防性**开关：它防的是"取证时恰好有后端在跑"这一情形，本次未触发。**如实登记，不夸大为"已排除该噪声"。**

  > **【订正，2026-09-13 15:25，原文保留】上述结论有两处不实，全部作废，以本框内为准。**
  >
  > **不实之一：`netstat` 判据写反了。** 上文的过滤模式 `LISTENING.*:8081` **永不匹配**实际行序——真实行是 `TCP  0.0.0.0:8081  0.0.0.0:0  LISTENING  7680`，"LISTENING"排在端口**之后**。故"无输出"是**假阴性**，不是"没有后端"。复核（`netstat -ano | grep ":8081"`）显示 **8081 确实在监听，PID 7680**；同时 5173（前端 dev，PID 20724）、3306/6379（MySQL/Redis，PID 21532）在监听。**这正是"用一个自认为会匹配的模式去证明否定命题"的典型翻车**，记在此处以儆效尤：证明"不存在"时必须先用一个**已知为真**的样例验证判据本身。
  >
  > **不实之二：jar 时间戳的归因错了。** "jar 时间戳 15:01 即该次构建产物"是**未经核对的推断**，且与事实不符：`-Dspring-boot.repackage.skip=true` 会跳过 `spring-boot:repackage`，而**同一次构建里 `jar:jar` 仍在跑**（15:19 的日志有 `jar:3.3.0:jar ... Building jar: ...crm-backend-0.1.0-SNAPSHOT.jar`）。故 jar 是被 `jar:jar` 重写的，**与 repackage 无关**；`repackage` 那一步在本环境**每次都被跳过**，因此"撞不撞锁"**从未被本次构建检验过**。
  >
  > **订正后的实际状态**：①8081 上有一个**陈旧**后端在跑（`java -jar target/crm-backend-0.1.0-SNAPSHOT.jar`，启动于 **08:40:17**），而本规格改动的 class 编译于 **15:17** → 该进程**不含本次任何修复**，**不可作为 085 的任何证据**；②该进程非本次启动，按"不重启可能属于并行会话的后端"约定**未动它**；③`-Dspring-boot.repackage.skip=true` 仍是验收命令的必需项，但理由是"**避开 repackage 与在跑进程争用 jar**"，与上文所述"预防撞锁"的**观察**相符、**归因**已订正。
- **纪律②（JaCoCo 代理）**：`target/jacoco.exec` 存在（5018005 字节，15:12，即 T001 那次构建产出）→ 证明代理确实挂上了、执行数据确实落盘。与记忆中的 `-DargLine` 陷阱一致：**只要在命令行传 `-DargLine`，代理会被挤掉**；本次未传。

### T003 —— 范围守卫（FR-V14，**关卡是否触发**）

对 `backend/src/main/resources/db/migration/V63__open_platform.sql:35-49` 的 `webhook_delivery` 建表语句逐列核对：

| 修③ 所需 | 表中是否已有 | 列定义 |
|---|---|---|
| 重试次数 | **已有** | `retry_count INT` |
| HTTP 状态 | **已有** | `http_status INT` |
| 错误摘要 | **已有** | `error VARCHAR(500)` |
| 状态 | **已有** | `status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS'` |

**判定：守卫未触发，修③ 不需要任何迁移。** 两个附带结论：① `status` 是 `VARCHAR(20)` 而**不是 ENUM**，故新增取值 `PENDING` 不涉及 schema 变更；② 该表**无 `updated_at` 列**，故原地更新不涉及时间戳维护。

### T004 —— 影响面清单（**实测**）

| # | 核查项 | 预期 | 实测结果 |
|---|---|---|---|
| ① | `webhook_delivery` 插入点是否全仓唯一 | 唯一 | **确认唯一**：全 `main` 下 `deliveryMapper.insert(...)` 仅 `WebhookDeliverer.java:188` 一处（`WebhookService.java:71` 是 `subscriptionMapper.insert`，另一张表） |
| ② | `status` 渲染点是否全仓唯一 | 仅 `IntegrationHubPage.tsx` | **确认唯一**：全 `frontend/src` 下渲染 `webhook_delivery.status` 的仅 `IntegrationHubPage.tsx:198-200`（其余 `.status ===` 命中分属合同模板/数据保留/审批/导出等模块，与 `webhook_delivery` 无关） |
| ③ | 是否另有「读实体 → 改非业务字段 → `updateById`」路径 | **预期无** | **与预期不符，实际有 1 处**：`PersonalCenterService.java:62`（`user.setDisplayName(...)` 后 `userMapper.updateById(user)`）。**裁定：属正确用法，不改动** —— 该实体在同一次请求内读出（版本新鲜），且 `displayName` 是用户对**自己**记录的**真实业务编辑**，消费乐观锁令牌正是其应有语义；与缺陷① 的区别在于缺陷① 是**登录这个读语义**操作去改一个与业务无关的字段。另 `UserService.java:137/160/180/200` 四处 `updateById` 均为有意业务更新（`:137` 显式 `setVersion(req.getVersion())` 即客户端令牌的正当用法），**未触碰**。 |

> **订正留痕**：第 ③ 项的预期是"无"，实测为"有 1 处"。此处**保留预期原样**并记下差异及其裁定，不做静默改写。

### 红→绿纪律的一处如实说明（**不掩饰**）

本规格新增的用例分两类，取证要求不同，须分开陈述，不得混为一谈：

- **真·先红后绿（3 条路径）**：`UserIT`（2 例）、`OpportunityIT`（`closeWithoutResultReturns422`）、`IntegrationHubIT#integrationFlow`。上表 T001 即其"红"。
- **不变量守卫（绿→绿，牙齿靠反向验证证明）**：`T006`（并发编辑仍 409）、`T012`（其他端点仍 400）、`T021`（一次投递只有一条记录）。它们**在改造前后都应绿** —— 这正是它们存在的意义（防止"修过头"）。因此**不能**用"先红后绿"证明其有区分度；其有效性由 T035 的反向验证、以及 `T012` 内部那条"必须走 `handleValidation` 路径"的断言（`$.error.message` == `参数校验失败`）来保证。若强行回退代码制造红，反而会破坏其守卫语义。

### 绿侧实测（T010 / T018 / T030 合并取证）

命令（三个故事一次跑完，逐类结果从 `failsafe-reports` 分别取，不影响"独立验收"的判定）：

```bash
cd backend && mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true -Djacoco.skip=true \
  spotless:apply verify -Dit.test=UserIT,OpportunityIT,IntegrationHubIT
```

| 项 | 实测值 |
|---|---|
| 退出码 | **0**（`BUILD SUCCESS`） |
| surefire | **555 例 / 0 失败 / 0 错误**（基线 551 + 新增 `WebhookDelivererTest` 4 例） |
| failsafe | **14 例 / 0 失败 / 0 错误**（只含指定的 3 类） |

逐类红→绿对照（**同一类、同一批用例**，改动前后可直接对比）：

| 类 | 基线（红） | 本次（绿） | 增量解读 |
|---|---|---|---|
| `UserIT` | 4 例 / **2 失败**（`expected:<200> but was:<409>` ×2） | **6 例 / 0 失败** | +2 为 T005/T006 新增（`loginDoesNotBumpVersion`、`staleVersionStillRejected`） |
| `OpportunityIT` | 4 例 / **1 失败**（`expected:<422> but was:<400>`） | **6 例 / 0 失败** | +2 为 T013/T014 新增（`bothCloseFailurePathsAgree`、`closingAlreadyClosedReturnsAlreadyClosed`） |
| `IntegrationHubIT` | 2 例 / **1 失败**（`$.data.total` `expected:<1> but was:<0>`） | **2 例 / 0 失败** | **计数未增** —— 正如 T019 所要求的：`integrationFlow` **用例本身未改动**，靠修根因而转绿 |

> **关于 `-Djacoco.skip=true`（须声明，否则会被误读）**：本次取合用 `-Djacoco.skip` **显式跳过**覆盖率插件，因为只跑了 3 个 IT 类，覆盖率必然低于全量阈值，会让构建中止在 `jacoco:check` 而掩盖"测试是否转绿"这个问题。**这是显式跳过，与纪律② 的 CLI `-DargLine` 陷阱是两回事**：后者是**静默**把代理挤掉、连 `jacoco.exec` 都不生成且全程无报错；前者在日志中有明确记录。**覆盖率的判定留到 T032–T035 做**（不跳过任何插件）。

> **【订正，2026-09-13，原文保留】上表是 T010/T018/T030 当次运行的快照，其中"本次"列的两个计数此后**又变过**：T014 的探测项给 `OpportunityIT` 再增 1 例（`missingResultTakesPrecedenceOverStaleVersion`），T022 的 FR-V07 库级验证给 `IntegrationHubIT` 再增 1 例（`stalePendingDeliveriesAreSweptToFailed`）。故**当前**为 `OpportunityIT` **7 例**、`IntegrationHubIT` **3 例**。**红→绿的结论不变**（三类均 0 失败），变的只是用例总数——全量实测见下方 T032。

### 实施过程中我自己引入并修掉的 2 个缺陷（**必须留痕**）

FR-V11 要求"先红后绿"，但**红**也可以来自我自己写错的测试。下面两例都是**测试侧的错**，不是生产代码的错——如实登记，以免被读成"生产代码又出问题"。

1. **编译错误：`Wrapper` 上没有 `getParamNameValuePairs()`。**
   `WebhookDelivererTest.sweepOnlyTargetsStalePendingRows` 断言清扫条件时，把捕获到的 wrapper 当作 `Wrapper<WebhookDelivery>` 调 `getParamNameValuePairs()`；该方法的声明位置是 `AbstractWrapper`（`javap` 实测确认），顶层 `Wrapper` 不暴露它。
   **后果链值得记下**：`testCompile` 失败 → 构建在 **surefire 之前**中止 → **failsafe 根本没跑**、`failsafe-reports/` 里只有上次的陈旧文件（时间戳 15:11–15:12 与基线**逐字相同**）。若不比时间戳，会把它误读成"IT 全绿"。
   **另一个同类陷阱**：我用 `mvn ... > log 2>&1; echo "EXIT=$?"` 取证，`EXIT=` 落到 **stdout**（即后台任务的输出文件），而后台任务上报的"exit code 0"是 **`echo` 的**退出码，**不是 Maven 的**。同一坑在本规格已踩第二次，此后一律把 `EXIT=` 重定向进日志再读日志。
   **修法**：下溯一层 —— `((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs()`。

2. **测试设计错误：在对象被原地改写**之后**才去断言它的初始态。**
   首轮修完编译错误后 `WebhookDelivererTest` **4 例中 2 例失败**：`expected: "PENDING" but was: "SUCCESS"` / `but was: "FAILED"`。
   **根因不是生产代码**：`insertPending` 插入的实体与 `finish` 更新的是**同一个实例**，`ArgumentCaptor` 拿到的引用因此指向已被改成终态的对象——"派发时是 PENDING"这个断言**在事后无从表达**。
   **这个错误本身是有信息量的**：它恰恰是 FR-V08"一次投递只有一条记录、终态是**原地更新**而非再插一行"的直接证据（`isSameAs` 断言成立即证明了这一点）。
   **修法**：用 `doAnswer` 让 mock 的 `insert` 在**被调用那一刻**记录快照（`statusAtInsert` / `retryCountAtInsert`），断言改打在快照上。修后 **4/4 绿**（`Tests run: 4, Failures: 0, Errors: 0`）。

### T014 探测项实测（**结论与 spec.md 的"倾向"不符，已回退规格层待裁**）

T014 明文要求："若实测不是 409，**不要**为了让绿而改断言，须记录实测结果并回到规格层确认取舍。" 实测**确实不是** 409。

命令：`mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true -Djacoco.skip=true -Dmaven.compiler.useIncrementalCompilation=false -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false verify -Dit.test=OpportunityIT`
结果：**`Tests run: 7, Failures: 0, Errors: 0`，`BUILD SUCCESS`，退出码 0**。

用例 `OpportunityIT.missingResultTakesPrecedenceOverStaleVersion` 用**同一个必然不匹配的版本号 99** 发两次请求，构成一组对照实验：

| 请求体 | 实测 | 作用 |
|---|---|---|
| `{"closeResult":"WON","version":99}` | **409 `VERSION_CONFLICT`** | **对照组**：证明 409 这条路径**可达**，排除"根本判不出来"的混淆解释 |
| `{"version":99}`（缺结果） | **422 `CLOSE_RESULT_REQUIRED`** | **探测项**：优先级实测结论 |

**机制（两层同序，非巧合）**：① 表现层 `@Valid @RequestBody` 在**进入方法体之前**即抛 `MethodArgumentNotValidException`，域层未被调用；② 域层即便被调用，也是 `SalesOpportunityService.close` 先在 `:139` 判结果、后在 `:149` 才用 `version` 抛 `VERSION_CONFLICT`。

**结论**：`spec.md` 边界节写的"倾向：版本冲突优先"**从未被实现过**；本规格**未**改变它（属 3 个缺陷之外）。已在 `spec.md` 该条下追加【实测登记】（原文保留），登记两条取舍路径与建议（**维持现状**：请求体不合法时先报缺字段，是调用方当场可修的；改 409 优先需前移版本校验 = **扩围**）。**并说明契约未规定优先级，故不构成契约违背，不触发 FR-V12、不动任何契约文件。**

### ⚠️ 新发现的环境危害：**IDE 的 ECJ 错误类会覆盖 Maven 的 javac 产物**（**影响取证可信度，必须登记**）

**现象**：一次 `-Dit.test=OpportunityIT` 运行中，7 例全部 `Errors: 7`，报 `java.lang.Error: Unresolved compilation problems: The import com.crm.AbstractIntegrationTest cannot be resolved ...` —— 而**同一时刻**构建日志显示 `testCompile` 打印的是 **`Nothing to compile - all classes are up to date`**（= Maven 认为类是最新的、根本没重编）。

**根因**：`target/test-classes/com/crm/integration/OpportunityIT.class` 被 **ECJ（Eclipse/JDT，即 IDE 的 Java 语言服务器）** 覆盖了。JDT 的设计是"带错误也产出可运行的 class，运行到错误处即抛 `java.lang.Error`"，于是该 class 内含 **7 处 `Unresolved compilation` 字面量**（`grep -ac` 实测），而它是**在 Maven 编译之后**写下的：源文件 mtime `15:22:21`，该 class mtime `15:22:22`。

**为什么危险（三条，都比"这次失败"本身更要紧）**：
1. **它会伪装成"用例失败"** —— 报的是测试错误，看起来像代码问题，而真正的因果链是"构建产物被 IDE 污染"；
2. **Maven 的时间戳式增量判断看不见它** —— class 比源**新**，于是 `testCompile` 判定"无事可做"，**不会**自我修复；若不复核产物来源，就会一直拿到同一个假失败；
3. **它是不确定性的来源** —— IDE 何时写、写哪些，取决于编辑与保存时机，因此同一命令两次运行可能得到不同结论。

**排查手段（已固化）**：`grep -rl "Unresolved compilation" target/test-classes/ target/classes/`。**全树扫描实测仅此 1 例**（`OpportunityIT.class`）。
**处置**：`rm -f target/test-classes/com/crm/integration/OpportunityIT.class` + `-Dmaven.compiler.useIncrementalCompilation=false` 强制重编（日志出现 `Compiling 1 source file with javac`）→ 该 class 的 `Unresolved` 计数归 **0**，用例转 **7/7 绿**。
**残留风险（如实声明）**：IDE 仍在运行，**该危害随时可能复发**；上述 grep 是本规格此后每次取证的**必做项**，已在 T032 的步骤中执行。

### T031 —— 手工时序验证：**未执行**（如实登记，不计为通过）

T031 要求真起一个后端、对指向 `http://127.0.0.1:9/` 的订阅手工观察三件事。**本次未执行**，原因是环境性的、必须写明：

1. 端口 8081 上**已有**一个后端起在跑（PID 7680，启动于 08:40:17，`java -jar target/crm-backend-0.1.0-SNAPSHOT.jar`）。它的 `.class` 时间（08:40）**早于**本规格的产物（15:17），故它**不含**本规格的任何改动——拿它观察必然看不到 PENDING。
2. 起第二个后端会**重复触发调度器**（含本规格新增的 webhook 清扫），对**同一个开发库**产生并发写入，且与那个可能属于并行会话的进程抢同一份数据。按"不擅自重启可能属于他人的后端"的约定，不动它。
3. 不重启它就无法用**本次构建的 jar** 做手工观察——而候选做法（`mvn spring-boot:run` 另起端口）仍会带来第 2 条的问题。

**结论：T031 记为"未执行"，不是"通过"。** 其实质内容由下列自动化用例覆盖，但**时序的端到端连续性仍缺一份手工证据**，此缺口如实留在下方：

| T031 要求观察的 | 自动化等价物 | 覆盖程度 |
|---|---|---|
| 1.5 秒内记录已存在且为"投递中" | `IntegrationHubIT#integrationFlow`（真 HTTP：创建通道→触发事件→**等 1.5s**→查交付记录，断言 `status == PENDING` 且 `items.length == 1`） | **完整覆盖**（同一时序、同一状态、真库真 HTTP） |
| 约 36 秒后**原地**变"失败"且仍只有一条 | `WebhookDelivererTest#exhaustedRetriesMarkFailedWithRetryCount`（`times(1).insert` + `times(1).updateById` + `isSameAs` 证同一条）+ `#rejectedUrlStillProducesExactlyOneRecord` | **覆盖"原地更新且只有一条"**；**未覆盖"真等满 36 秒"**（该用例用中断标志让首次 sleep 立即抛出，正是为了不真等——这是有意的取舍） |
| 投递中 kill 重启 → 悬空记录被清扫为终态 | `IntegrationHubIT#stalePendingDeliveriesAreSweptToFailed`（**真库**插入 10 分钟前的 PENDING + 1 条刚派发的 PENDING → 调 `sweepStalePending()` → 断言前者变 `FAILED` 且带原因、后者**仍为 PENDING**） | **覆盖清扫的库级效果与阈值不被误伤**；**未覆盖"真 kill -9 一个进程再重启"**（后者由调度器按 cron 周期触发，触发本身无测试，只依赖 Spring `@Scheduled` 的既有契约） |

### T032 —— 完整构建转绿（**实测，退出码 0**）

命令（**不改任何插件开关**，即验收命令原文）：

```bash
cd backend && mvn -B -Djava.version=17 -Dspring-boot.repackage.skip=true verify
```

| 项 | 实测值 |
|---|---|
| 退出码 | **0**（`BUILD SUCCESS`，Total time **02:11 min**） |
| surefire | **555 例 / 0 失败 / 0 错误 / 0 跳过**（基线 551 + 新增 `WebhookDelivererTest` 4 例） |
| failsafe | **282 例 / 0 失败 / 0 错误 / 0 跳过**（基线 275 + 新增 7 例，**逐项对账见下**） |
| `jacoco:check (coverage-check)` | **已执行并判定通过**（日志出现 `jacoco:0.8.11:check (coverage-check)`） |
| failsafe 报告文件 | **73 个 `TEST-*.xml`** |

**这只是第 2 次尝试。第 1 次（15:26–15:29）失败**，且失败**不在** 083 批准的那 4 例之内——见下一节。

**用例数对账（先前登记为"对不上"，此处已对平）**：T001 记 failsafe 基线 275，而本次 282，差 **+7**；我最初按"新增 5 例"记，故**误判为对不上**。逐类实测（从本次 `failsafe-reports` 取）：`UserIT` 6（基线 4，+2）、`OpportunityIT` 7（基线 4，+3）、`IntegrationHubIT` 3（基线 2，+1）、`ExceptionHandlerIT` 5（基线 4，+1）、`WebhookRedirectIT` 4（±0）；**2+3+1+1 = 7**，**275 + 7 = 282 ✓**。先前的"5"漏算了 `ExceptionHandlerIT` 的 T012 用例与 T014 探测项。**故基线 275 与本次 282 是自洽的**，不存在计数缺口。
> 附注（仍如实保留）：083 的记录里同一基线写作 **274**，与 T001 的 **275** 差 1。本次**未**去追平它——那是两个时点的采样，与本次改动无关；此处只声明"275 与 282 自洽"。

### 全量构建暴露的**第 5 例**失败：`WebhookRedirectIT`（**由本规格的生产修复直接引起**）

第 1 次全量 `verify` 在 failsafe 处中止：`Tests run: 282, Failures: 1` —— `WebhookRedirectIT.legitimateRedirectIsFollowedHopByHop`，`expected: "SUCCESS" but was: "PENDING"`。这是**真失败，不是抖动**（连跑两次同一类都复现），且**不在** 083/T068 批准的 4 例范围内。它必须被如实说明，不能当作"环境噪声"抹掉。

**它不是生产缺陷，也不推翻本规格的修复——它是同一处修复的镜像面。** 该类的 `awaitDelivery` 辅助方法原判据是"**记录已出现**"（`items.size() > 0` 即返回）。改造前，投递记录要等重试循环跑完才落库，于是"存在"恰好等价于"已有终态"；缺陷③ 修复后记录在**派发那一刻**就以 `PENDING` 落库，于是**"存在"不再蕴含"已判定"**，轮询一上去就读到 `PENDING`，把一次**成功**的投递读成了失败。

于是同一处生产修复在两侧各暴露一个陈旧假设，方向正好相反：
- `IntegrationHubIT#integrationFlow` —— 原来**记录不存在**（total=0）而红；
- `WebhookRedirectIT#legitimateRedirectIsFollowedHopByHop` —— 现在**记录存在得太早**而红。

**修法与声明**：把 `awaitDelivery` 的判据从"记录已出现"改成"**状态已是终态**"（循环直到 `status != "PENDING"`），并加 `IN_FLIGHT` 常量与更具体的超时错误（带记录数、最后一次状态、两跳命中次数）。**这不是放宽断言**：它仍要求等到一个明确的终态，只是不再把"在途"误当"终态"。

**同类的潜在抖动被一并消除（值得记下）**：该类另两例（`legacySubscriptionRowWithDeniedTargetIsRejectedAtDeliveryTime`、`publishToUrlTargetIsRejectedAtDeliveryTime`）用的是**同一个** `awaitDelivery`，判据缺陷同样存在——它们这次**靠时序侥幸**通过（被拒路径在重试循环之前就 `finish`，快到轮询第一拍通常已是 `FAILED`），**任何一次调度延迟都会让它们随机变红**。故这处修改是**必需**的，不是为了让红转绿的粉饰。

**顺带**：该文件改完先被 `spotless:check` 拦下一次（javadoc 折行），按"不与他人未提交工作混在一起"的约定**手工改行**，未跑模块级 `spotless:apply`（`pom.xml`/`Dockerfile`/`ci.yml` 是并行会话的在飞改动，不碰）。

修完后 5 个类合跑：**`Tests run: 25, Failures: 0, Errors: 0`，`BUILD SUCCESS`，退出码 0。**

### T033 —— `jacoco:check` 的"实际判定"（**须靠反向验证才看得见**）

**先说一个反直觉的实测事实**：`jacoco:check` **通过时几乎不打印任何判定内容**——日志里只有 `Loading execution data file .../target/jacoco.exec` 一行，**不含** `INSTRUCTION covered ... / ...` 与阈值比较。那行只在**判定失败**时才出现。故 T033 字面要求的"含 `INSTRUCTION covered` 与阈值比较"，在**绿**的那次构建里**从原理上就不可能出现**；若以"日志里没这行"为判据，会得出"门禁零命中"的**错误**结论。

**证据链改用三条（互相独立）**：
1. **数据非空**：`target/jacoco.exec` = **7 830 477 字节**（7.5 MB）。零命中时该文件根本不会生成（083 T066 已实测过这条判据）。
2. **相位正确**：`jacoco:report (report)` 出现在日志 **2947 行**、failsafe 汇总在 **2944 行** → 报告确在**集成测试之后**生成（FR-V09 的相位修复已生效），故覆盖率**含集成测试**。
3. **判定有牙齿**：T035 反向验证给出了那行被引用的比较文本（见下），证明该 check 真的在拿实测比值与阈值比。

### T034 —— 本次实测覆盖率比值（**未沿用 083 的 0.8018**）

来源 `target/site/jacoco/jacoco.csv`（本次构建产物，非历史值），全模块逐行汇总：

| 计数 | 实测 |
|---|---|
| INSTRUCTION **missed** | 11 131 |
| INSTRUCTION **covered** | **45 171** |
| INSTRUCTION **total** | **56 302** |
| **covered ratio** | **0.8023** |
| 门槛（`pom.xml:302`） | 0.73 |
| 余量 | **+7.23 个百分点** |

**与 083 记录的关系（不得混同）**：083 的 `0.8018`（45 035/56 169）与本次 `0.8023`（45 171/56 302）是**两次独立实测**，分子分母都不同（covered +136、total +133）；本次**重新实测**，未沿用旧值，符合 T034 的要求。
> **残留项（如实登记）**：`pom.xml` 的 jacoco 注释里最后记录的值仍是 `0.7818`（44 008/56 288），**已不反映现状**。该文件含并行会话的在飞改动，按约定**未改**；修正注释留待其提交后进行。

### T035 —— 反向验证（**不可省略的那一步**）

同一命令跑三轮，只动 `pom.xml:302` 的一个数字：

| 轮次 | `<minimum>` | 实测 covered 比值 | 退出码 | 失败点 |
|---|---|---|---|---|
| ① 原阈值 | **0.73** | 0.8023 | **0** | —— |
| ② **临时调高** | **0.90** | **0.80**（日志四舍五入；CSV 精确值 0.8023） | **1** | **正是 `jacoco:check (coverage-check)`** |
| ③ **还原后重跑** | **0.73** | 0.8023 | **0** | —— |

第 ② 轮的关键原文（这是 T033 要的那句"实际判定输出"）：

```
[WARNING] Rule violated for bundle crm-backend: instructions covered ratio is 0.80, but expected minimum is 0.90
[ERROR] Failed to execute goal org.jacoco:jacoco-maven-plugin:0.8.11:check (coverage-check)
        on project crm-backend: Coverage checks have not been met.
```

**这一轮的信息量比"构建失败了"更大**：第 ② 轮里 **surefire 555/0/0、failsafe 282/0/0 全绿**，构建**仍然失败**——**证明覆盖率门禁是决定性的，不是装饰**。这正是 FR-V10 要的"牙齿"，也补上了 T033 在绿轮中无法取得的那句话。

**还原的可信度（写成可复核的形式）**：改动前后对 `pom.xml` 取 MD5 —— 改前 `6058490cee6fa19a9bbbce96036ad20f`，还原后**同一个值**，且 `diff` 无输出（`RESTORED_IDENTICAL`）。即：我只动了 `<minimum>` 一处、且**逐字节还原**，**并行会话在该文件里的未提交改动未被扰动**。

**残留说明**：三轮取证期间若并行会话恰好写入 `pom.xml`，本轮比值会不可比。本次未发生（改前/还原两次 MD5 一致），但这是环境事实而非我能担保的约束，故登记。

### T036 —— 撤回 083 的 T068 偏差登记（SC-V07，**已执行**）

在 `specs/083-engineering-consolidation/tasks.md` 的 T068 条目**下方追加**了一个 `>` 订正块，**未改动原批准记录一个字符**（含批准人 **龙星**、批准日期 **2026-09-12**、三条边界与三条失效条件）。订正块内容：4 例逐项现状（均绿）+ 各自根因与修法、**两条避免过度主张的声明**、以及"批准基础消失故撤回"的效力说明。

其中**第 1 条声明是必须写的**：本规格首次全量构建时**确实**出现过第 5 例失败（`WebhookRedirectIT`），按 T068 的失效条件"出现第 5 例即越界"，**那一刻真的越界了**。写成"从未越界"会是**假的**；故如实记为"一次发生过的越界，已消除"。

### T037 / T038 / T039 / T040 —— 见「验证记录」各节

前端五道门禁、范围守卫终检、quickstart 验证 1–6 的实测登记分别在 `quickstart.md` 与本文档前文；登记面改动经 `git diff` **逐行核对**（`specs/README.md` 只动 3 行：版本行 / 085 表格行 / 编号说明；`specs/roadmap.md` 只动 3 处），**迁移对照表未被触及**（本规格无迁移，已核对而非凭印象）。

### T041 —— 提交（**已执行**）

提交前按约定执行 `ListAgents`：确认仅 1 个并行会话（`crm-gap-remediation`，**idle**）。**以显式路径暂存**（未用 `git add -A`），暂存后复查 `git status` 确认**恰好**并行会话的 3 个在飞文件（`.github/workflows/ci.yml`、`Dockerfile`、`backend/pom.xml`）**留在工作区未被卷入**。

| 项 | 值 |
|---|---|
| 提交 | **`c3256a1`** `fix(085): 修复 4 例集成测试失败背后的 3 个生产缺陷，使 verify 转绿` |
| 文件数 | **25 个**（+2 142 / −35） |
| 分支 | `appmod/java-upgrade-20260912235322`（**非**主分支，故无需新建分支） |
| 钩子 | 仓库**无生效的 git 钩子**（`.git/hooks/` 下只有 `*.sample`），故 `--no-verify` **未跳过任何东西** |

**提交前的最后一项核对**：`git diff backend/pom.xml` 的 `minimum` 计数为 **0** —— 证明 T035 对阈值的临时改动**未残留**在待提交内容里，阈值仍为 **0.73**。

> **【订正，2026-09-13，原文保留】** 上表此前记的哈希是 `25928d0`。该值**已被 `--amend` 取代**：提交后为把本文件（T036/T041 的实施记录与勾选状态）一并纳入同一提交而做了 amend，哈希随之变为 **`c3256a1`**。**这个订正本身说明了一个可复用的教训**——**不要在 amend 之前记录提交哈希**：任何一次 amend 都会改写它，而"把哈希写进被提交的文件"必然自指，因此**只能是"先定稿、再提交、最后单独补记哈希"**。本次即按此处理：本行是对主体提交的**事后补记**，落在紧随其后的小提交里。
