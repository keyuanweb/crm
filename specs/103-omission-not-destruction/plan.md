# 实施计划（103）

**输入**：[spec.md](./spec.md)（31 条 FR / 7 条 SC / 10 条非目标 / 6 条边界）、[research.md](./research.md)（两级盘点与两条阻塞发现）

---

## Constitution Check

| 原则 | 本项如何满足 |
|---|---|
| **一 契约优先的 API 设计（不可协商）** | **无端点变更、无请求/响应结构变更**。变更的是**保存路径的补偿行为**，而 056 的契约对「省略」**零约定**（`research.md` §3 的 grep 零命中）⇒ 登记在 [contracts/omission-restore.md](./contracts/omission-restore.md)，**056 全部工件一字不改**（对其 `git diff` **必须为零**）。⚠️ 上游 102 把该缺陷称作「056 契约覆盖的行为」**过宽**，本项**不复述**该理由（契约 §1 已逐字纠正）。 |
| **二 测试与证据** | 每条 FR 指向一条用例（U1–U6 / I1–I5 / 前端两条）或一次定向破坏（D1–D11）；**SC-006 要求那条钉住式用例本身可被证伪**（D9），否则它是一条没有判据看着的绿。 |
| **三 单一判据源** | 受保护字段的判据写成 `!EDITABLE`，与 `support/FieldMaskPlanner.protectedKeys`（`:58-67`，javadoc「往严的一侧倒」）**同口径**。**本批只有一个消费者**，故以**私有孪生**形式落在 `CustomFieldService` 内——**不**提升为公开 API（单调用点的公开访问器是投机 API）。 |
| **四 不可逆操作需批准** | 本批**零迁移**（无 DDL、无数据迁移）；**不编辑任何已应用的迁移**；**不碰 `.specify/feature.json`**；**不跑任何 `/speckit-*`**。 |
| **五 登记与留痕** | 缺陷类的全类盘点落 `research.md`；债务落 `specs/roadmap.md`（**只写在 `research.md` 里等于没登记**）；交付边界落根目录 `DELIVERY_SCOPE.md`。 |

**无豁免、无 `Complexity Tracking`。**

---

## 结构决策

### D1 受保护集合的判据：`!EDITABLE`，**不是**并列枚举两值

```java
/** 该角色在本实体上**受保护**的字段 id（HIDDEN ∪ READ_ONLY）——先删后插之后须按库中原值回补。 */
private Set<Long> protectedFieldIds(String roleCode, String entityType) {
  return fieldPermissionService.permissionsForRole(roleCode, entityType).entrySet().stream()
      .filter(e -> !FieldPermissionService.PERM_EDITABLE.equals(e.getValue()))
      .map(Map.Entry::getKey)
      .collect(Collectors.toSet());
}
```

- **为什么是 `!EDITABLE`**：仓里已就**同一个问题**（哪些字段受保护）做出过裁决——`FieldMaskPlanner.java:58-67`，其 javadoc 逐字「判据写成 `!EDITABLE` ……**往严的一侧倒**」。此处若改为枚举 `HIDDEN || READ_ONLY`，就是给同一问题造**第二套、会分叉的**政策：将来若多出第四种权限值，两处会给出**不同**答案。一致性优先，且它恰好是**更安全**的方向：未知值 ⇒ 受保护 ⇒ 被回补，**绝不静默删除**。代价是未知值也**不可被省略清除**——可接受，且是正确的失败方向。
- **为什么私有且相邻**：本批它**只有一个调用点**（`saveValues`）。`readValues` / `readValuesBatch` / `dropHidden` **必须保持 HIDDEN-only**（FR-005）。孪生相邻（紧跟 `hiddenFieldIds:73-78`）让读者一眼看到两者差别**与原因**。
- **为什么不落 `FieldPermissionService`**：那会让单元测试必须 stub `permissionMapper.selectList`（**类内调用打不了桩**），而现有脚手架 `CustomFieldServiceTest.asRole:191-196` 桩的正是 `permissionsForRole` ⇒ 私有孪生让现有测试脚手架**零改动**可用。**若将来出现第二个消费者再提升，此理由写进 javadoc。**

### D2 回补块：**谓词放宽 **加上** 已提交 id 排除**（缺一不可）

**这是本批最关键的机制发现**（`research.md` §0.1）：只放宽谓词会**每次 PUT 500**。

```java
Set<Long> protectedIds = protectedFieldIds(roleCode, entityType);
if (!protectedIds.isEmpty()) {
  Set<Long> submittedIds = values == null ? Set.of()
      : values.stream()
          .filter(v -> v.getFieldId() != null && StringUtils.hasText(v.getValue()))
          .map(CustomFieldValueDTO::getFieldId).collect(Collectors.toSet());
  for (Map.Entry<Long, String> e : existing.entrySet()) {
    if (!protectedIds.contains(e.getKey())
        || submittedIds.contains(e.getKey())
        || !StringUtils.hasText(e.getValue())) {
      continue;
    }
    /* 原样构造 kept 并 valueMapper.insert(kept) */
  }
}
```

- `submittedIds` 的过滤条件**逐字对齐主循环 `:241` 的跳过规则**（`fieldId == null || !hasText(value)`）。在可达的用例里两套定义重合，但**与主循环完全一致**才是可辩护的选择。
- `!StringUtils.hasText(e.getValue())` **继续跳过**：镜像插入侧 `:241` 的规则 ⇒ 空白原值保持「无行」的同一种状态，不因本批多出一行空记录。
- `existing` 在 `:226-231` **已算好并复用**，不重查。
- **顺序**：回补在 `valueMapper.delete`（`:235`）**之后**。本批**没有** 102 那种 `apply` 内的 `null→0` 强转要盖，故**无跨相位顺序风险**——但 D5 仍专杀「顺序无关」的错觉。

### D3 不破坏 EDITABLE 的清空能力（**本项最重要的不变式**）

`permissionsForRole` 对 ADMIN 返回 `Map.of()`、对未配置的行**不出现** ⇒ 未配置字段**不在受保护集合里** ⇒ 其省略**照旧删除**。

这是客户端清除自定义字段的**唯一手段**（`frontend/src/utils/customField.ts:11` 跳过空值）。**反方向的用例（U4 / I3）与定向破坏 D3 是本 fix 的核心风险控制**——一个粗心的「多回补」就会把「清空」这个能力永久废掉。

### D4 读路径**不动**（HIDDEN-only）

`readValues` / `readValuesBatch` / `dropHidden` **必须**保持只挡 HIDDEN。READ_ONLY 的值**必须照常下发**——056 的 422 只针对「修改已有值」，客户端**看得见**才谈得上「原样回传」。若把读路径接到 `protectedFieldIds` 上，回传将**不可能发生**，056 的 422 语义随之**不可达**。D6 专杀这个方向。

### D5 LEAD `ownerId`：**无生产代码改动**（分支 A，已实测）

`research.md` §1 的读数：`ownerId` **被保留**（T0 全绿）⇒ 第二条修复是**非缺陷**。

- **不落**任何 `LeadService` 改动；那条用例**原地转为钉住式用例**。
- **若将来翻转为「被清空」**，处置是**一行守卫** `if (req.getOwnerId() != null) { lead.setOwnerId(req.getOwnerId()); }`。
- **为什么不是 102 的 `capture → apply → restore` 形态**：那个形态的三条理由**没有一条可迁移**——(i) 它守的是由**角色相关**谓词决定的约 11 个字段，而 `ownerId` **无条件、与角色无关**（`BuiltinFieldRegistry` 只覆盖 CUSTOMER + OPPORTUNITY，FLS 配置**根本点不到 LEAD**）；(ii) 它必须盖过 `apply` 内的 `null→0L` 强转，而 `setOwnerId(null)` **无强转**；(iii) 它有**四个**调用点。为一个字段再造一个守卫类还会给 jacoco 分母**加一个类**，并重新引入 102 需要专门 D 行去管的顺序依赖——**收益为零**。**且单行守卫更合本仓体例**：`apply` 上方三行已经用同一写法守着 `source` / `status` / `score`。
- **`create` 语义不得改变**：`create` 在 `apply` **之后**读 `lead.getOwnerId() == null` 决定默认负责人（`LeadService.java:183-190`）⇒ 若日后落地守卫，**必须**有用例钉住「非 ADMIN ⇒ 自己；ADMIN ⇒ 池」（D11）。

### D6 前端：只消费既有标记，不做格式改造

- `permission` 声明为**可选**（分页端点不下发，`research.md` §4.1）⇒ 消费点一律 `?.`。
- **不动** `toCustomFieldPayload`、**不**把 READ_ONLY 换成只读展示组件：两者都会改变回传值的**类型/格式**，而 `validateWrite` 拿提交值与库中**原始字符串**比对 ⇒ 会把今天的绿回传变成 `422 FIELD_READ_ONLY`（债务，`research.md` §6-3）。
- **加零个 i18n 键**：disabled 控件自明；若要「只读」提示，代价是两处新键 + `PROJECT_FEATURES.md` 的 i18n 数字联动，**不值得**。

### D7 为什么**刻意不产** `data-model.md`

102 产它，是因为 V91 改了列 / 可空性 / 索引，且 H2 与 MySQL 的读**本身**是证据。

**103 零 schema 变更**：无迁移、无实体改动、无新索引。本批唯一的数据形状事实是**既有的** `uk_field_entity_value`，其**权威住处**是 `V39__custom_field_value.sql:13`（+ `schema-h2.sql:740` 镜像）。

把它抄进 `data-model.md` 等于给一个**已有住处的数字造第二个家**——正是本仓严打的「一个数字住在好几个地方」。⇒ 索引读数写在 `research.md` §0.1 并**并列两处 file:line**。

**本文件的这一节是刻意写下的**：`data-model.md` 的**缺席**否则会被读成**漏做**。

---

## Project Structure

### 后端（唯一的行为变更）

| 文件 | 改什么 |
|---|---|
| `backend/src/main/java/com/crm/service/CustomFieldService.java` | 新增 `protectedFieldIds`（D1，紧跟 `hiddenFieldIds:73-78`）；改写回补块 `:252-265`（D2）；改写 `:252-253` 的注释（含四件事，且「看不见不等于该被删除」**留下并扩展**为「看不见 / 改不动，都不等于该被删除」） |

**调用点不改**：`CustomerService:354/385`、`LeadService:197/248`、`OpportunityService:135/171`、`TicketService:181/202` —— 修复在 `saveValues` 内，**实体无关**。

**新增类：零**（不新增 jacoco 分母条目）。

### 前端

| 文件 | 改什么 |
|---|---|
| `frontend/src/types/customField.ts` | `CustomField` 加**可选** `permission?: { hidden: boolean; readOnly: boolean }` + 一行注释指向 `types/fieldPermission.ts` 说明**不可统一**（D6） |
| `frontend/src/components/CustomFieldItems.tsx` | HIDDEN **不渲染**；READ_ONLY 加 `disabled` |
| `frontend/src/hooks/useCustomFieldFilters.ts:13` | 过滤 HIDDEN |
| 同目录新增两个 `*.test.tsx` / 用例 | 见 §验证 |

### 测试

| 文件 | 改什么 |
|---|---|
| `backend/src/test/java/com/crm/service/CustomFieldServiceTest.java` | U1–U6 |
| `backend/src/test/java/com/crm/integration/FieldPermissionIT.java` | **扩展**（I1–I5），**不新开 IT 类** |
| `backend/src/test/java/com/crm/integration/LeadIT.java` | `omittedOwnerIdSurvivesLeadUpdate`（**T0 已写**，原地转为钉住式用例） |

### 工件与登记

`specs/103-omission-not-destruction/`：`spec.md` / `plan.md` / `research.md` / `contracts/omission-restore.md` / `quickstart.md` / `falsification-evidence.md` / `tasks.md` / `checklists/requirements.md`（**八件**；`data-model.md` 刻意不产，见 D7）。

---

## 一个数字住在好几个地方 —— 落点表

**103 零迁移 ⇒ `INSTALL.md` 不动**（其迁移列表与「V1~V91 / 90 个」正文照旧）。**这是相对 102 的落点减少**，须**明写**，免得读者去找一处并不存在的遗漏。

| 文件 | 改什么 | 旧值处置 |
|---|---|---|
| `specs/README.md` | 模块表加 103 行（**同样 5 列**）；`:3` 版本行；编号说明段（仍「`069` 未创建」，并写明 **103 无需例外条款**）；**迁移表不加任何行**——在正文**明写**这一点 | 旧值逐字保留 |
| `specs/roadmap.md` | `## 当前进度` 加 103 行（立项**刻意不预勾**）；计数移动；103 行下**债务 blockquote**（`research.md` §6 的 8 条）；`:4` `**最后更新**` | 上一条**逐字保留** |
| `README.md` | 目录树 `specs/` 计数 `001~102` → `001~103`；树加 `DELIVERY_SCOPE.md`；文档索引加指针 | 旧值逐字保留 + 带日期 ⚠️ |
| `PROJECT_FEATURES.md` | 一次重测块：只写 103 真正移动的行（后端测试类、前端单测、Spec 模块 `102 → 103`）；**i18n 不动**（不加键）；**Flyway 迁移不变（明写**，因历次重测块都改过它） | 旧值保留可 grep |
| `CRM_FEATURE_COMPARISON.md` | **很可能不改**——**但须先读 §2.9 与 P0/P1 列表再决定，不得静默跳过** | 判定列与分值**一律不动** |

**必须不动**：`specs/056-field-permission/**`（`git diff` 为零）、`specs/102-builtin-field-permission/**`（那条过宽理由逐字留着）、`specs/101-*/**`、`db/migration/**`、`schema-h2.sql`、`.specify/feature.json`、`pom.xml` 的 jacoco 排除项。

---

## 验证

### 用例清单（唯一的行为层证据）

**单元** `CustomFieldServiceTest`（复用 `asRole(roleCode, perms):191-196` / `stored(fieldId,value):181-188` / `saveValuesKeepsHiddenFieldValue:232-253` 的插桩捕获形态）：

| # | 用例 | 断言 | 钉住 |
|---|---|---|---|
| U1 | `saveValuesKeepsReadOnlyFieldValue` | perms `{1L: READ_ONLY}`、提交字段 2 ⇒ **两次** insert，其一 `1L → 原值` | SC-001 |
| U2 | `saveValuesRestoresProtectedFieldWhenNothingSubmittedAtAll` | `values == null` 与 `List.of()` 两种入参**都**回补 | 回补不以非空载荷为前提 |
| U3 | `saveValuesDoesNotDuplicateEchoedReadOnlyValue` | 提交 `[{1,"v"}]` 且库中 `1L="v"` ⇒ id 1 **恰好一行** | **SC-002 / D2 的回归** |
| U4 | `saveValuesStillClearsOmittedEditableField` | id 2 未配置 ⇒ 提交只含 id 1 时 **id 2 无 insert** | **SC-003（反方向）** |
| U5 | `saveValuesTreatsUnknownPermissionValueAsProtected` | perms `{1L:"SOMETHING_ELSE"}` ⇒ id 1 **被回补** | SC-005（`!EDITABLE` 的方向） |
| U6 | `saveValuesRestoresNothingWhenNoPermissionConfigured` | perms `Map.of()` ⇒ 省略**照旧清空** | fail-open 默认 |

**集成** —— **扩展 `FieldPermissionIT`，不新开 IT 类**（它已拥有自定义字段 FLS 的端到端故事与 `createCustomField:20-36`、`createSalesUser:38-51` 两个助手，且已示范 `fieldId` 形态的请求体 `:85-87`；`BuiltinFieldPermissionFixture` 发的是 `fieldKey`，**此处不可复用**，需加本地 `configureCustom`）。

⚠️ **不得加 `@Transactional`、不得加 `@TestMethodOrder`**（每方法 `resetDatabase()` 依赖 IT 类**无序**）。**只以 ADMIN 读回判定销毁**（`application.yml:22` 的 `default-property-inclusion: non_null` 让缺键与 null 不可区分，且受限调用方**看不见**它要检查的字段）。

| # | 用例 | 断言 |
|---|---|---|
| I1 | `omittedReadOnlyCustomFieldValueSurvivesUpdate` | SALES 对 A 持 READ_ONLY；建时 A="只读原值"、B="旧"；只改 B 的 PUT ⇒ 200；**ADMIN 读回 ⇒ A 原值 且 B="新"**（正对照：这次 PUT 确实生效） |
| I2 | `echoingReadOnlyCustomFieldValueDoesNotFail` | A **原样回传** + B 改动 ⇒ 200，且 A **恰好一个值**（无重复、无 500）。**唯一能端到端抓住 §0.1 陷阱的那条** |
| I3 | `omittedEditableCustomFieldValueIsStillCleared` | 无任何 FLS 配置；建时含 A、B；只提交 B ⇒ ADMIN 读回 **A 已消失** |
| I4 | `readOnlyCustomFieldValueIsStillVisibleToTheRestrictedRole` | 读路径必须 HIDDEN-only：SALES GET **看得见 A 的值** |
| I5 | `requiredProtectedCustomFieldSaveIsRejected` | **必填**字段配 READ_ONLY 后被省略 ⇒ 422 `CUSTOM_FIELD_REQUIRED`（来自 `:218-222`，**早于**回补）。**标注为边界，不是 fix** |

⚠️ 中文断言**必须**显式 `StandardCharsets.UTF_8`（本仓有 ISO-8859-1 假红先例）；能断 `error.code` **优先断 code**。

**前端**（与组件同目录）：`CustomFieldItems.test.tsx`（桩 `fetchFieldDefinitions` 返 HIDDEN / READ_ONLY / EDITABLE 各一 ⇒ HIDDEN **不渲染**、READ_ONLY 控件 `disabled`、EDITABLE 两者皆非）；`useCustomFieldFilters` 用例（HIDDEN 不产 `cf_<id>` 列；`permission` 为 `undefined` 时**仍产列**——可选链分支）。

### 定向破坏（D 系列，逐条**观测到转红**）

**破坏表不是占位符**：「该改变哪条行为」在**开工前定稿**，留空的只有读数。

| # | 破坏 | 该红 |
|---|---|---|
| D1 | 回补谓词缩回 `HIDDEN`（103 之前的代码） | U1、U2、I1（U3 **应保持绿**） |
| D2 | **去掉 `submittedIds` 排除** | U3、I2 —— 预测 `DuplicateKeyException` 撞 `uk_field_entity_value` ⇒ 500。**若不变红，则唯一索引/插入路径与此处记录的机制不符** ⇒ **逐字记录该证伪结果**，不得回填成「验证通过」 |
| D3 | **反方向过度回补**：把每个 id 都当受保护 | U4、I3 |
| D4 | 谓词改成枚举 `HIDDEN \|\| READ_ONLY` | U5 |
| D5 | 回补块挪到 `valueMapper.delete` **之前** | U1、I1（证明回补**在删除之后**，而非仅仅存在） |
| D6 | 读路径接上受保护集合（`dropHidden` → `protectedFieldIds`） | I4 + 056 的 `fieldPermissionFlow` |
| D7 | 前端：重新渲染 HIDDEN / 去掉 `disabled` | 两个 vitest 用例 |
| D8 | 前端：去掉 `useCustomFieldFilters` 的 HIDDEN 过滤 | 筛选列 vitest |
| D9 | **让 null 也能写库**（`Lead.ownerId` 加 `updateStrategy = FieldStrategy.IGNORED`） | `omittedOwnerIdSurvivesLeadUpdate`。**这是 SC-006 的判据**：若**不变红**，说明那条钉住式用例**没有判据看着**，须如实记录 |
| D10 | LEAD 的 `create` 默认逻辑改经守卫（若日后落地守卫） | create 两条路径的用例（非 ADMIN ⇒ 自己；ADMIN ⇒ 池） |
| D11 | LEAD 的 `apply` 改成**无条件**写 `ownerId` 且 `update` 用 `update(entity, wrapper)` 全字段覆盖 | `omittedOwnerIdSurvivesLeadUpdate`（与 D9 互为佐证的不同破坏面） |

⚠️ 每条**先写一句「它该改变哪条可观察行为」**，跑完核对那条行为**确实变了**——没变就是**空操作**；看到红先读**是不是手段的红**（CRLF / spotless / 编译错都不是目的的红）。**禁止 `git checkout` 还原**（用 `cp` 回写 + `git diff --quiet -- <file>` 判逐字相等）。**就地改一律用 Edit 工具**（本仓 Java 源是 CRLF）。探针残留判据用唯一标记 `留痕后还原`。

### 门禁

```bash
cd backend && mvn -B -Djava.version=21 spotless:apply
cd backend && mvn -B -Djava.version=21 verify
ls backend/target/jacoco.exec
cd frontend && pnpm run i18n:check && pnpm run lint && pnpm run typecheck && pnpm run ui:check && pnpm run zh:check
```

⚠️ **`-Djava.version=21` 是本批的一处环境偏离**（`research.md` §5）：工区里有一处**不属于本批**的未提交改动 `backend/pom.xml` 把 `<java.version>` 改成了 `25`（属 `appmod/java-upgrade-*` 线），而 **JDK 25 下 spotless 必崩**（`NoSuchMethodError` on `Log$DeferredDiagnosticHandler.getDiagnostics()`）、**JDK 21 下 release 25 编译不过** ⇒ 两头都堵。处置：**不碰那处改动**（章程），改以**命令行覆盖该 property**（磁盘零改动）。**它不碰 `-DargLine`，故 JaCoCo 不受影响。**

- **判据**：`jacoco:check` 必须打印「All coverage checks have been met.」；前端五道全绿且 `i18n:check` 的键/行数**实测值**与 `PROJECT_FEATURES.md` 一致；`zh:check` 的硬编码中文台账**不得增加**。
- **交付态读数只取那一次完整 `verify`**，门禁跑完**不再跑 Maven**（会覆盖 `jacoco.exec` 使交付块读数变成假话）。
- 定向不跑全量：`pnpm exec vitest run <file>`；后端 `-Dtest='A,B'` 逗号分隔。

---

## 提交拆分（每次可独立回退）

| # | 提交 | 内容 |
|---|---|---|
| C1 | `docs(103): 立项` | 八件工件 + 登记（`specs/README.md` 103 行与编号说明段、`specs/roadmap.md` 103 行 + 计数 + 债务 blockquote）。**不含任何代码**。**T0 的实测结果写在这里**（`research.md` §1 / `spec.md` §1.3） |
| C2 | `feat(103): 自定义字段受保护值回补` | `protectedFieldIds` + 回补块重写（**含 `submittedIds` 排除**）+ 注释改写。**本批唯一的行为变更** |
| C3 | `test(103): 自定义字段受保护值用例` | U1–U6 + I1–I5 + LEAD 钉住式用例（`LeadIT` 已含，T0 时写下） |
| C4 | `feat(103): 前端表单与筛选列遵循字段权限标记` | 类型加可选 `permission`、`CustomFieldItems`、`useCustomFieldFilters` + 其用例 |
| C5 | ~~`fix(103): 线索编辑保留负责人`~~ | **分支 A 下不存在此提交**（`ownerId` 实测被保留） |
| C6 | `docs(103): 交付边界说明` | 根 `DELIVERY_SCOPE.md` + README 目录树行 + 交叉引用 |
| C7 | `docs(103): 交付登记与文档订正` | roadmap/README/PROJECT_FEATURES 落点、模块行【交付后记】、`falsification-evidence.md` 实测读数、T0 的最终记录、**环境偏离（`-Djava.version=21`）的登记** |

C2 落地时**允许新代码暂无对应用例**（照 102 先例的**显式红窗**），门禁在 C3 之后跑第一次；C2 只做 `mvn -B -o -q compile` 级自证。

---

## 提交纪律

`ListAgents` 确认无并行会话写同一批文件 → **逐路径 `git add`，禁用 `git add -A` / `git commit -a`**（本仓多会话共用工作区，**同文件里的对方 hunk 也会被扫走**）→ 提交信息末尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`（逐字）。**不把提交自己的哈希写进它携带的文件**；**复选框绝不回填/预勾**（`tasks.md` 只在交付时勾）。**订正不静默**：原文逐字保留 + 带日期 ⚠️ 块，粒度到**每一列**，自查判据是「**旧值仍能被 grep 到**」（排除行首注释后统计，并显式许可测试里的负断言）。`tasks.md` 的 `## 实做订正` 是**三列**。
