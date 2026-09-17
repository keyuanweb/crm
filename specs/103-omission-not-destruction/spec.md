# 103-omission-not-destruction：「省略即销毁」的修一处 + 全类盘点 + 交付边界说明

**类型**：缺陷修复 + 调查登记（**不是**功能新增）

**形制**：改造类（改生产代码），后端 + 前端；**零迁移、零 schema 变更**（与 102 相反）。

---

## 1 由来

### 1.1 上游

102（字段级权限的内置字段）交付后，A 档商业交付评估中暴露出**一类**缺陷：**一个更新路径会销毁客户端合法省略的值**。102 自己修掉了它在「内置字段」那一族上的旗舰实例（商机金额「省略即落 0」），但那只是**一个**实例；同一形状的实现散落在多个 Service 里。

判据分两层，缺一层就会误判：

- **Level 1 —— 这个省略能到库吗？**（决定「是否真销毁」）
- **Level 2 —— 这个省略可达吗？**（决定「是否值得修」）

### 1.2 用户裁决（2026-09-17 ~ 09-18）

| 决定 | 取值 |
|---|---|
| 范围 | **修一处 + 全类盘点登记**——修复已核实可达的一处，其余整理为带证据的工作单，**不修** |
| 交付边界 | **要写一份集中说明**（A 档交付的边界文档，根目录 `DELIVERY_SCOPE.md`） |
| 第二条修复 | 初判「两条一起修」；**开工第一步实测后订正为「非缺陷」**（见 §1.3） |

### 1.3 ⚠️ 立项期的订正：LEAD `ownerId` 主张被实测证伪

**决策链条**：我在计划期向用户陈述「LEAD `ownerId` 编辑即丢，**已验证**、可通过 UI 触达」，并据此请用户把范围从「修一处」放宽到「两条一起修」。

**那条核实是错的**：它只做到**代码路径**（编辑弹窗不渲染 `ownerId`；`LeadService.apply:420` 无条件把它置 null），**没有查持久化行为**。

**机制**：MyBatis-Plus 默认 `NOT_NULL` 策略**跳过 null 字段**——全仓**无 `update-strategy` 覆盖**（实测 `grep` 只命中 `BuiltinFieldWriteGuardIT.java:55` 的一行**注释**），`Lead.ownerId` 上**无 `@TableField(updateStrategy=...)`**，全仓**无 mapper XML** ⇒ `setOwnerId(null)` 根本不进 `UPDATE` 语句。同一机制**本仓在 102 已实测过**：`BuiltinFieldWriteGuardIT.java:52-58` 记载 D4（去掉 `restore`）客户用例**仍绿**，原因正是被置 null 的列压根没进 UPDATE。

**实测（本批 T0，2026-09-18）**：在 `LeadIT` 落一条读数——管理员建线索 → `claim` 生成本人负责人 → `PUT` **省略 `ownerId`**（改 `name` 作正对照）→ 断言响应里 `ownerId` 仍在且值不变。**结果：全绿**（`Tests run: 8, Failures: 0`）。⇒ **`ownerId` 被保留，该主张不成立。**

**处置（分支 A）**：**不落任何 `LeadService` 生产改动**；那条用例**原地转为钉住式用例**（`omittedOwnerIdSurvivesLeadUpdate`）——把「无人观测的巧合」变成「被看着的性质」，防的是将来有人加 `updateStrategy`、改用 `update(entity, wrapper)` 或加 `.set("owner_id", null)`，而**那种回归否则完全观测不到**（102 的 D4 已经证明过这一点）。

⚠️ **本节不得被删改**：它是本批的起点，不是脚注。**订正不静默**——错的那条主张、它错在哪一步、以及改成什么，三者并列在此。

### 1.4 本批修的那一处：自定义字段 READ_ONLY 的省略

056 一期的字段级权限**只作用于自定义字段**，其写侧语义是：提交 HIDDEN ⇒ 422、改 READ_ONLY ⇒ 422。⇒ **写侧唯一的缺口是「省略」**：客户端**看不见**（HIDDEN）或**改不动**（READ_ONLY）就会不提交，而 `CustomFieldService.saveValues` 的**先删后插**会把它一起删掉。

102 已经给 HIDDEN 补了回补（`CustomFieldService.java:252-265`），**但判据只覆盖 `hidden`**——READ_ONLY 被省略时**仍然丢值**。而前端今天**就在渲染 READ_ONLY 字段**（`CustomFieldFormItems` 把所有字段都映射成 `Form.Item`，零处 `READ_ONLY` 处理），**邀请用户去清空一个他改不动的字段**。

⇒ **可达性：UI 全量往返（用户清空即丢）**。这是本批唯一修复的实例。

---

## 2 功能需求

### 2.1 后端：受保护值的回补（本批唯一的行为变更）

- **FR-001** 新增**私有**方法 `CustomFieldService.protectedFieldIds(roleCode, entityType)`，返回该角色在该实体上**受保护**的自定义字段 id 集合（**HIDDEN ∪ READ_ONLY**）。
- **FR-002** 判据**必须**写成 `!EDITABLE`（**不是**并列枚举 `HIDDEN` / `READ_ONLY`）：将来若多出一种权限值，「不是可编辑」一律按受保护处理——**往严的一侧倒**，不会静默放行一个未知权限的字段被删除。此判据须与 `support/FieldMaskPlanner.protectedKeys`（`:58-67`，javadoc 逐字「往严的一侧倒」）**同口径**。
- **FR-003** 回补循环的判据由 `hidden` 改为 `protectedIds`，**并新增「已提交 id 排除」**：提交过的 id 不得再回补一次。
  - **理由（本批最关键的机制发现）**：`custom_field_value` 上有唯一索引 `uk_field_entity_value (field_id, entity_id)`。现有循环之所以安全**纯属巧合**——HIDDEN 字段**永远不可能**被提交（`validateWrite` 直接 422）。放宽到 `!EDITABLE` 会打破这个巧合：**READ_ONLY 的值会被客户端原样回传并活着穿过 `validateWrite`**（`existing.equals(v.getValue())` ⇒ 未变更 ⇒ 不 422），于是主循环插一行、回补循环再插**同一个键** ⇒ `DuplicateKeyException` ⇒ **500**。这正是**前端今天的默认行为**，属常见路径而非边角。
  - **FR-003a** 「已提交」的过滤条件**逐字对齐主循环的跳过规则**（`fieldId == null || !hasText(value)`）。
- **FR-004** **不得**破坏「省略即清空」对 **EDITABLE** 字段的能力：`permissionsForRole` 对 ADMIN 返回空映射、未配置的行不出现 ⇒ 未配置字段**不在受保护集合里** ⇒ 其省略**照旧删除**。这是客户端清除自定义字段的**唯一手段**（`frontend/src/utils/customField.ts:11` 会跳过空值）。**本项最重要的不变式**。
- **FR-005** 读路径（`readValues` / `readValuesBatch` / `dropHidden`）**必须保持 HIDDEN-only**：READ_ONLY 的值**必须照常下发**——056 契约的 422 只针对「修改已有值」，客户端**看得见**才谈得上「原样回传」。不得把读路径接到 `protectedFieldIds` 上。
- **FR-006** 改写 `saveValues` 中那处注释，须含四件事：(a) 缺陷类名「省略即销毁」；(b) 为何含 READ_ONLY；(c) **为何绝不含 EDITABLE**；(d) 重复插入陷阱与索引名。**「看不见不等于该被删除」这句必须留下并扩展**为「看不见 / 改不动，都不等于该被删除」——它是全仓对此政策的**唯一在册表述**。
- **FR-007** `protectedFieldIds` 以**私有方法**落在 `CustomFieldService` 内、与 `hiddenFieldIds` 相邻：本批它**只有一个调用点**，公开访问器是投机 API；私有孪生相邻能让读者一眼看到两者差别**与原因**；且不落在 `FieldPermissionService` 是因为类内调用打不了桩，而现有测试脚手架 `CustomFieldServiceTest.asRole` 桩的正是 `permissionsForRole`。若将来出现第二个消费者再提升，**此理由须写进 javadoc**。

### 2.2 FEM：LEAD `ownerId` 的钉住式用例（**无生产代码改动**）

- **FR-008** 落一条集成用例：编辑线索时**省略 `ownerId`** ⇒ 必须以**响应里的值**（键在**且**等于 claim 之后的值）断言负责人被保留，并以同一次 PUT 改动的 `name` 作**正对照**（若正对照没变，说明这次 PUT 根本没生效，该条会以另一种方式失败而不是假绿）。
- **FR-009** **不得**为它落任何 `LeadService` 生产改动（分支 A）。若将来实测翻转为「被清空」，处置是**一行守卫** `if (req.getOwnerId() != null) { lead.setOwnerId(req.getOwnerId()); }`——**不**复用 102 的 `capture → apply → restore` 形态（该形态的三条理由——角色相关谓词、`null→0L` 强转、四个调用点——**没有一条可迁移**），且**不得**改变 `create` 语义（`create` 在 `apply` **之后**读 `getOwnerId() == null` 决定默认负责人）。
- **FR-010** 该用例必须**可被证伪**：人为让 null 也能写库（给 `Lead.ownerId` 加 `updateStrategy = FieldStrategy.IGNORED`）⇒ 它必须转红。否则它就是一条**没有判据看着**的绿。

### 2.3 前端：消费 056 已下发、但无人消费的权限标记

- **FR-011** **前提（实测）**：`GET /custom-fields/definitions` **已经**下发逐字段权限标记（`CustomFieldService.listByEntity:59-61`）⇒ 本项是**纯前端改动**，**不动后端 DTO / 不动契约**。
- **FR-012** `types/customField.ts` 的 `CustomField` 增加**可选**字段 `permission?: { hidden: boolean; readOnly: boolean }`。**可选**是对一处**既有漂移**的诚实编码：分页端点 `/custom-fields`（走 `toResponse:368-382`）**不设**该字段。消费点一律 `?.` 兜底。形状**就地内联**，**不得**并进 `types/fieldPermission.ts`（那是配置面 API，其 `permission` 是 `'HIDDEN'|'READ_ONLY'|'EDITABLE'` 字符串联合，**不是一回事**）——须加一行注释说明二者不可"统一"。
- **FR-013** `components/CustomFieldItems.tsx`：**HIDDEN 字段不渲染**（`filter(f => !f.permission?.hidden)`）。理由：渲染它只会造成用户**填不了**的必填空项；创建路径上还让用户往看不见的字段里打字（⇒ 422 `FIELD_HIDDEN`）。READ_ONLY 字段的输入控件加 `disabled`。⚠️ **antd `Form` 把值存在自己的 rc-field-form store 里**（`onFinish`/`getFieldsValue` 从该 store 取值），故 `disabled` 控件**仍会提交其值**——这与原生 HTML 表单不同，且**正是我们要的**：回传值与今天逐字节相同。
- **FR-014** **刻意不动** `utils/customField.ts` 的 `toCustomFieldPayload`，也**不**把 READ_ONLY 换成只读展示组件：两者都会改变回传值的**类型/格式**（`customField.ts:16` 会格式化 dayjs 并 `String()` 数字），而 `validateWrite` 拿提交值与库中**原始字符串**比对 ⇒ 格式不匹配会把今天的绿回传变成 `422 FIELD_READ_ONLY`。该脆弱性**登记为债务**。
- **FR-015** `hooks/useCustomFieldFilters.ts` 过滤掉 HIDDEN 字段（`.filter(f => f.enabled && !f.permission?.hidden)`）。这关掉一条**侧信道**：今天 HIDDEN 字段照样生成 `cf_<fieldId>` 筛选列，被 4 个列表页消费。`extractCfParams` 不动。
- **FR-016** **加零个 i18n 键**（disabled 控件自明）。若加「只读」提示文案，代价是 zh-CN + en 两处新键并牵动 `PROJECT_FEATURES.md` 的 i18n 数字——**不值得**。`zh:check` 只数 `StringLiteral`/`NoSubstitutionTemplateLiteral`/`JsxText` 三类节点，**中文注释与 JSDoc 不计数**，故新注释一律用中文。

### 2.4 全类盘点（**登记不修**）

- **FR-017** `research.md` 必须交付**两级判据**（不是一份"怀疑者清单"）：
  - **Level 1（省略能否到库）** 三类：**1a** 先删后插 / blob 整体替换 / 自定义 SQL ⇒ **销毁**（与 null 策略无关）；**1b** `null → 非 null 默认值` 强转 ⇒ **销毁**（写出的是真值，`NOT_NULL` 救不了）；**1c** 无条件 `set(null)` + `updateById` ⇒ **不销毁**（被 `NOT_NULL` 跳过）。
  - **Level 2（省略是否可达）** 三个标签：`UI 全量往返` / `结构性省略（表单不渲染）` / `无 UI 路径（仅 API）`。
  - **精化**：Level 2 只在 Level 1 判定为**销毁**时才咬人——否则结构性省略也不可见。
- **FR-018** 每条盘点项须给 **file:line + 机制 + 后果 + 可达性标签 + 验证状态**。**本会话未实测的项一律标注为未核实**，不得冒充既成事实。
- **FR-019** **1c 类一律写「按机制预测为不成立，需一条读数确认」，永不写「不是缺陷」**——加一个 `@TableField(updateStrategy=IGNORED)` 或改用 `update(entity, wrapper)` 就会翻转，而**全仓没有门禁看着这件事**。
- **FR-020** 须登记该缺陷类**结构性存在**的根因：全仓**零个 `@PatchMapping`**（约 40 个变更端点全是 `PUT`，若干实现的是**穷举替换**语义，而受影响 DTO 字段上**没有 `@NotNull`**）。这也是「全都修」不成其为一个批次规模的**诚实理由**。
- **FR-021** 须登记**另行分类**（不属本缺陷类）的邻近发现 **L-1**：`PUT /leads/{id}` 只要求 `lead:update`（`LeadController.java:120-121`），而 `LeadService.apply:420` **无条件**写 `req.getOwnerId()`（**非 null 值一定进 UPDATE**），专用改派端点却要求 `lead:assign`（`:136-137`）⇒ 持 `lead:update` 者**绕开 `lead:assign` 即可改派**。这是「省略即销毁」的**镜像方向（提交即改派）**，**登记不修**——修它需要产品裁决（忽略提交值 = 移除能力；改要求 `lead:assign` = 新增授权规则）。

### 2.5 交付边界说明

- **FR-022** 新增仓库**根目录** `DELIVERY_SCOPE.md`（与 `README.md`/`INSTALL.md`/`PROJECT_FEATURES.md`/`CRM_FEATURE_COMPARISON.md` 并列），标题「交付边界说明」。**不放 `specs/103-*/`**：它记录的是**产品的交付信封**（面向客户对话），放批次目录里会被读成「103 的工件」、被 104 取代、且想找交付条款的人永远找不到。
- **FR-023** 六节内容：适用范围与失效条件 / 已交付范围 / **明确不在交付范围内** / 部署与运维前提 / 已知缺陷与债务的入口 / 复核方式。
- **FR-024** **「不在交付范围内」的每一行必须带判据列**（file:line、端点或用例名），让读者**复核**而不是相信。
- **FR-025** **全文不含任何快照数字**——无模块数 / 迁移数 / 用例数 / i18n 数，也无模块清单。任何数字都变成**复算命令**或**指向拥有它的文档**。这是**决定性**的防过期规则：**不含数字的文档不会在数字上过期**。
- **FR-026** 「已交付范围」**指向 `specs/README.md` 模块表**而不抄计数；「已知缺陷与债务」**链到 `specs/roadmap.md`** 而**不抄列表**（抄来的列表会烂）。
- **FR-027** 须写明**失效触发项**（开多租户 / 公网暴露 / 签 SLA / 任一「未交付」项实际交付），命中即**改述本文件**，且**改述不删行**。
- **FR-028** 须**自陈盲点**：**没有任何门禁看着这个文件**，故以「复算命令 + 旧值仍可 grep 到」替代门禁。
- **FR-029** ⚠️ 涉及限流时**逐字沿用 100 的口径降级**（「不是抗敌手措施」），**不得升级措辞**。
- **FR-030** `README.md` 的目录树与文档索引须加上它。

### 2.6 契约台账

- **FR-031** 新增 `contracts/omission-restore.md`，按 `specs/101-mail-inbound-honesty/contracts/mail-inbound.md:60` 的**恰好 4 列** `| 维度 | 056 原约定（§2 逐字） | 103 实现 | 不变的部分 |`。
- **FR-032** §2 须**逐字、可 grep** 地收存 056 原文（带 `file:line` 小标题）。
- **FR-033** **方向必须说准**：本项是**登记实现侧的缺口，不是偏离一纸承诺**——056 的契约对「省略/回补」**零约定**（其「保存拦截」节只有两条 bullet：提交 HIDDEN ⇒ 422、改 READ_ONLY ⇒ 422；且 `specs/056-field-permission/**` 全目录 grep `省略|回补|恢复|restore|部分更新|子集` **零命中**）。**102 登记该缺陷时给的理由（「056 契约覆盖的行为」）过宽**——其结论（登记）成立，**理由不成立**；**103 不得复述 102 的理由**。101 的先例只在**形式**上可迁移（台账放新批自己的 `contracts/`、旧 spec 目录不动、对其 `git diff` 必须为零），**理由不同**（101 是 062 写下了**不实行为**；这里**什么都没写**）。**三个方向必须并列写清**——这是本批最易错的段落。
- **FR-034** 引用条款：`.specify/memory/constitution.md:21`（契约不得被静默修改）、`:78`（任何偏差必须明确说明理由并经批准）。

---

## 3 成功判据（可核）

- **SC-001** READ_ONLY 自定义字段被省略 ⇒ 库中**原值仍在**（不是「请求成功」——断的是**值**）。
- **SC-002** READ_ONLY 值被**原样回传** ⇒ **不产生重复行、不 500**（唯一索引陷阱的回归）。
- **SC-003** **EDITABLE** 字段被省略 ⇒ **仍然被清空**（反方向的判据：清空能力是「多回补」会毁掉的东西）。
- **SC-004** READ_ONLY 的值对受限角色**仍然可见**（读路径不被顺手接到受保护集合上）。
- **SC-005** 未知权限值 ⇒ 按**受保护**处理（`!EDITABLE` 的往严一侧倒）。
- **SC-006** LEAD 省略 `ownerId` ⇒ 负责人**保留**；且该用例**可被证伪**（人为让 null 能写库 ⇒ 必须转红）。
- **SC-007** `DELIVERY_SCOPE.md` 内 grep 数字 ⇒ **0 命中**（它的防过期判据）。

---

## 4 非目标（明确不做，且各有理由）

1. **不修**盘点中的其余约 18 处——**登记不修**。理由：全仓零 `@PatchMapping` 是**结构性**根因；逐处修需要产品裁决（哪些省略是「清空」、哪些是「保留」），不是本批能单方面决定的。
2. **不修 L-1**（`lead:update` 可改派）——需产品裁决，且属镜像方向的另一类。
3. **不修必填校验对权限不可见**：`saveValues:218-222` 的必填校验**早于**回补，故**必填 READ_ONLY 字段的省略会先 422**（回补对它不可达）；而**必填 HIDDEN** 字段**任何调用方都无法满足**。登记 + 用一条用例**钉住行为**，**不扩范围**。
4. **不改 056 任何文件**（授权依据见 FR-033）；**不改 102 的 `spec.md`/`research.md`**（它那条过宽理由**逐字留着**，订正写在 103 自己的契约里）。
5. **不改** `V39` / `V64` / `pom.xml` 的 jacoco 排除项（**不得**把新类挪进 `common/**` 躲门禁）。
6. **不引入全局 `@JsonInclude`**；**不动 `toCustomFieldPayload`**；**不加 READ_ONLY 展示组件**（理由见 FR-014）。
7. **不加 i18n 键**；**不动 `.i18n-keys/**`** 与 `frontend/coverage/**`。
8. **不新增迁移**；**不动 `INSTALL.md`**（其迁移列表与「V1~V91 / 90 个」正文照旧）——**这是相对 102 的落点减少，须明写**，免得读者去找一处并不存在的遗漏。
9. **不产 `data-model.md`**：103 **零 schema 变更**（无迁移、无实体改动、无新索引）。本批唯一的数据形状事实是**既有的** `uk_field_entity_value`，其权威住处是 `V39__custom_field_value.sql:13`（+ `schema-h2.sql:740` 镜像）。抄进 `data-model.md` 等于给一个已有住处的数字**造第二个家**（本仓严打的「一个数字住在好几个地方」）。⇒ 索引读数写在 `research.md` 并**并列两处 file:line**；`plan.md` 明写「不产它是刻意，不是漏」。
10. **不碰 `.specify/feature.json`**；**不跑任何 `/speckit-*`**。

---

## 5 边界（如实声明）

1. **只覆盖自定义字段**：内置字段族已在 102 处理（CUSTOMER 7 + OPPORTUNITY 4），本批**不扩实体**。
2. **必填 READ_ONLY 字段的省略不可达回补**（§4-3）——022 早于回补。
3. **回传格式脆弱性**：READ_ONLY 的回传之所以逐字节安全，**只是因为**初始值来自库中的原始字符串。任何「清空 READ_ONLY 字段」或「重排 DATE 格式」的需求都会打破它。
4. **`CustomFieldService.apply:168-175` 是同一缺陷类的另一实例**（定义路径：省略 `required` ⇒ `required` 静默变 0）——**登记不修**，与修复点同文件不同方法。
5. **盘点中 1a/1b 的多数成员未经本会话实测**（只有 `TagService.setCustomerTags`、`CustomFieldService.apply`、`CustomFieldService.saveValues`、`LeadService.apply` 等少数几处被逐行读过）⇒ 一律标注未核实。
6. **LEAD 分支 A 的结论有前提**：它成立的前提是「`updateById` + 默认 `NOT_NULL` 策略 + 无 mapper XML」这三件事同时为真。三者任一被改动，结论失效——而这**正是** FR-010 那条钉住式用例存在的理由。

---

## 6 上游与落点

本批**不在 `CRM_FEATURE_COMPARISON.md` 上闭合任何缺口、也不翻转任何判定**（它记的是功能对照，本批是缺陷修复与交付边界）。但**须先读 §2.9 与 P0/P1 列表再决定**，**不得静默跳过**；若盘点的发现触及某行，加带日期 ⚠️ 订正并**判定列与分值一律不动**（102 的规矩）。
