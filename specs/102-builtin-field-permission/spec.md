# 102-builtin-field-permission：字段级权限的内置字段（读 + 写 + 导出统一收口）

**Feature Branch**: `102-builtin-field-permission`

**Created**: 2026-09-17

**Status**: 立项（Draft）

**Input**: 用户原话（2026-09-17，四条裁决）：① 配置存储 = **「复用同一张表（推荐）」**——`field_permission` 加 `field_key` 列、`field_id` 改可空，两者**二选一**，不新开表；② 尺度 = **「读+写，客户+商机（推荐）」**，**写路径必须管**；③ 出参过滤落点 = **「单一收口点（推荐）」**（`ResponseBodyAdvice#beforeWrite` + 注册表驱动），**不采用** pull 型逐装配点调用；④ xlsx 导出 = **「含：导出也管（推荐）」**。

---

## 1 由来

### 1.1 上游那两行（原文逐字）

`CRM_FEATURE_COMPARISON.md` 第五节 **P0 第 1 条**（差距清单第一行）：

> | 1 | **字段级权限只作用于自定义字段** | P0 | **仍缺**（作用域未变；**「值泄漏」子项已闭合**，2026-09-16 订正，见下） | **只剩「出参统一过滤」**：~~`readValuesBatch` 读路径修补~~ **已于 2026-09-15 由 `008cbb9` 完成** —— 详情/列表/导出三条读路径均过滤，护栏 `FieldPermissionIT` + `CustomFieldServiceTest`（**证据见 2.9 该行 ⚠️**）。<br>**原文（逐字保留，含被改写的判定列）**：判定列原文为「**仍缺**（且确认有值泄漏）」，动作列原文为「出参统一过滤 + `readValuesBatch` 读路径修补（**泄漏路径见 2.9**）」 |

同一份文档 **2.9 节的 FLS 行**（「字段级权限（FLS）」那一行，判定列 `❌ **仍缺**`）里，写着本项要动的那个东西：

> **仍成立的一半（这才是"仍缺"的理由）**：`V64__field_permission.sql` 的 `field_id BIGINT` 指向 `CustomField.id`、表内**无内置列名** ⇒ 内置字段（客户名/金额等）与 API 出参**确未过滤**。<br>**分值不动**：本条属**判定修正**，非能力增量

以及 2.9 的小结与 4.1 的结论 3（两者都逐字点名「FLS 内置字段」尚未动）：

> **它仍是非 AI 域中最低的一块**：FLS 内置字段、SSO、字段加密、IP 白名单四项一个都没动，而这几项恰是政企与中大型企业采购的一票否决项。

> 剩下的**字段级权限（含确认存在的值泄漏路径）/ SSO / 字段加密 / IP 白名单**四项**一个都没动**，仍是政企与中大型企业采购的一票否决项。

### 1.2 实测复核：那两行准确，且「只作用于自定义字段」的后果比文字更具体

立项勘察读了实现（不是「跑一遍看没红」）：

- **读路径**：`CustomFieldService.readValues` / `readValuesBatch` 会丢掉 HIDDEN 的**自定义**字段值（056 一期 + `008cbb9`）。但**内置字段**（客户电话/邮箱/地址/备注、商机金额…）**完全不经过任何权限计算**——它们由 53 个文件里的手写 `toResponse/copyToResponse/fillResponse` 直接装配进 DTO，Controller 不参与转换。
- **后果**：管理员今天可以把 SALES 角色对「客户电话」配成 **HIDDEN**，配置能存进库、`GET /field-permissions` 也能列出来——但 `GET /customers` **照样把电话发给他**。**配置存在、语义为假**。
- **写路径**：更糟。`CustomerService.apply`（`CustomerService.java:458-470`）**无条件**覆盖每一个字段，`OpportunityService` 的两处金额赋值还带 `req.getX() == null ? 0L : req.getX()` 强转——**客户端省略一个字段 ⇒ 落库为 null 或 0**。若那个字段是被配成 HIDDEN/READ_ONLY 的敏感字段，**客户端看不见它、也就必然省略它、于是每次编辑都把它销毁**。这不是「功能没做」，是**一条会静默销毁数据的通路**，且**今天就能复现**（见 §1.4-5）。

### 1.3 056 是「后续扩展」，不是「冻结契约偏离」——这决定了本项不必写偏差台账

`specs/056-field-permission/` 三处逐字把内置字段写成**待扩展项**：

| 落点 | 原文（逐字） |
|---|---|
| `spec.md:46` | `- 权限仅作用于自定义字段（内置字段不在 v1 范围，后续扩展）。` |
| `spec.md:86` | `- v1 仅作用于自定义字段（内置字段权限后续扩展）。` |
| `plan.md:25`（Constraints） | 同义表述（内置字段权限属后续扩展） |

⇒ **本项是兑现 056 自己留的待扩展项**，方向与 101 相反：101 是「062 的契约写下了不实行为 ⇒ 偏离 + 偏差台账」；本项是「056 明文声明这一块**留到以后做** ⇒ 现在做了」。**故 056 全部工件一个字符不改，也不产生偏差台账**（只在 `research.md` 里**原文回引**上面三处作为授权依据）。

⚠️ **但 056 的 `contracts/field-permission.md` 与 `data-model.md` 与实现之间有一组既有漂移**（契约里的 body 是 `roleId: 2` 而实现是 `roleCode`；契约里的 `PUT /field-permissions/{id}` 在实现里不存在；`data-model.md` 写 `role_id BIGINT` / `field_id BIGINT NOT NULL` 而实现是 `role_code VARCHAR(30)`）。本项**不改 056**，但把这组漂移**用证据登记在 `research.md`**——否则「偏离/未偏离冻结契约」这句话会不踏实。

### 1.4 七条决定形状的实测事实

1. **仓里不存在任何出参收口点**（实测 0 命中，逐项）：`@Json*` 注解 **0**；`ResponseBodyAdvice` **0**；`HandlerMethodReturnValueHandler` **0**；HTTP 层 `WebMvcConfigurer` / `HandlerInterceptor` **0**（唯一 `addInterceptors` 是 `WebSocketConfig:62` 的 STOMP 握手）；`aspect/` 目录**不存在**（切面在 `security/` 下，只有两个 `@Before`，**无 `@Around` / `@AfterReturning`**）；`@RestControllerAdvice` 只有 `GlobalExceptionHandler`（只处理异常，不碰正常响应体）。⇒ 收口点要**新建**，且它必须落在**序列化**这一层。
2. **FLS 是「pull 型」，挂在 Service 方法调用上**：15 个调用点全靠自觉。若照这个范式去逐点调用，等于**再造一批「新增读路径忘了调就静默失效」的点**——而本项要覆盖的正是 53 个装配点 + 导出。⇒ 这是选**单一收口点**的核心理由（用户裁决③）。
3. **权限的真实语义（逐字读 `FieldPermissionService.java:119-147`）**：HIDDEN「**提交即 422**」；READ_ONLY 的判据是 `existing == null || !existing.equals(v.getValue())` ⇒ **`existing == null` 也算 changed ⇒ 422**，即 READ_ONLY =「**必须原样回传，任何差异都 422**」。⇒ **写侧唯一的缺口是「省略」**——客户端看不见（HIDDEN）就会省略，而实体装配无条件覆盖。**「拒绝省略」在语义上不可实现**（HIDDEN 字段客户端根本没有能力提交，提了就 422）⇒ 写侧的正当处置只有**回补原值**。
4. **导出的既有先例是「列在、格空」**：`ExportExecutor.writeHeader` 为**全部**自定义字段写表头，而 HIDDEN 字段的格子取 `getOrDefault(fieldId, "")` ⇒ **空串**，因为过滤版 `readValuesBatch` 里根本没有它们。⇒ 内置字段照此，**不发明第二种读法**（本项不去删列）。
5. **`apply` 无条件覆盖 + 两处 `null → 0L` 强转**（实测）：`CustomerService.apply` 逐字段赋值；`OpportunityService.create:120-121` 与 `OpportunityService.update:145,147` 是 `req.getX() == null ? 0L : req.getX()`。⇒ 被配成 HIDDEN 的 `expectedAmountMin`，一次不带它的 `PUT` 就把它写成 **0**——**一个看起来合法的值**。这是本项的旗舰用例（`spec.md` T11 / `falsification-evidence.md` D5）。
6. **H2 与 MySQL 对「可空列上的唯一索引」同向**（本项实测，见 `data-model.md`）：同 (角色, 实体) 插**两行**内置配置通过 ⇒ 「两个可空列 + 两条唯一索引」的形状**可行**；但同时**双 NULL 行也通过** ⇒ 「恰好一列非空」**没有 DB 级约束**，只能由 `upsert` 保证（登记为债务）。
7. **两条被误当成「必炸点」的假设，实测一条真一条假**：`permissionsForRole` 的 `Collectors.toMap` 遇 **null 键并不 NPE**（实测 `{null=HIDDEN, 1=HIDDEN, 2=EDITABLE}`；null **值**才 NPE、重复键抛 `IllegalStateException`）⇒ 真后果只是 `hiddenFieldIds` 的 Set 里多一个 `null` 元素，**静默不洁而非 500**；而真正会炸的是 `.eq(FieldPermission::getFieldId, null)` 生成的 `field_id = NULL`——**永不匹配** ⇒ `upsert` 第二次保存必然再 INSERT 并撞唯一键，`permissionFor` 则**回落 `EDITABLE`（fail-open）**。

## 2 用户故事

### US1 管理员配得出内置字段，且配置**真的生效**（P1）

作为**管理员**，我希望在字段权限页上能**看见并勾选**「客户电话」「商机金额」这类**内置**字段，配成 HIDDEN 之后，那个角色的列表/详情/公海响应里**真的不再出现**这个值。

**Why this priority**：这是本项的全部理由。今天这个配置**能存、能列、但不生效**——它的语义是假的，而一份「看起来配好了、其实没生效」的数据权限配置，比没有这个功能更危险。

**Independent Test**：ADMIN 用内置 `fieldKey` 配一条 `CUSTOMER + phone + HIDDEN`；SALES 登录后 `GET /api/v1/customers` 与 `/customers/{id}` 的 `phone` **均为 `null`**，而 ADMIN 自己仍能看到真值（正对照）。

**Acceptance Scenarios**：

1. **Given** 已配 `CUSTOMER + phone + HIDDEN`（SALES），**When** SALES 拉客户**列表**，**Then** 每条的 `phone` 为 `null`、其余字段不变。
2. **Given** 同上，**When** SALES 拉客户**详情**（`CustomerDetailResponse`），**Then** `phone` 为 `null`；同响应里 `contacts[].phone`（属 CONTACT 实体）**不受影响**。
3. **Given** 同上，**When** SALES 看**公海**列表，**Then** 同样过滤（公海走的是另一份装配代码 `CustomerPoolService.toResponse`，**不得**漏）。
4. **Given** 同上，**When** ADMIN 拉同样的接口，**Then** `phone` 是真值（**ADMIN 恒不受限**，与自定义字段同口径）。
5. **Given** 未配任何内置权限，**When** 任意角色拉接口，**Then** 与配置前**逐字相同**（默认零影响）。

### US2 省略一个看不见的字段，**不会**把数据销毁（P1）

作为**业务用户**，我提交一次客户/商机编辑时，**看不见的字段在我的请求里必然缺失**——我希望这次提交**不要**因此把它清空（或写成 0）。

**Why this priority**：这是一条**数据销毁通路**，且它**今天就能复现**（§1.4-5）。它比 US1 更硬：US1 是「配置不生效」，US2 是「一次正常编辑销毁数据」。两者都必须在本项关掉，否则本项交付的是半个护栏。

**Independent Test**：SALES 客户 `phone` 原值为 `13800000000`，配 `CUSTOMER + phone + HIDDEN` 后提交一次**不含 `phone`** 的 `PUT`（其余字段合法）⇒ 200，随后以 ADMIN 读库/读接口，`phone` **仍是 `13800000000`**。

**Acceptance Scenarios**：

1. **Given** `expectedAmountMin` 被配成 HIDDEN 且库中原值 `5000`，**When** 提交一次不含该字段的商机 `PUT`，**Then** 落库仍是 **5000**（**不是 `0`**——这是本项最关键的一条断言）。
2. **Given** 同一字段被配成 **READ_ONLY**，**When** 提交**原样值**，**Then** 成功（正对照：READ_ONLY 允许原样回传）。
3. **Given** 同上，**When** 提交**不同的值**，**Then** **422 `FIELD_READ_ONLY`**。
4. **Given** 某字段被配成 **HIDDEN**，**When** 请求里**显式带着**它，**Then** **422 `FIELD_HIDDEN`**（与自定义字段同码）。
5. **Given** `create`（无原值可回补），**When** 该字段被配成 HIDDEN，**Then** 它取默认值/null，**且这是明文声明的语义**（`spec.md` §6），**不是**漏做。

### US3 导出里也不出现（P1）

作为**管理员/合规方**，一个角色被禁看的字段，**不应该**在这个角色的 xlsx 导出里出现——否则「看不见」只是一个界面约定。

**Why this priority**：导出是**唯一绕过 JSON 收口点**的出口（三个入口，其一定时导出**没有请求主体**）。只修 JSON 而漏掉 xlsx，本项就会交付一个「界面上看不到、导出里全在」的假护栏。

**Independent Test**：SALES 触发客户导出 ⇒ xlsx 中 `电话` **列仍在**、该行该格为**空**；ADMIN 导出同数据 ⇒ 有值。

**Acceptance Scenarios**：

1. **Given** `CUSTOMER + phone + HIDDEN`（SALES），**When** 用 `ExportExecutor` 导出客户，**Then** `电话` 列表头在、格空（照自定义字段的既有先例，§1.4-4）。
2. **Given** 同上，**When** 定时导出任务的**业主是 SALES**、而环境主体是 ADMIN/无主体，**Then** **仍按 SALES 掩码**（定时导出路径无请求主体 ⇒ 必须取任务业主的角色）。
3. **Given** 同上，**When** ADMIN 导出，**Then** 有值。

### US4 配置面可用：管理员配得出内置字段（P2）

作为**管理员**，我要能在界面上**选到内置字段**（而不是只知道一个数字 id），并且保存后能看出它是哪种字段。

**Why this priority**：没有这一项，US1–US3 的机制**没有任何人能配置**——功能可达性等于零。今天 `FieldPermissionPage.tsx` 的字段列表来自 `fetchCustomFields`（**只列自定义字段**），表格的「字段」列渲染的是 `dataIndex: 'fieldId'`（**一个数字**）。

**Independent Test**：字段下拉里同时出现内置字段（如「电话」）与自定义字段；保存内置字段后，列表的「字段」列显示**名称**而不是数字。

**Acceptance Scenarios**：

1. **Given** 打开字段权限页，**When** 点「字段」下拉，**Then** 列表含该实体的**全部可配内置字段** + 既有自定义字段，且可区分。
2. **Given** 保存一条内置字段权限，**When** 看列表，**Then** 「字段」列显示**名称**（内置项的名称由后端注册表提供）。
3. **Given** 请求体里 `fieldId` 与 `fieldKey` **同时给**或**都不给**，**When** 调用 `POST /field-permissions`，**Then** **422 `FIELD_PERMISSION_INVALID`**。

## 3 功能需求

### 3.1 存储与迁移

- **FR-001** 新增 `V91__field_permission_builtin_fields.sql`（**不编辑 `V64`**，已应用的迁移永不编辑）：`field_id` 改**可空**、新增 `field_key VARCHAR(64) DEFAULT NULL`；索引由「一条」改为「**两条**」——`uk_field_perm (role_code, entity_type, field_id)` **保留**、新增 `uk_field_perm_builtin (role_code, entity_type, field_key)`。
  ⚠️ **形状承重依赖「唯一索引视多个 NULL 彼此不等」**：MySQL 与 **H2 2.2.224 均已实测同向**（`data-model.md` 记读数）⇒ 每个 (角色,实体) 可以有**多行**内置配置，也可以有**多行**自定义配置，而**两条索引各自只约束自己那一列非空的行**。
- **FR-002** 「`field_id` 与 `field_key` **恰好一个非空**」这条不变式**只由服务端强制**（`upsert`），**不加 DB CHECK**：本仓 89 个迁移里 **零 CHECK**；`SchemaParityIT` 自陈**不覆盖列级漂移**。⇒ **实测已确认「双 NULL 行」在 H2 与 MySQL 都能插入**，故这是一条**真实的**约束缺口，必须登记为债务（`research.md`）。
- **FR-003** `backend/src/test/resources/schema-h2.sql` 三处联动：`field_permission` 的 CREATE 块加 `field_key`、`field_id` 改可空、加第二条唯一约束；受影响行尾标 **`-- V91`**（照头部版本标记约定）；`SchemaParityIT.MIRRORED_MIGRATIONS` 加 `"91"`（**刻意不用区间**）。
- **FR-004** 迁移数在文档里的**全部活落点同批收口**（实测枚举共 **6 处**）：`INSTALL.md:262` 的正文与迁移列表（**补 V90 行**——⚠️ 该行**此前就缺失**，不是本项造成，须带日期 ⚠️ 标明 + **加 V91 行**）、`INSTALL.md:124`、`specs/README.md:3` 的前言版本行、`specs/README.md:150` 的**章节标题**与**迁移表末行**、`PROJECT_FEATURES.md:13`、根 `README.md:156` 的目录树。
  ⚠️ **只加 V91 而不补 V90，会让列表看着像本项跳了号**；⚠️ **只改表行、漏标题**是 082 批踩过的坑（该节自己的 ⚠️ 块记着）；
  ⚠️ 订正块/重测块里的**历史读数一个字不动**；⚠️ 本项**有**一次迁移 ⇒ 与 101（零迁移、落点一个都不动）**相反，必须逐处改**。

### 3.2 内置字段注册表

- **FR-005** 新增 `com.crm.support.BuiltinFieldRegistry` + `BuiltinField`（record）：登记「实体 → 可配内置字段」及其在三类载体（entity / request / response）上的属性名与显示名。**共 11 条**：CUSTOMER 7（`contactPerson` `phone` `email` `address` `remark` `status` `campaignId`）、OPPORTUNITY 4（`expectedAmountMin` `expectedAmountMax` `remark` `status`）。
- **FR-006** 入选规则写进 javadoc，**四条**：① 用户可见的业务数据；② 三侧都有同名载体；③ **不是必填**；④ `null` 不承载额外语义。
  ⚠️ **必填字段永久排除**（`CustomerRequest.name`/`company` 是 `@NotBlank`、`OpportunityRequest.customerId` 是 `@NotNull`、`name` 是 `@NotBlank`）：把它们配成 HIDDEN 会让实体**完全不可编辑**（客户端看不见却必须提交）⇒ **上游那句「内置字段（客户名/金额等）」里的「客户名」这一半本项不覆盖**，必须在 2.9 的 ⚠️ 块里写明（不得让读者以为覆盖了）。
  ⚠️ **`ownerId`/`ownerName` 排除**：`ownerId` 的语义是「空 = 公海」⇒ 置 null 不是「看不见」而是「变成公海」（**说谎**）；`ownerName` 只在两条装配路径上被赋值，遮蔽它会时有时无。
- **FR-007** `@PostConstruct` 自检：对**每个载体类**逐一解析登记的属性名与类型，**任一失败即让 ApplicationContext 启动失败**。⇒ 「属性名打错 = 静默空操作」在本设计下**不可能发生**（这是本设计最关键的护栏，`spec.md` 的 T1 专钉它）。
- **FR-008** 注册表查找用 `Map<String, BuiltinField>`（键 `entityType + ":" + fieldKey`），**不用** `Set<BuiltinField>` / `contains`——避免把 record 的 `equals`/`hashCode` 拉进 jacoco 覆盖率义务。

### 3.3 出参统一过滤（单一收口点）

- **FR-009** 新增 `com.crm.support.FieldMaskingResponseBodyAdvice`（`@ControllerAdvice implements ResponseBodyAdvice<Object>`）：在**序列化前**把该角色 HIDDEN 的内置字段**就地置 `null`**。覆盖 `ApiResponse.data` 与 `PageResult.items` 两层信封、`Collection`/数组/`Map.values()` 递归、以及两个 `DetailResponse` 子类（用 `isAssignableFrom` 匹配载体）。
- **FR-010** 快路径：`principal == null || ADMIN` ⇒ **原样返回**（零成本；ADMIN 恒不受限与 `permissionFor` 同口径）。`principal == null → ADMIN` 是**沿用的 fail-open**，不改口径，但写进 `research.md`。
- **FR-011** 遍历安全：JDK 类型（`java.*`、`String`、枚举、`byte[]`）**直接跳过**（避免 `InaccessibleObjectException`）；**身份集**做环guard + 深度上限。
- **FR-012** **掩码按「字段所属实体」判定，不向子对象传播**：`ContactResponse.phone/email/remark`（属 CONTACT）与 `SalesOpportunityResponse.amount`（属 SALES_OPPORTUNITY）**不**被 CUSTOMER/OPPORTUNITY 的配置遮蔽（否则误伤），同时**如实登记为本项未覆盖**（否则读者会把「金额已过滤」读成 `amount` 也不可见）。
- **FR-013** 掩码形态是**置 `null`**（不删键、**不引入全局 `@JsonInclude`**——那会改动全仓所有响应体的形状）。置 null 与「无值」**不可区分**（无存在性侧信道）；代价是字段呈 `null` 而非缺键（登记为债务）。
- **FR-014** 新增 `com.crm.support.FieldMaskPlanner.plan(roleCode, entityType) → Set<String>`（不可变）作为**唯一判据源**，收口点、`ExportExecutor`、`CustomerExcelService` **共用**同一个它。**不加缓存**（配置一改即生效；代价是每响应 1–2 次查询）。

### 3.4 写侧回补（本项的数据完整性护栏）

- **FR-015** 新增 `com.crm.support.BuiltinWriteGuard`，调用序列**逐字固定**为：`require(id)`（拿到**未被改动**的库中实体）→ `snapshot(entity, entityType)`（仅注册字段）→ `validateBuiltinWrite(roleCode, entityType, req, original)`（422 判定，**必须在 apply 之前**）→ `apply(req, existing)` → `restore(entity, original, maskedKeys)` → `updateById`。
- **FR-016** `restore` **必须**在 `apply` **之后**：`OpportunityService` 的 `null → 0L` 强转发生在 `apply` 之内，**只有后置回补才能盖掉它**（T11 专钉这一条；`falsification-evidence.md` 的 D5 把顺序挪到前面专杀「顺序无关」的错觉）。
- **FR-017** HIDDEN 与 READ_ONLY **都**无条件回补（READ_ONLY 的改动已被 422 挡住 ⇒ 回补等价于保留）。
- **FR-018** 接线 4 处：`CustomerService.create/update`、`OpportunityService.create/update`。`create` **无原值可回补** ⇒ 只做 422 校验，HIDDEN 字段在 create 上**等价于「不可设置」**（**明文声明**，不是漏做）。
- **FR-019** 提交 HIDDEN ⇒ **422 `FIELD_HIDDEN`**；改 READ_ONLY ⇒ **422 `FIELD_READ_ONLY`**（与自定义字段**同码同语义**，不新造码）。

### 3.5 导出（三个入口，同一判据源）

- **FR-020** `ExportExecutor.writeCustomers` / `writeOpportunities` 的内置列接 `FieldMaskPlanner`：**列保留、格置空**（照 §1.4-4 的既有先例，**列头不动**）。
- **FR-021** `CustomerExcelService`（另一套 7 列、**不调** `mask()`）同样接入。
- **FR-022** `ScheduledExportServiceImpl.executeExport` 必须**取任务业主的角色**来算掩码（该路径**无请求主体**）。⚠️ 本项**只**把**字段掩码**接上业主角色；063 的 `mask()` 与 `visibleOwnersOrNull` 在该路径上仍 fail-open ⇒ **混合态必须写明**，不得含混成「定时导出已修好」（登记为债务）。
- **FR-023** 掩码后必须实测：**若某导出路径判断错误，只有导出用例会红**（T15/T16）——JSON 全绿不代表 xlsx 正确。

### 3.6 配置面（让管理员配得出来）

- **FR-024** 新增 `GET /api/v1/field-permissions/available-fields?entityType=X`：返回该实体的**内置 + 自定义**可配字段（`{fieldId, fieldKey, fieldName, builtin}`），复用 `PageResult` 形；**未知 `entityType` ⇒ 422 `FIELD_PERMISSION_INVALID`**（不静默返回空表）。
- **FR-025** `FieldPermissionService.upsert` 重写判定：① `fieldId` 与 `fieldKey` **恰好一个非空**，否则 422 `FIELD_PERMISSION_INVALID`（056 定义了却**从未抛过**的码终于有用途）；② `fieldKey` 必须在注册表中**且属于该 `entityType`**，否则 422；③ 查询**按 identifier 分支**（`.eq(fieldKey)` vs `.eq(fieldId)`）——**不得**用 `.eq(fieldId, null)`（生成 `field_id = NULL`，永不匹配 ⇒ 第二次保存必撞唯一键）。
- **FR-026** 新增 `FieldPermissionService.permissionForKey(roleCode, entityType, fieldKey)`（ADMIN 恒 `EDITABLE`；未配置 `EDITABLE`）与 `builtinPermissionsForRole(roleCode, entityType) → Map<String,String>`；`validateBuiltinWrite(...)`。**不得**让内置查找退化成 `permissionFor(roleCode, entityType, null)`（那会**恒定回落 EDITABLE = fail-open**）。
  ⚠️ **不动 056 的既有方法签名**（`permissionFor` / `permissionsForRole` / `validateWrite`）——改签名会牵动 15 个自定义字段调用点；本项用**并行的方法族**。
- **FR-027** `FieldPermissionRequest`：`fieldId` 去掉 `@NotNull`、加 `fieldKey`（`@Size(max=64)`）；二选一由服务层强制（跨字段规则 Bean Validation 表达不了）。**`permission` 的 `@Pattern` 白名单一字不动**（它已经挡住了非法值 ⇒ 不加冗余的服务层校验，见 `research.md`）。
- **FR-028** `FieldPermissionResponse.fieldName` 补齐赋值（今天只声明、**从不赋值**）；内置项的名称取注册表的标签。
- **FR-029** 前端：`FieldPermissionPage.tsx` 的字段列表改调新端点、表单选项同时支持 `fieldId`/`fieldKey`、表格「字段」列渲染**名称**而非 `dataIndex: 'fieldId'` 的数字；`types/fieldPermission.ts` 加 `fieldKey?`。

### 3.7 登记、契约与订正

- **FR-030** 产 `contracts/field-permission-builtin.md`：给出新的对外行为（新端点、`upsert` 请求体形状、掩码语义），并**逐字收存** 056 契约的原行为与其**既有漂移**作对照。
- **FR-031** 产 `data-model.md`，**只写 V91 的增量**（新列、可空性、第二条唯一索引、不变式的强制位置与 H2/MySQL 实测读数），**不重列** `V64` 已定的 6 列（避免「一个数字住在好几个地方」）。
- **FR-032** 对比文档按「**订正不静默**」改写 5 处（`docs 时间线块` / 2.9 FLS 行 / 2.9 小结 / 4.1 结论 3 / P0 第 1 条）：原文逐字保留 + 带日期 ⚠️ 块，**粒度到每一列**，自查判据是「**旧值仍能被 grep 到**」；**判定列与分值一字不动**。
- **FR-033** ⚠️ **P0 第 1 条的状态列从 `**仍缺**` 改为 `**⚠️ 基本闭合**（102，2026-09-17）`，但不得改成 `✅`**：动作列的字面要求（「出参统一过滤」）**已满足**，而**差距列的名字**（「字段级权限**只作用于自定义字段**」）只被**部分**证伪——2 个实体 / 11 个字段；LEAD/TICKET/ORDER/CONTRACT 等实体的内置字段仍未约束；**必填字段永久排除**。⇒ 停在「基本闭合」是如实。
- **FR-034** 数字落点一并收口（**一个数字住在好几个地方**）：i18n 键数与行数的多处、Spec 模块数的多处、**迁移数的 6 处**（见 FR-004）、根 `README.md:163` 的模块数行（**已腐坏**：写 `99 个功能模块，001~100`，实际应为 100 个 / 001–101）、两份登记（`specs/README.md`、`specs/roadmap.md`）。旧值必须仍可 grep 到。
  ⚠️ 这 6 处迁移落点是**实测枚举**出来的；`plan.md` 初稿只列了 `INSTALL.md` 一处，已在**立项期**改正（未提交、未执行代码）——照初稿做会一次留下 5 处假数字。
- **FR-035** `specs/roadmap.md` 的**债务台账**新增本项的 8 条（见 `research.md` §13）——**债务必须写进台账**，只写在 `research.md` 里等于没登记。

## 4 非目标（明确不做，且各有理由）

1. **不接 CUSTOMER / OPPORTUNITY 之外的实体**（用户裁决的实体范围）。LEAD/TICKET/ORDER/CONTRACT 等的内置字段仍未约束——机制是注册表驱动的，**再登记条目即可**，但本项不扩。
2. **不登记必填字段**（`name`/`company`/`customerId`）。理由见 FR-006：HIDDEN 一个必填字段会让实体**完全不可编辑**。⇒ 上游点名的「客户名」这一半**本项不覆盖**，且**不假装覆盖**。
3. **不修自定义字段族的既有缺陷**：`saveValues` 的原值回补只覆盖 `hidden`、**不含 READ_ONLY** ⇒ 自定义的 READ_ONLY 字段被省略时值会被「先删后插」删掉（`CustomFieldService.java:254-265`）。它属自定义字段族、且是 056 契约覆盖的行为，修它需要自己的偏离登记 ⇒ **登记为债务，本项不修**。
4. **不改 063 的脱敏口径**：`mask()` 与 `visibleOwnersOrNull` 在无主体时 fail-open，本项**只**接字段掩码（FR-022），不碰脱敏。
5. **不改 056 的任何文件**（授权依据见 §1.3：它自己写的是「后续扩展」）。**不改 `V64`**、不改 `V87` 与 `RoleConstants` 的历史论述。
6. **不引入全局 `@JsonInclude`**（会改动全仓所有响应体形状）；**不给收口点加缓存**（避免配置变更后陈旧）。
7. **不加服务层的 `permission` 白名单**：`FieldPermissionRequest.permission` 的 `@Pattern` 已经挡住了非法值 ⇒ 再加一层是**冗余分支 + 覆盖率成本**（`research.md` 有实测依据）。
8. **不给掩码做「字段级豁免清单」**、不新增 opt-out 注解：那是一套新的对外配置面，本项只做收口。
9. **不碰 `.specify/feature.json`**；**不跑任何 `/speckit-*`**。

## 5 成功判据

### 可度量结果

- **SC-001** 一个被配成 HIDDEN 的内置字段，在**列表 / 详情 / 公海**三处响应里**全部**为 `null`，且 **ADMIN 仍见真值**（实测）。
- **SC-002** 省略一个 HIDDEN 的内置字段提交更新 ⇒ **库中原值不变**；其中商机金额必须**保持 5000 而非 0**（实测，隔离实例）。
- **SC-003** xlsx 导出里该字段**格为空**，且**定时导出**（业主 = 受限角色）路径同样如此（实测）。
- **SC-004** 管理员能在配置页里**选到内置字段并保存**；`upsert` 对「双给/双不给 identifier」与「未知 `fieldKey`」都回 **422**（实测）。
- **SC-005** **未配置任何内置权限时**，全部既有用例**逐字全绿**（本项对默认路径零影响）。
- **SC-006** 八道门禁全绿（后端「失败集合 ⊆ 4 例已批准偏差 + 本项新增全绿」+ `jacoco:check` 打印结论行；前端 i18n/lint/typecheck/ui/zh + 定向 vitest）。
- **SC-007** 定向破坏 **D1–D14** 逐条**被观测到转红**（逐条 `cp` 还原，判据是 `git hash-object` 与破坏前相等）；其中 **D14 的预期是「保持全绿」**，且**如实记为「没有端到端判据看着这条结构性风险」**（不得写成「已验证无风险」）。

## 6 未验证边界（如实声明）

- **内置掩码只覆盖 2 个实体、11 个字段。** 上游点名的是「**内置字段（客户名/金额等）**」，而「**客户名**」这一类**本项覆盖不到**（它是必填字段，见 §4-2）。⇒ 本项**部分**兑现了那句话，⚠️ 块里必须写明，**不得**让读者以为内置字段已全面受控。
- **`create` 上被配成 HIDDEN 的字段取默认值/null，且客户端无法设置它。** 这是 HIDDEN 在 create 上的**唯一自洽语义**（提交即 422、省略即默认），但它确实意味着**没有人能创建带该字段值的记录**——若业务需要该字段在创建时可填，就不能配 HIDDEN。**没有判据能检测「管理员配错了 HIDDEN」**。
- **掩码后的字段呈 `null` 而非缺键。** 与「这个字段本来就没值」不可区分（这是**有意**的：无存在性侧信道），但**客户端无法借此区分**「被遮蔽」与「空值」——若产品需要区分，那是另一个规格。
- **「恰好一列非空」没有 DB 级约束**（实测：双 NULL 行在 H2 与 MySQL 都能插入）。若日后有代码绕过 `upsert` 直接写这张表，错行会被静默接受。**`SchemaParityIT` 也不覆盖列级漂移**（其自陈）。
- **收口点每响应算一次 plan、未加缓存。** 代价是每响应 1–2 次查询；本项**不声称**它零成本。
- **`principal == null → ADMIN` 的 fail-open 沿用未改。** 今天匿名端点不返回任何注册载体，故**不可观测**；但这是一个**约定的**安全边界，不是被验证过的边界。**没有对应用例**。
- **没有一条用例能证明「内置字段掩码在并发/多主体下是安全的」**——本项的证据只覆盖单请求路径。
- **本项不改变「数据权限（行级）」的任何行为**：一个角色能通过字段掩码看不见 `phone`，**不代表**他看不见那条客户记录。
