# 研究报告（103）

**口径**：★ = **本会话逐行自测**；☆ = 规划期报告、**本会话未逐行核实**（写字时须先核实，或如实标注未核实）。

---

## 0 两条阻塞发现（先读这两条）

### 0.1 只放宽回补谓词会让**每次 PUT 失败**——谓词不是全部

> ⚠️ **2026-09-18 订正（由本批定向破坏 D2 实测）**：本节标题与末句原写 **「…会让每次 PUT 500」/「…⇒ 500」**。
> **实测：机制对、状态码错。** 去掉 `submittedIds` 排除后跑 `FieldPermissionIT.echoingReadOnlyCustomFieldValueDoesNotFail`，
> 抛的**确实是** `DuplicateKeyException`（日志 `Type = org.springframework.dao.DuplicateKeyException`，撞的确实是
> `uk_field_entity_value`），但 `exception/GlobalExceptionHandler.java:130-138`（063 起的唯一键冲突出口）把它渲染成
> **409 `DUPLICATE_KEY`** —— IT 的读数逐字是 `Status expected:<200> but was:<409>`，**不是 500**。
> **结论一字不变**：少了这个排除，原样回传 READ_ONLY 值的 PUT **一律失败**，故排除依旧不是可选优化。
> **旧值「500」逐字保留在本节的标题与末句里**（判据：`grep -n "500" research.md` 仍应命中它们）。
> 同批订正的落点还有 `spec.md`（2 处）、`quickstart.md`（1 处）、`plan.md`（2 处）、
> `falsification-evidence.md`（D2 行）、以及 `CustomFieldService.java` 的对应注释；`tasks.md` §实做订正 第 3 行。

`custom_field_value` 上有**唯一索引** `uk_field_entity_value (field_id, entity_id)`，**两处都有**（★）：

- `backend/src/main/resources/db/migration/V39__custom_field_value.sql:13` —— `` UNIQUE KEY `uk_field_entity_value` (`field_id`, `entity_id`), ``
- `backend/src/test/resources/schema-h2.sql:740` —— `CREATE UNIQUE INDEX uk_field_entity_value ON custom_field_value (field_id, entity_id);`

`CustomFieldService.java:252-265` 的回补循环遍历 `existing` 的**每一行**并插入保留下来的那些。今天它安全**纯属巧合**：HIDDEN 字段**永远不可能**被提交（`validateWrite` 直接 422），所以**被回补的 id 绝不会同时被主循环插过**。

把谓词放宽到 `!EDITABLE` 会打破这个巧合，因为 **READ_ONLY 的值会被客户端原样回传、并活着穿过 `validateWrite`**（`:279-307` 的判据是 `existing == null || !existing.equals(v.getValue())` ⇒ 值相同 ⇒ 未变更 ⇒ **不 422**）⇒ 主循环在 `:244-249` 插一行，回补循环再插**同一个 `(field_id, entity_id)`** ⇒ `DuplicateKeyException` ⇒ **409 `DUPLICATE_KEY`**（**破坏实验前本句写的是「500」，订正见上**）。

**而这是常见路径，不是边角**：前端今天就在渲染 READ_ONLY 字段并回传其值（§4.2）。

⇒ **修复 = 谓词放宽 **加上** 「已提交 id 排除」，缺一不可。** 该排除有自己的用例（U3 / I2）与定向破坏行（D2）。

### 0.2 LEAD `ownerId` 是**非缺陷**（预测 + 实测，见 §1）

MyBatis-Plus 默认 `NOT_NULL` 策略**跳过 null 字段**所以不进 `UPDATE`。证据链（全部 ★）：

| # | 事实 | 坐标 |
|---|---|---|
| a | 全仓**无** `update-strategy` / `FieldStrategy` 覆盖 | `grep -rn` 扫 `backend/src/main` 与 `backend/src/test`，**唯一命中是一行注释** `BuiltinFieldWriteGuardIT.java:55` |
| b | `Lead.ownerId` 上**无** `@TableField(updateStrategy=...)` | `entity/Lead.java:31-32`（仅 javadoc「负责人（空=线索池）」） |
| c | 全局配置里**无策略项** | `application.yml:148-158` 的 `mybatis-plus.global-config.db-config` 只有 `id-type` / `logic-delete-*` |
| d | 全仓**无 mapper XML** ⇒ 无自定义 SQL 可绕过策略 | `find src/main/resources -name "*.xml"` → 空 |
| e | `LeadService.update` 只经 `updateById` 落库 | `:233`、`:239`（`grep -n "updateById"` 全量核对过） |
| f | **同一机制在 102 已被实测过** | `BuiltinFieldWriteGuardIT.java:52-58`：D4（去掉 `restore`）客户用例**仍绿**，因为被置 null 的列**压根没进 UPDATE** |

⇒ 预测：`lead.setOwnerId(null)` **不进 UPDATE 的 SET 子句**，列保留原值。

⚠️ **但不得用读代码来翻案**——那正是本条被推翻的方式（§1）。故落一条**读数**。

---

## 1 T0：那条读数（**已执行，2026-09-18**）

### 1.1 设计

在 `LeadIT` 落一条集成用例 `omittedOwnerIdSurvivesLeadUpdate`：管理员建线索（`create` 对 ADMIN **不设** owner，留在线索池）→ `POST /leads/{id}/claim` 把负责人设成当前用户（**造出非空 ownerId**）→ `PUT /leads/{id}` **刻意不含 `ownerId`**、改 `name` 作**正对照** → 断言。

**判据取「响应里的值」而非「键在不在」**：`application.yml` 的 `default-property-inclusion: non_null` 会让 null 字段整个从响应里消失，故「键在**且**等于 claim 之后的值」才**同时**排除「被清空」与「从未设置」两种可能。**正对照**排除「这次 PUT 根本没生效」这种假绿。

### 1.2 结果：**全绿 ⇒ ownerId 被保留 ⇒ 主张被证伪**

```
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 11.87 s -- in com.crm.integration.LeadIT
[INFO] BUILD SUCCESS
```

日志中的请求序列（逐字）：
```
POST /api/v1/auth/login           status=200
POST /api/v1/leads                status=200     ← createLead
POST /api/v1/leads/1/claim        status=200     ← 负责人被设为非 null
PUT  /api/v1/leads/1              status=200     ← 省略 ownerId
```

**闭包**：`data.has("ownerId")` 为真 **且** 值等于 claim 之后的值 ⇒ `setOwnerId(null)` **确实没进 UPDATE**。

### 1.3 处置（分支 A）

- **不落任何 `LeadService` 生产改动**。
- 该用例**原地转为钉住式用例**：把「无人观测的巧合」变成「被看着的性质」。
- **它必须可被证伪**（FR-010）：人为让 null 也能写库 ⇒ 必须转红。否则它是一条**没有判据看着**的绿。该破坏行见 `falsification-evidence.md` D9。

### 1.4 这条订正的元教训（写进 `spec.md` §1.3，不得删）

我先前只核到**代码路径**就下了结论，**没核持久化行为**。同一份代码，**ORM 的写策略**决定了装配与落库之间的差别——**装配层看到 null ≠ 库里变 null**。凡是「省略/置空是否销毁」的论断，**必须落到一条读数**上。

---

## 2 盘点：**两级判据**（本批最有价值的产出）

### 2.1 Level 1 —— 这个省略能到库吗？

全仓无 `FieldStrategy`（§0.2 事实 a）⇒ 分三类：

| 类 | 机制 | 是否销毁 | 成员（★=已读，☆=待核实） |
|---|---|---|---|
| **1a** | 先删后插 / blob 整体替换 / 自定义 SQL | **是**（与 null 策略**无关**——删除或整体覆盖无论如何都发生） | ★ `TagService.setCustomerTags:107-109`、★ `CustomFieldService.saveValues:235-250`（**本批修**）；☆ `SalesOrderService.rebuildPlans:186-194`、☆ `CustomObjectRecordService.update:74-83`、☆ `QuoteService:147-150`、☆ `WorkflowRuleService:153-159` |
| **1b** | `null → 非 null 默认值` **强转** | **是**（写出的是**真值**，`NOT_NULL` 救不了） | ★ `CustomFieldService.apply:168-175`（**定义**路径：`required` 省略 ⇒ 静默变 0）、★ `ProductService.apply`（`standardPrice ⇒ 0L`，坐标待复核）；☆ `TaskService:171-178`、☆ `CallRecordService:164-171`、☆ `MarketingCampaignService:161-168`、☆ `LandingPageService:81-86`、☆ `MailAccountService:105-113`、☆ `DepartmentService:83-86`、☆ `KnowledgeArticleService:130-134`、☆ `OpportunityStageService:220,236`、☆ `SalesOpportunityService:115`、☆ `ContractService:164`、☆ `SalesOrderService:131`、☆ `QuoteService:289`（各需一读：那一行是否施加默认值） |
| **1c** | 无条件 `set(null)` + `updateById` | **否**（被 `NOT_NULL` 跳过） | ★ `LeadService.apply:406-424`（`title`/`phone`/`email`/`ownerId`/`campaignId`/`remark` —— **§1 已实测其中 `ownerId`**）；☆ `TicketService.apply:430`（`assigneeId`）、☆ `ContactService:201-210` |

**1a 的两个已读实例，机制逐字**：

- `TagService.setCustomerTags:107-109`：
  ```java
  customerTagMapper.delete(
      new LambdaQueryWrapper<CustomerTag>().eq(CustomerTag::getCustomerId, customerId));
  if (tagIds != null) { ... }
  ```
  `delete` **无条件**执行、且在 `tagIds != null` 判空**之前** ⇒ 省略/传 null 即**清空全部标签**。**这正是它属 1a 的原因**：与 null 策略无关。

- `CustomObjectRecordService.update:74-83`：`record.setRecordValues(writeJson(values))` 写入的是**非 null 的 JSON 字符串** ⇒ 整个 blob 被替换（**结构上最接近的孪生**）。

### 2.2 Level 2 —— 这个省略可达吗？

**原则（用户批准的范围划分）**：**表单渲染的字段** ⇒ 省略是合法清空；**表单未渲染的字段** ⇒ 省略是**结构性**的。

**精化**：该原则**只在 Level 1 判定为销毁时才咬人**——否则结构性省略也不可见。

三个标签：`UI 全量往返` / `结构性省略（表单不渲染）` / `无 UI 路径（仅 API）`。

### 2.3 Level 2 的本会话实测（★）

| 实体 | 编辑入口 | 结构性省略的字段 | 判定 |
|---|---|---|---|
| **LEAD** | `updateLead` | **`ownerId`**（编辑弹窗不渲染） | **结构性省略，但 Level 1 判为 1c ⇒ 不销毁**（§1 实测） |
| **CONTACT** | `updateContact` | 无 —— `customerId` **确实渲染**（`ContactListPage.tsx:269` `name="customerId"`），且 DTO 上是 `@NotNull` | 干净 |
| **TASK** | `updateTask` | 无（载荷含每个表单字段） | 干净（`priority` 需用户**主动清空**） |
| **PRODUCT** | `updateProduct` | 无（`standardPrice` 渲染） | 干净，但另有机制：前端 `Math.round((values.standardPrice ?? 0) * 100)` 把空值**显式**变 `0`（**从不省略**，与后端强转是不同的机制） |
| **TICKET** | **无** | —— | `updateTicket` 已导出但**无任何页面调用**（弹窗是 create-only）⇒ `无 UI 路径（仅 API）` |

⚠️ `ContactListPage` 那条**曾经被我自己的 grep 模式误判过**：`Form.Item name="[a-zA-Z]+"` 漏掉多行写法，一度得出「`customerId` 未渲染」。**grep 模式的边界要自证**——漏掉的那项不会出现在结果里。

### 2.4 根因登记（FR-020）

全仓**零个 `@PatchMapping`**（约 40 个变更端点全是 `PUT`，若干实现的是**穷举替换**语义，而受影响 DTO 字段上**没有 `@NotNull`**）。这是该类**结构性**存在的根因，也是「全都修」不成其为一个批次规模的**诚实理由**：修每一处都要先裁决「这个省略是清空还是保留」。

### 2.5 另行分类（**不属本缺陷类**，FR-021）

**L-1（★ 仅凭读码即可断定，无 ORM 微妙性）**：

- `LeadController.java:120-121`：`PUT /leads/{id}` 只要求 `lead:update`。
- `LeadService.apply:420`：**无条件**写 `req.getOwnerId()` —— **非 null 值一定进 UPDATE**（与 §0.2 的 null 跳过无关）。
- `LeadController.java:136-137`：专用改派端点 `POST /leads/{id}/assign` 要求 `lead:assign`。

⇒ 持 `lead:update` 者**绕开 `lead:assign` 即可改派**。方向与「省略即销毁」**相反**（**提交即改派**）。**登记不修**：修它需要在两件事之间做**产品裁决**——「忽略提交的 ownerId」（= 移除一个现有能力）或「改要求 `lead:assign`」（= 新增授权规则）。

---

## 3 056 对「省略」零覆盖（★，FR-033 的依据）

- `specs/056-field-permission/contracts/field-permission.md:41-44` 的「保存拦截」**只有两条 bullet**：提交 HIDDEN ⇒ 422 `FIELD_HIDDEN`；提交 READ_ONLY（修改已有值）⇒ 422 `FIELD_READ_ONLY`。**两条都要求客户端先提交**，**省略侧没有条款**。
- `specs/056-field-permission/**` 全目录 grep `省略|回补|恢复|restore|部分更新|子集` ⇒ **零命中**。
- 056 的实现注释里**有**政策表述（`CustomFieldService.java:252-253`「看不见不等于该被删除」），但**契约层没有**。

⇒ **102 登记该缺陷时的理由「056 契约覆盖的行为」过宽**——结论（登记）成立，**理由不成立**。103 的准确分类是：**省略侧落在 056 契约的射程之外，103 补的是这半边**。

---

## 4 前端现状（★）

### 4.1 标记已下发、无人消费

`GET /custom-fields/definitions` → `CustomFieldService.listByEntity:59-61` **已经**设 `resp.setPermission(FieldPermissionView.of(p))` ⇒ 前端那一半**不需要任何后端 DTO / 契约改动**。

**反向漂移**：`page()` 走的 `toResponse:368-382` **不设** `permission` ⇒ 分页端点（设置页用）**不下发**该字段 ⇒ 前端类型**必须**声明为可选（FR-012）。

### 4.2 两个消费点

- `components/CustomFieldItems.tsx`：`CustomFieldFormItems` 把**每一个**字段都映射成 `Form.Item`，**零处** `READ_ONLY` / `disabled` 处理 ⇒ 用户被**邀请**去清空一个他改不动的字段。
- `hooks/useCustomFieldFilters.ts:7-29`：只按 `f.enabled` 过滤，为**所有**字段（含 HIDDEN）生成 `cf_<fieldId>` 过滤列 ⇒ **侧信道**。被 4 个列表页消费（`CustomerListPage` / `LeadListPage` / `OpportunityListPage` / `TicketListPage`）。

### 4.3 `toCustomFieldPayload` 的跳过规则

`frontend/src/utils/customField.ts:11` 跳过 `undefined` / `null` / `''` ⇒ **清空输入会完全省略该键**。这既是「省略即清空」是客户端**唯一**清除手段的证明（FR-004），也是 FR-014 那条脆弱性的来源（回传格式必须与库中原始字符串逐字节相同）。

---

## 5 环境事实（★，影响门禁，须交付登记）

**工区里有一处不属于本批的未提交改动**：`backend/pom.xml` 的 `<java.version>21</java.version>` → `25`（属 `appmod/java-upgrade-*` 线）。

**它让门禁命令无法在任一 JVM 下跑完**：

| JVM | 编译 `release 25` | spotless |
|---|---|---|
| JDK 21（`JAVA_HOME` 现值 `jdk-21.0.12.1+1`） | ✘ `release version 25 not supported` | ✓ |
| JDK 25（已装 `jdk-25.0.4.1+1`） | ✓ | ✘ `NoSuchMethodError: 'java.util.Queue com.sun.tools.javac.util.Log$DeferredDiagnosticHandler.getDiagnostics()'` |

**处置**：**不碰那处改动**（章程：不得修改或丢弃另一会话的未提交工作），改以 **JDK 21 + 命令行 `-Djava.version=21`** 覆盖该 property —— `<java.version>` 是 Maven property，命令行可覆盖，**磁盘零改动**，spotless 与编译**都成立**。

⚠️ **该偏离须在交付登记里写明**。它**不碰 `-DargLine`**，故 JaCoCo **不受影响**（本仓已知：`-DargLine` 会静默挤掉 jacoco 代理、让覆盖率门禁空过）。

---

## 6 债务（须落 `specs/roadmap.md`，**只写在本文件里等于没登记**）

1. **唯一索引陷阱**：`uk_field_entity_value` 使得「回补」必须自带「已提交 id 排除」；本批由 U3/I2/D2 三者共同钉住，但**该约束只存在于代码注释与用例里**，无 DB 级或架构级护栏阻止后人再写一个裸回补。
2. **必填校验对权限不可见**：`saveValues:218-222` 的必填校验**早于**回补 ⇒ ① 回补对**必填 READ_ONLY** 字段**不可达**（省略先 422）；② **必填 HIDDEN** 字段**任何调用方都无法满足**（= 该字段一旦被配成 HIDDEN 且必填，该实体的任何保存都会 422）。102 通过在**内置**字段侧**永久排除必填字段**避开了这个坑，但**自定义字段侧没有同样的排除**（`availableFields` 列出**每一个**自定义字段）。**本批只登记 + 用一条用例钉住行为，不修**。
3. **回传格式脆弱性**：READ_ONLY 的回传之所以逐字节安全，**只是因为**其初始值来自库中的原始字符串。`customField.ts:16` 会格式化 dayjs 并 `String()` 数字 ⇒ 任何「清空 READ_ONLY 字段」或「重排 DATE 格式」的需求都会把绿回传变成 `422 FIELD_READ_ONLY`。
4. **分页端点的 `permission` 漂移**：`/definitions` 下发、`/custom-fields`（分页）不下发。前端只能以**可选字段**编码这一漂移。
5. **`CustomFieldService.apply:168-175`**（**定义**路径，同一缺陷类的另一实例）：PUT 一个省略 `required` 的字段定义 ⇒ 必填字段**静默变可选**。**登记不修**。
6. **L-1**（§2.5）：持 `lead:update` 者可改派，绕开 `lead:assign`。**登记不修**（需产品裁决）。
7. **1c 类无任何门禁看着**：本批的结论「被 `NOT_NULL` 跳过」建立在「无 `update-strategy` + 无 mapper XML」之上，而**没有任何门禁**阻止后人加上其中任一项。LEAD 那一条由 FR-010 的钉住式用例**部分**覆盖；`TicketService` / `ContactService` 的 1c 候选**完全没有**判据。
8. **全仓零 `@PatchMapping`**（§2.4）：缺陷类的结构性根因。

---

## 7 本会话**未核实**的清单（不得在别处冒充既成事实）

1. §2.1 表中全部标 ☆ 的 1a/1b/1c 成员——**坐标与机制均为规划期报告**，写字前须逐行读。
2. `ProductService.apply` 的 `standardPrice ⇒ 0L`——机制方向已由前端载荷独立佐证，但**后端那一行的坐标未逐字复核**。
3. `QuoteService:147-150` / `WorkflowRuleService:153-159`：疑为序列化 blob 或先删后插，**须确认 setter 收到的是非 null 序列化结果**。
4. **`ui:check` 的冻结计数**（产品文件数 / `Form.Item` 数）在新增一个同目录 `*.test.tsx` 时**是否移动**——**跑一次看，不要假设**。
5. **本仓是否存在任何 IT 直接 autowire mapper 做行级读取**——在已读过的文件里**没有**发现，故本批一律经 **ADMIN HTTP 读回**判定销毁（这是 102 `BuiltinFieldWriteGuardIT:200-213` 验证过的、**可证明未被掩码**的路径）。
6. `CustomFieldService.saveValues` 的调用点：`CustomerService:354/385`、`LeadService:197/248`、`OpportunityService:135/171`、`TicketService:181/202`（☆）。**修复是实体无关的**，但本批只在 **LEAD / CUSTOMER** 路径上做端到端用例——**须写明**，不得让读者以为四条路径都被端到端覆盖。
