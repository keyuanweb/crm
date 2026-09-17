# 任务清单（103）

> **勾选框纪律**：立项时**全部留空、绝不预勾**；只在**真正做完**时勾。
> **破坏表与用例表在开工前定稿**（`plan.md` §验证、`falsification-evidence.md`），留空的**只有读数**。

---

## C1 `docs(103): 立项`

- [ ] T001 八件工件手写落库：`spec.md` / `plan.md` / `research.md` / `contracts/omission-restore.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`（**`data-model.md` 刻意不产**，理由见 `plan.md` D7）
- [ ] T002 `research.md` 交付**两级判据**盘点（1a/1b/1c + 可达性三标签），每条带 file:line 与**验证状态**（★/☆）
- [ ] T003 `research.md` §0.1 记下唯一索引陷阱（**两处 file:line**）、§1 记下 T0 的**设计与实测结果**、§5 记下环境偏离
- [ ] T004 `contracts/omission-restore.md`：**恰好 4 列**表；§2 逐字收存 056 原文（带 file:line）；§1 **三个方向并列**（085 违反 / 101 偏离 / 103 实现侧缺口）并**逐字纠正 102 的过宽理由**
- [ ] T005 `spec.md` §1.3 记下**那条被证伪的核实**（错在哪一步、机制、实测、处置），**不得删改**
- [ ] T006 `specs/README.md`：模块表加 103 行（**5 列同形**）、`:3` 版本行、编号说明段（写明 103 无需例外条款、**迁移表不加行**）
- [ ] T007 `specs/roadmap.md`：`## 当前进度` 加 103 行（**不预勾**）+ 计数移动 + 债务 blockquote（8 条）+ `:4` 最后更新（**上一条逐字保留**）
- [ ] T008 立项提交（**不含任何代码**）

## C2 `feat(103): 自定义字段受保护值回补`（唯一的行为变更）

- [ ] T009 `CustomFieldService` 加私有 `protectedFieldIds`（紧跟 `hiddenFieldIds:73-78`），判据 `!EDITABLE`，javadoc 写清三件事：同口径于 `FieldMaskPlanner`、**为何不枚举两值**、**为何是私有**（含「将来第二消费者再提升」）
- [ ] T010 回补块 `:252-265` 重写：谓词换 `protectedIds` + **新增 `submittedIds` 排除**（过滤条件**逐字对齐主循环 `:241`**）
- [ ] T011 改写 `:252-253` 注释，含四件事（类名 / 为何含 READ_ONLY / **为何绝不含 EDITABLE** / 重复插入陷阱与索引名）；「看不见不等于该被删除」**留下并扩展**为「看不见 / 改不动，都不等于该被删除」
- [ ] T012 编译自证 `mvn -B -o -q compile`（**允许暂无对应用例——显式红窗**，门禁在 C3 之后跑第一次）
- [ ] T013 C2 提交

## C3 `test(103): 自定义字段受保护值用例`

- [ ] T014 单元 U1–U6（复用 `asRole` / `stored` / 插桩捕获形态）
- [ ] T015 集成 I1–I5 **扩展 `FieldPermissionIT`**（**不新开 IT 类**；加本地 `configureCustom`；**不得** `@Transactional` / `@TestMethodOrder`；只以 **ADMIN 读回**判定销毁）
- [ ] T016 LEAD 钉住式用例 `omittedOwnerIdSurvivesLeadUpdate`（T0 时已写入 `LeadIT`，此处**复核**其断言形态与正对照）
- [ ] T017 C3 提交

## C4 `feat(103): 前端表单与筛选列遵循字段权限标记`

- [ ] T018 `types/customField.ts` 加**可选** `permission` + 一行注释指向 `types/fieldPermission.ts` 说明**不可统一**
- [ ] T019 `CustomFieldItems.tsx`：HIDDEN **不渲染**、READ_ONLY 加 `disabled`
- [ ] T020 `useCustomFieldFilters.ts:13` 过滤 HIDDEN
- [ ] T021 前端用例：`CustomFieldItems.test.tsx` + `useCustomFieldFilters`（含 `permission` 为 `undefined` **仍产列**的可选链分支）
- [ ] T022 前端五道门禁（含 `ui:check` 的冻结计数**实测**——新增同目录测试文件是否移动计数**不得假设**）
- [ ] T023 C4 提交

## C5 `docs(103): 交付边界说明`

- [ ] T024 根目录 `DELIVERY_SCOPE.md`：六节；**全文零快照数字**；每条「不在范围内」带**判据列**；失效触发项写清；自陈「无门禁看着」
- [ ] T025 ⚠️ 限流相关措辞**逐字沿用 100 的口径降级**（「不是抗敌手措施」），**不得升级**
- [ ] T026 `README.md` 目录树加行 + 文档索引加指针
- [ ] T027 自查：`grep -nE "[0-9]+ *个|[0-9]{3,}" DELIVERY_SCOPE.md` ⇒ **预期 0 命中**
- [ ] T028 C5 提交

## C6 验证与留痕

- [ ] T029 定向破坏 D1–D11 逐条执行，**每条先写「它该改变哪条可观察行为」**；`cp` 备份回写还原，判据 `git diff --quiet`
- [ ] T030 **D2 与 D9 的读数按 `falsification-evidence.md` 的特别说明如实记录**（不变红就是不变红，**不得回填**）
- [ ] T031 门禁：`spotless:apply` → `verify` → 核对 `jacoco.exec` 存在 **且 `jacoco:check` 打印了结论行**
- [ ] T032 前端五道
- [ ] T033 收尾检查：`specs/056-field-permission/**` 的 `git diff` **为零**；`specs/102-*/**`、`specs/101-*/**`、`db/migration/**`、`schema-h2.sql` 同样为零；`留痕后还原` **0 命中**

## C7 `docs(103): 交付登记与文档订正`

- [ ] T034 `specs/README.md` 103 行状态改 ✅ + 【交付后记】
- [ ] T035 `specs/roadmap.md` 交付读数 + 勾选 + 最后更新段
- [ ] T036 `PROJECT_FEATURES.md` 重测块（只写移动的行；**i18n 不动**；**Flyway 迁移不变要明写**）
- [ ] T037 读 `CRM_FEATURE_COMPARISON.md` §2.9 与 P0/P1 列表**再决定**改不改（**不得静默跳过**）；若改，判定列与分值**一律不动**
- [ ] T038 登记**环境偏离**（`-Djava.version=21`）与那处**不属于本批**的 `pom.xml` 未提交改动
- [ ] T039 `falsification-evidence.md` 填实测读数；`tasks.md` 勾选 + 填交付块（**不写任何提交哈希**）+ 在「实做订正」里如实记录偏差
- [ ] T040 交付前复跑 `quickstart.md` §6 的复算命令（**不跑 Maven**——门禁读数已取过）

---

## 交付块（**交付时填，不得预填**）

| 项 | 值 |
|---|---|
| 提交 | C1 `docs(103): 立项` / C2 `feat(103): 自定义字段受保护值回补` / C3 `test(103): 自定义字段受保护值用例` / C4 `feat(103): 前端表单与筛选列遵循字段权限标记` / C5 `docs(103): 交付边界说明` / C6 `docs(103): 交付登记与文档订正`（**如实按实做增删**；**不写哈希**） |
| 门禁 | （交付时填：那一次完整 `verify` 的结果 + `jacoco:check` 结论行） |
| `jacoco.exec` | （交付时填：字节数 / mtime） |
| 前端五道 | （交付时填） |
| 定向破坏 | D1–D11 见 `falsification-evidence.md`（**含不变红的行**） |
| 分支 | **A**（LEAD `ownerId` 实测被保留 ⇒ C5 那个 `fix` 提交**不存在**） |

---

## 实做订正（**如实记录与本计划的偏差**，交付时填）

| # | 偏差 | 处置与理由 |
|---|---|---|
| 1 | （交付时填） | （交付时填） |
