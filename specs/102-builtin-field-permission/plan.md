# 实施计划：字段级权限的内置字段（102）

**上游**: `CRM_FEATURE_COMPARISON.md` **P0 第 1 条** + **2.9 节 FLS 行**（`spec.md` §1.1 逐字引）
**用户裁决（2026-09-17）**: ① 配置存储 = **复用同一张 `field_permission` 表**（加 `field_key`、`field_id` 改可空、二选一）；② 尺度 = **读 + 写**，实体 = **CUSTOMER + OPPORTUNITY**，**写路径必须管**；③ 出参过滤落点 = **单一收口点**（`ResponseBodyAdvice#beforeBodyWrite` + 注册表），**不采用** pull 型逐装配点调用；④ xlsx 导出 = **含**
**形制**: 后端 + 前端 + 迁移 + 文档；**1 个迁移（V91）、1 个新端点、零新权限码**；**产 `contracts/`（对外行为确有变更）**、**产 `data-model.md`（只写 V91 增量）**
**授权依据**: 056 三处逐字把内置字段写成**后续扩展** ⇒ 本项是**兑现 056 自己留的待扩展项**，**056 全部工件一字不改、无偏差台账**（`research.md` §1 原文回引）。

---

## Constitution Check

### 一、契约优先的 API 设计（不可协商）

⚠️ **通过，但需要一份新契约**，并须先解决一个**归类问题**。056 的 `specs/056-field-permission/spec.md:46` 逐字写着「权限仅作用于自定义字段（**内置字段不在 v1 范围，后续扩展**）」，`:86` 与 `plan.md:25` 同义 ⇒ 本项**不是偏离冻结契约**，而是**兑现契约里明文的待扩展项**。方向与 101 **相反**（101 是 062 写下了不实行为 ⇒ 偏离 + 台账），**两者不得混用**。
⚠️ 但 056 的 `contracts/field-permission.md` 与实现之间**本就有一组漂移**（契约 body 是 `roleId: 2` 而实现是 `roleCode`；契约的 `PUT /field-permissions/{id}` 在实现里**不存在**；`data-model.md` 写 `role_id BIGINT` / `field_id BIGINT NOT NULL` 而实现是 `role_code VARCHAR(30)`）⇒ 本项**不改 056**，但这组漂移必须**用证据登记**在 102 的 `research.md`，否则「未偏离契约」这句话不踏实。
本项**新增** `contracts/field-permission-builtin.md`：新端点、`upsert` 请求体形状（`fieldId`/`fieldKey` 二选一）、出参掩码语义（置 `null`、不删键、读+写+导出三面），并**逐字收存** 056 契约的原行为与其三处漂移作对照。

### 二、分层架构与关注点分离（不可协商）

✅ **通过**。四个新类落 `com.crm.support`（该包已存在，唯一成员 `SalesOpportunityAssembler`）：注册表（`BuiltinFieldRegistry` + `BuiltinField` record）、判据源（`FieldMaskPlanner`）、出参收口点（`FieldMaskingResponseBodyAdvice`）、写侧护栏（`BuiltinWriteGuard`）。收口点是**框架接缝**（`@ControllerAdvice`），不是把业务搬进 Controller——Controller 仍不判业务。业务判定留在 Service/Permission 层：`validateBuiltinWrite` 挂在 `FieldPermissionService` 上**与既有 `validateWrite` 同形**。
**不新增包、不新增范式**（`@ControllerAdvice` 仓里已有先例：`GlobalExceptionHandler`）。

### 三、数据完整性、安全与校验（不可协商）

✅ **通过，且本项的主体就是这条**。本项关掉的是一条**今天就能复现的数据销毁通路**（`spec.md` §1.4-5：省略一个 HIDDEN 金额字段 ⇒ 落库 `0`，一个看起来合法的值），并让一条**存了却不生效**的权限配置真的生效（今天配 HIDDEN 的客户电话照样发给该角色）⇒ 安全面是**净收敛**。
⚠️ 三处必须写明的**新边界**：① `create` 无原值可回补 ⇒ HIDDEN 字段在 create 上等价于「不可设置」；② `principal == null → ADMIN` 的 fail-open **沿用未改**（自定义字段今天就是，匿名端点今天不返回任何注册载体）；③ 掩码后字段呈 `null` 而非缺键（与「无值」不可区分——**无存在性侧信道**，代价写进债务）。
Bean Validation：`FieldPermissionRequest.fieldId` 去掉 `@NotNull`、加 `fieldKey`（`@Size(max=64)`）；**「恰好一个非空」是跨字段规则，Bean Validation 表达不了** ⇒ 由 Service 强制并抛 **422 `FIELD_PERMISSION_INVALID`**（056 定义了却从未抛过的码）。**`permission` 的 `@Pattern` 白名单一字不动**（已挡住非法值 ⇒ 不加冗余的服务层校验，见事实 13）。

### 四、测试优先与质量门禁（不可协商）

✅ **通过**。17 条判据（T1–T17，见「验证」节），覆盖注册表自检/条数、判据源三态、`upsert` 不变式与幂等、三处读路径（列表/详情/公海）、两个信封、两种详情子类、**写侧两条旗舰**（省略 HIDDEN 客户字段不销毁、省略 HIDDEN 金额保持 5000 而非 0）、422 两码与 READ_ONLY 正对照、available-fields、两种导出（含**无请求主体**的定时导出）；14 条定向破坏逐条做、逐条 `cp` 还原。
⚠️ **假绿通道已识别（三条）**：① 未配置内置权限时 plan 为空 ⇒ 收口点是逐字直通 ⇒ **既有用例全绿不代表新机制正确**，必须有**配了权限**的用例（T8–T13）；② **JSON 全绿不代表 xlsx 正确**（导出是唯一绕过收口点的出口）⇒ T15/T16 是唯一防守；③ **定时导出路径没有请求主体**，用 `SecurityUtil` 取主体的写法在单测里恒为 null ⇒ T16 必须真的把业主设成受限角色才有效。
⚠️ 中文断言必须显式 UTF-8（本仓有 ISO-8859-1 假红的先例）；能断 `error.code` 的地方**优先断 code**。
⚠️ **五个新类都在 jacoco 分母里**（`support/**` 不在排除清单）⇒ T1–T7 必须真覆盖它们的分支；**不得**把机制类放进 `common/**`（排除包）——那等于用排除项躲门禁。

### 五、简洁、可维护与可观测（不可协商）

✅ **通过**。新增 4 个类（3 个机制 + 1 个 record）、1 个端点、1 个迁移；**不引入**全局 `@JsonInclude`、**不引入**缓存、**不引入** opt-out 注解、**不引入** `FieldMaskPlan` 记录类（返回 `Set<String>` 少一个类、少一份 record 覆盖税）。
可观测性：新增错误码**零枚**（复用 056 的 `FIELD_PERMISSION_INVALID` / `FIELD_HIDDEN` / `FIELD_READ_ONLY`）；注册表 `@PostConstruct` 自检**把「属性名打错」从静默空操作变成启动失败**（这是本设计最关键的护栏）；查找表用 `Map<String, BuiltinField>` 而非 `Set.contains`（避免把 record 的 `equals/hashCode` 拉进覆盖率义务）。

---

## 已核实事实（机制级；均已读实现或实测）

1. **仓里不存在任何出参收口点**（实测 0 命中）：`@Json*` 注解 **0**、`ResponseBodyAdvice` **0**、`HandlerMethodReturnValueHandler` **0**、HTTP 层 `WebMvcConfigurer`/`HandlerInterceptor` **0**（唯一 `addInterceptors` 是 `WebSocketConfig:62` 的 STOMP 握手）、`aspect/` 目录**不存在**（切面在 `security/` 下且只有两个 `@Before`，**无 `@Around`/`@AfterReturning`**）、`@RestControllerAdvice` 只有 `GlobalExceptionHandler`（只处理异常）。转换是 **53 个文件**里的手写 `toResponse/copyToResponse/fillResponse`（实测 `grep -rln` = **53**，不是 57），Controller 不参与转换。
2. **FLS 挂在 Service 方法调用上、不在 HTTP 边界**：15 个调用点全靠自觉 ⇒ 收口点必须选在**序列化**这一层，否则等于**再造一批**「新增读路径忘了调就静默失效」的点（这是选单一收口点的核心理由）。
3. **权限的真实语义（逐字读 `FieldPermissionService.java:119-147`，**推翻两条转述**）**：HIDDEN「**提交即 422**」；READ_ONLY 的判据是 `existing == null || !existing.equals(v.getValue())` ⇒ **`existing == null` 也算 changed ⇒ 422**，即 READ_ONLY =「**必须原样回传，任何差异都 422**」，**不是**「首次写入放行」。⇒ **写侧唯一的缺口是「省略」**（客户端提交 HIDDEN 会 422、改 READ_ONLY 会 422，但它**看不见就会省略**），而实体装配**无条件覆盖** ⇒ 「拒绝省略」在语义上**不可实现**，正当处置只有**回补原值**。
4. **`apply()` 无条件覆盖每一个字段 + 商机金额有两处 `null → 0L`**（实测）：`CustomerService.apply`（`:458-470`）逐字段赋值；`OpportunityService.create:120-121` 与 `update:145,147` 是 `req.getX() == null ? 0L : req.getX()`。⇒ 省略一个 HIDDEN 金额字段，落库的是 **0**（比 null 更坏：一个看起来合法的值）。**这个缺陷今天就能复现**，是本项的旗舰用例（T11 / D5）。
5. **单一收口点在序列化时遍历对象图 ⇒ 53 个手写装配点与 `CustomerPoolService.toResponse:216` 这条第二份装配自动被覆盖**（正对照：ADMIN 必须仍见真值，防「全 null」假绿）。
6. **导出的既有先例是「列在、格空」**：`ExportExecutor.writeHeader` 为**全部** `cfDefs` 写表头，而 HIDDEN 自定义字段的格子取 `getOrDefault(fieldId, "")` ⇒ **空串**（过滤版 `readValuesBatch` 里根本没有它们）⇒ 内置字段照此，**不发明第二种读法**（**不去删列**）。
7. **导出有三个入口，其中定时导出没有请求主体**：`ExportExecutor.writeCustomers:175`、`writeOpportunities:203`、`CustomerExcelService:34`（另一套 7 列、**不调** `mask()`），以及 `ScheduledExportServiceImpl:216` 从调度线程进 `executeExportWithRowCount`（`SecurityUtil.currentPrincipal()` 为 **null**）。
8. **jacoco 排除项** = `com/crm/CrmApplication.class`、`dto/**`、`entity/**`、`common/**`（`pom.xml:252`）⇒ `support/**` **在覆盖率分母里**；新机制类**不得**藏进排除包（101 的先例是刻意让 `config/MailInboundStatus` 留在分母付覆盖成本）。
9. **「就地置 null 会毒化缓存」以证据排除**：全仓只有三个手工缓存（`rolePermissions` / `visibleOwnerIds` / `opportunityStages`），**没有一个存出参 DTO**；`@Cacheable` 被 `CacheConfig` javadoc 刻意禁用。⇒ **D14 的预期是「仍全绿」**，且必须如实记为「**没有端到端判据看着这条结构性风险**」，**不许**写成「已验证无风险」。
10. **H2 2.2.224 实测（决定迁移形状能否成立）**：同 (角色,实体) 插**两行**内置（`field_id` 均 NULL）**通过** ⇒ H2 视 NULL 彼此不等，**与 MySQL 同向**；重复的自定义行（`field_id=5`）被**旧**唯一索引拒绝；**双 NULL 行（两列都空）也通过** ⇒ 「恰好一列非空」**没有** DB 级约束，只能由 `upsert` 保证（债务 1）。
11. **迁移数 = 89**（实际文件，V1–V90，**缺 V72**）⇒ 下一个可用 **V91**。⚠️ `INSTALL.md` 有**既有腐坏**（**不是本项造成**）：正文写「当前 V1~V90 共 89 个脚本，末条为 V90」，而下面的迁移列表**最后一行是 V89**——**V90 从未被登记进表**（V90 = `comment_and_custom_object_record_codes`，096 两族补授）⇒ 只加 V91 会**看着像本项跳了号**，必须同批补 V90 行并带日期 ⚠️ 标明「本行此前缺失」。
   ⚠️ **对照：`specs/README.md:150` 的迁移表末行是 V90**（096 当批登记齐全）⇒ **同一份仓里两处迁移清单的状态不同**，不得用「另一处已登记」推断这一处也已登记。
   ⚠️ **迁移数的活落点实测共 6 处**（判据 `grep -rn 'V1~V90\|V1–V90\|89 个迁移\|89 个脚本' --include=*.md .`）：`INSTALL.md:124` 与 `:262`、`specs/README.md:3`（前言版本行）与 `:150`（**章节标题**）、`PROJECT_FEATURES.md:13`、根 `README.md:156`（目录树）。**本项计划初稿只列了 `INSTALL.md` 一处**，已在**立项期**改正（C1 未提交、未执行代码）——照初稿做会一次留下 5 处假数字，正是仓内「**一个数字住在好几个地方，要一起改**」那条教训的原形。订正块/重测块内的**历史读数**（`PROJECT_FEATURES.md:41/61/80/109/135/316`、`CRM_FEATURE_COMPARISON.md:89-91/452`、`roadmap.md:386`）**一个字不动**。
12. **`FieldPermissionRequest.permission` 已有 `@Pattern(^(HIDDEN|READ_ONLY|EDITABLE)$)`** ⇒ 不存在「非法权限值入库」的隐患，**不加**冗余的服务层白名单（多一个未被覆盖的分支还要付覆盖率）；`fieldId` 是 `@NotNull`，本项须改为可选。
13. **嵌套载体实测，掩码必须按「字段所属实体」判定、不随父对象传播**：`CustomerDetailResponse extends CustomerResponse`（+ `opportunities`/`followUps`/`contacts`/`customer360`）、`OpportunityDetailResponse extends OpportunityResponse`（+ `List<SalesOpportunityResponse> salesOpportunities`）。`OpportunityBrief` = {id,name,status,salesOpportunityCount} **不含敏感列**；但 `ContactResponse` 有 `phone/email/remark`（属 **CONTACT**）、`SalesOpportunityResponse` 有 `amount`（属 **SALES_OPPORTUNITY**）⇒ 这两类**不**被 CUSTOMER/OPPORTUNITY 的配置遮蔽（否则误伤），同时**必须如实登记为本项未覆盖**（否则读者会把「金额已过滤」读成 `amount` 也不可见）。
14. **必填字段的实测约束**：`CustomerRequest.name`/`company` 是 `@NotBlank`、`OpportunityRequest.customerId` 是 `@NotNull`、`name` `@NotBlank` ⇒ 配成 HIDDEN 会让实体**完全不可编辑**（客户端看不见却必须提交）⇒ **必填字段永久排除**，且上游点名的「**客户名**」这一半本项**不覆盖**（必须写进 ⚠️ 块，不得让读者以为覆盖了）。同理 `ownerId` 的 javadoc 是「空 = 公海」⇒ 置 null 是**说谎**（不是「看不见」，是「变成公海」），排除。
15. **两条被当成「必炸点」的假设，实测一条真、一条假**：
   - **假**：`permissionsForRole` 的 `Collectors.toMap` 遇 **null 键**并不 NPE（实测 `{null=HIDDEN, 1=HIDDEN, 2=EDITABLE}`；null **值**才 NPE、重复键抛 `IllegalStateException`）⇒ 真后果只是 `hiddenFieldIds` 的 Set 里多一个 `null` 元素（`hidden.contains(v.getFieldId())` 对非空自定义字段恒 false），**静默不洁而非 500**。把内置行排除出 custom 映射仍然要做，但**理由是卫生，不是防炸**。
   - **真**：`upsert`（`:54-74`）与 `permissionFor`（`:83-96`）都用 `.eq(FieldPermission::getFieldId, ...)` ⇒ 传 null 时生成 **`field_id = NULL`，永不匹配** ⇒ 前者第二次保存**必然再 INSERT 并撞唯一键**，后者**回落 `EDITABLE`（fail-open）**。⇒ 内置查找**必须**走 `field_key` 分支，且 `permissionForKey` **不得**退化成「传 null 给 `permissionFor`」。

---

## 结构决策

### 语义（本项全部的行为变更）

| 面 | 内置字段被配成 HIDDEN / READ_ONLY 后的行为 |
|---|---|
| **出参（JSON）** | 收口点在序列化前把注册过的 **HIDDEN** 字段**置 null**（不删键、**不引入全局 `@JsonInclude`**——那会改动全仓所有响应体形状）。置 null **与「无值」不可区分 ⇒ 无存在性侧信道**；代价是字段呈 `null` 而非缺键（债务 6）。覆盖**列表 / 详情 / 公海 / 嵌套载体**与 `ApiResponse` + `PageResult` 两层信封 |
| **入参（写）** | 提交 HIDDEN ⇒ **422 `FIELD_HIDDEN`**；改 READ_ONLY ⇒ **422 `FIELD_READ_ONLY`**（与自定义字段**同码同语义**）；**省略** HIDDEN/READ_ONLY ⇒ **回补库中原值** |
| **create** | 无原值可回补 ⇒ HIDDEN 字段在 create 上**等价于「不可设置」**（省略即取默认值；提交则 422）。**必须写明**，否则会被读成漏做 |
| **xlsx 导出** | **列在、格空**（照自定义字段的既有先例）；**三个入口全部接同一个判据源** |
| **配置面** | 新增 `GET /field-permissions/available-fields?entityType=X` 返回「内置 + 自定义」可配字段；`upsert` 接受 `fieldKey`（与 `fieldId` 二选一）⇒ 管理员从此**配得出内置字段** |

### 注册表（`support/BuiltinFieldRegistry` + `BuiltinField` record）

**入选规则**（写进 javadoc，防后人随手加）：① 是用户可见的业务数据；② 在 entity / request / response 三侧**都有同名载体**；③ **不是必填**；④ `null` 不承载额外语义。

- ⚠️ **必填字段排除**（事实 14）；⚠️ **`ownerId`/`ownerName` 排除**（事实 14 末）；⚠️ **派生/只读列排除**（`customerName`、`salesOpportunityCount` 之类无对应实体列）。

| 实体 | 注册字段（`fieldKey` = 属性名） | 载体类 |
|---|---|---|
| CUSTOMER | `contactPerson` `phone` `email` `address` `remark` `status` `campaignId`（**7**） | `Customer` / `CustomerRequest` / `CustomerResponse`（`isAssignableFrom` 覆盖 `CustomerDetailResponse`） |
| OPPORTUNITY | `expectedAmountMin` `expectedAmountMax` `remark` `status`（**4**） | `Opportunity` / `OpportunityRequest` / `OpportunityResponse`（覆盖 `OpportunityDetailResponse`） |

**共 11 条**，条数由一条**测试断言**钉住（防漂移）。`@PostConstruct validate()` 用反射在**每个载体类**上逐一解析属性名与类型，**任一失败即让 ApplicationContext 启动失败** ⇒ 「属性名打错 = 静默空操作」在本设计下**不可能发生**（T1 专钉它）。查找表用 `Map<String, BuiltinField>`（键 `entityType + ":" + fieldKey`），**不用** `Set<BuiltinField>`/`contains`。

### 单一收口点（`support/FieldMaskingResponseBodyAdvice`，`@ControllerAdvice implements ResponseBodyAdvice<Object>`）

- **快路径**：`principal == null || ADMIN` ⇒ **原样返回**（零成本；ADMIN 恒不受限与 `permissionFor` 同口径）。`principal == null → ADMIN` 是**沿用的 fail-open**——不改口径，但写进 `research.md`。
- **遍历**：`ApiResponse` 取 `data`、`PageResult` 取 `items`，再按 `Collection`/数组/`Map.values()`/载体类递归；**JDK 类型**（`java.*`、`String`、枚举、`byte[]`）**直接跳过**（避免 `InaccessibleObjectException`）；**身份集**做环guard + 深度上限。载体匹配用 `isAssignableFrom`（覆盖两个 `DetailResponse` 子类）。命中载体 ⇒ 取该载体**所属实体**的掩码集合，置 null 对应属性；**不向子对象传播**（事实 13）。
- `support/FieldMaskPlanner.plan(roleCode, entityType) → Set<String>`（不可变）是**唯一判据源**：收口点、`ExportExecutor`、`CustomerExcelService` **共用**。**不加缓存**（配置一改即生效；代价是每响应 1–2 次查询，与既有 `readValues` 同量级）——债务 7。
- 刻意**不返回 `FieldMaskPlan` 记录类**：返回 `Set<String>` 少一个类、少一份 record 的 `equals/hashCode/toString` 覆盖税。

### 写侧回补（`support/BuiltinWriteGuard`，4 个调用点）

调用序列**逐字固定**：`require(id)`（拿到**未被改动**的库中实体）→ `snapshot(entity, entityType)`（仅注册字段）→ `validateBuiltinWrite(roleCode, entityType, req, original)`（422 判定，**必须在 apply 之前**）→ `apply(req, existing)` → `restore(entity, original, maskedKeys)`（HIDDEN 与 READ_ONLY **都**无条件回补——READ_ONLY 的改动已被 422 挡住，回补等价于保留）→ `updateById`。
调用点：`CustomerService.create/update`、`OpportunityService.create/update`。
⚠️ **`restore` 必须在 `apply` 之后**——商机金额的 `null → 0L` 强转发生在 `apply` 内，**只有后置回补才能盖掉它**（T11 专钉这一条；D5 把顺序挪到前面专杀「顺序无关」的错觉）。
⚠️ `create` 无原值 ⇒ 只做 422 校验（见语义表的 create 行）。

### `upsert` 重写（三处）

① `fieldId` 与 `fieldKey` **恰好一个非空**，否则 422 `FIELD_PERMISSION_INVALID`；② `fieldKey` 必须在注册表中**且属于该 `entityType`**，否则 422；③ 查询**按 identifier 分支**（`.eq(fieldKey)` vs `.eq(fieldId)`）——**不得**用 `.eq(fieldId, null)`（事实 15）。
配套新增**并行**方法族：`permissionForKey` / `builtinPermissionsForRole` / `validateBuiltinWrite`。⚠️ **不动 056 的既有方法签名**（`permissionFor` / `permissionsForRole` / `validateWrite`）——改签名会牵动 15 个自定义字段调用点。

### 配置面

- 后端：`GET /api/v1/field-permissions/available-fields?entityType=X`（复用 `PageResult` 形；未知 `entityType` ⇒ **422 `FIELD_PERMISSION_INVALID`**，**不静默返回空表**）。项形如 `{fieldId, fieldKey, fieldName, builtin}`；内置项的 `fieldName` 由注册表的 `label` 提供（中文由后端给，照 `CUSTOMER_HEADERS` 的既有做法）。顺带修好 `FieldPermissionResponse.fieldName` **今天只声明、从不赋值**的问题。
- 前端：`FieldPermissionPage.tsx` 的 `loadFields` 改调新端点（今天只列自定义字段）；表单「字段」下拉的选项值编码 `fieldId|fieldKey`；表格「字段」列渲染**名称**而非 `dataIndex: 'fieldId'` 的**数字 id**；`types/fieldPermission.ts` 加 `fieldKey?: string`。

### 一个数字住在好几个地方（落点清单，交付时逐处收口）

| 落点 | 现值 | 本项 |
|---|---|---|
| `CRM_FEATURE_COMPARISON.md:367`（P0 第 1 条） | 状态列 `**仍缺**` | **`**⚠️ 基本闭合**（102，2026-09-17）`**——**不能是 ✅**（动作列的字面要求已满足，但差距列的名字只被**部分**证伪）；**判定列与分值不动** |
| `:273`（2.9 FLS 行） | 判定列 `❌ **仍缺**`；逐字「内置字段（客户名/金额等）与 API 出参确未过滤」 | 原文逐字保留 + 带日期 ⚠️：写清**已覆盖什么**与**没覆盖什么**；**判定列与分值照旧** |
| `:285`（2.9 小结） | 逐字「FLS 内置字段、SSO、字段加密、IP 白名单**四项一个都没动**」 | 本项后**当场变成假话** ⇒ 原文逐字保留 + ⚠️ 订正其中**一项已动**、其余三项未动 |
| `:349`（4.1 结论 3） / `:19-23`（时间线块） | 断言 FLS 内置字段未做 | 同批订正（原文逐字保留 + 日期） |
| `README.md:163` | **已腐坏**（`99 个功能模块，001~100`，实际应为 100 个 / 001–101） | 与编号落点一起订正为 **101 个 / 001–102**（旧值逐字保留）；独立于本项主题但同属「一个数字住在好几个地方」 |
| `PROJECT_FEATURES.md:19`/`:77`/`:111` | 键 **2963**、行 **3453/3426** | 以交付时 `i18n:check` **实测**为准，三处**一起**改、旧值逐字保留可 grep |
| `PROJECT_FEATURES.md:20`/`:107` | `100（001–101，缺 069）` | **101（001–102，缺 069）**（旧值逐字保留）；`:290` 那条 097 的历史读数注记**不动** |
| `INSTALL.md:262` + 迁移列表 | 正文「当前 V1~V90 共 89 个脚本，末条为 V90」；**表末行是 V89** | 正文改 **V1~V91 共 90 个脚本，末条为 V91**；**补 V90 行**（既有遗漏，带日期 ⚠️）+ **加 V91 行** |
| `INSTALL.md:124` | 「（V1~V90，共 89 个迁移脚本；**V72 不存在**，故编号有断档）」 | 同步改 **V1~V91，共 90 个迁移脚本**（旧值逐字保留） |
| `specs/README.md:3`（前言版本行） | `Flyway V1~**V90**，共 **89** 个迁移脚本` | **V1~V91，共 90 个**（旧值逐字保留）；并同段加 102 的形制句 |
| `specs/README.md:150`（章节标题） | `## 数据库迁移对照（Flyway V1~**V90**，共 **89** 个脚本，V72 不存在）` | **V1~V91，共 90 个脚本**；**并加迁移表末行 V91** —— ⚠️ **082 批踩过的坑正是「只改表行、漏标题」**（该节自己的 ⚠️ 块记着这件事） |
| `PROJECT_FEATURES.md:13` | `| Flyway 迁移 | **89 个（V1–V90，缺 V72）** |` | **90 个（V1–V91，缺 V72）**（旧值逐字保留） |
| `README.md:156`（**根** README，目录树） | `db/migration（V1~V90，缺 V72；V90 = 096 权限码补授）` | 加 **V91 = 102 内置字段权限**（旧值逐字保留） |

⚠️ **迁移数这 6 处是实测枚举出来的**（判据：`grep -rn 'V1~V90\|V1–V90\|89 个迁移\|89 个脚本' --include=*.md .`，**排除**订正块内的历史读数——`PROJECT_FEATURES.md:41/61/80/109/135/316`、`CRM_FEATURE_COMPARISON.md:89-91/452`、`specs/roadmap.md:386` **都是历史读数，一个字不动**）。
⚠️ **本项计划初稿只列了 `INSTALL.md` 一处** ⇒ 已在**立项期**改正（C1 未提交、未执行任何代码）：若照初稿做，交付时会**同时**留下 5 处假数字——这正是仓内「**一个数字住在好几个地方，要一起改**」那条教训的原形。
⚠️ **迁移数与 101 不同**：101 是**零迁移**（落点一个都不动），本项**有**一次迁移 ⇒ **必须逐处改**，不得照抄 101 的结论。
| `specs/README.md` 模块表 + 编号说明段 | 末行为 101 | 加 102 行（立项 `⏳ 进行中` → 交付 `✅`）；编号说明段追加 102 的形制句（含「**兑现 056 自己留的待扩展项，056 一字不改**」） |
| `specs/roadmap.md` 最后更新段 / 102 行 / 计数（现 100） | 100 勾 / 0 未勾 | 加 102 行 + 计数 **100 → 101** + 最后更新段（交付读数）+ **债务台账 8 条** |
| `specs/056-field-permission/**` | — | **一个字符都不改**（授权依据）；其与实现的既有漂移 + 本项新增列，**在 102 的 `research.md` 用对照表登记** |
| `V64__field_permission.sql`（`field_id BIGINT NOT NULL`） | — | **不得编辑**（已应用的迁移永不编辑）；变更全部落在新的 V91 |

---

## Project Structure

### Documentation (this feature)

```
specs/102-builtin-field-permission/
├── spec.md                       # 需求（§1 由来 / §2 用户故事 / §3 FR / §4 非目标 / §5 SC / §6 未验证边界）
├── plan.md                       # 本文件
├── research.md                   # §1 056 回引与漂移对照 / §2..§12 机制勘察与实测读数 / §13 债务 8 条
├── data-model.md                 # **只写 V91 增量**（不重列 V64 的 6 列）
├── contracts/field-permission-builtin.md   # 对外行为变更 + 056 契约原行为逐字对照
├── quickstart.md                 # 门禁 / 隔离实例 / 可核判据 / 订正自查
├── falsification-evidence.md     # D1–D14 的「该红哪条用例」事先写死；读数交付时回填
├── tasks.md                      # 阶段 A–E + 实做订正三列
└── checklists/requirements.md    # 内容质量 / 一致性 / 覆盖度 / 可执行性
```

### Source Code

```
backend/src/main/resources/db/migration/
└── V91__field_permission_builtin_fields.sql          # 新增（field_key + field_id 可空 + 第二条唯一索引）

backend/src/main/java/com/crm/
├── support/
│   ├── BuiltinFieldRegistry.java                     # 新增：11 条登记 + @PostConstruct 反射自检
│   ├── BuiltinField.java                             # 新增：record（entityType/fieldKey/label/载体属性名）
│   ├── FieldMaskPlanner.java                         # 新增：plan(roleCode, entityType) → Set<String>（唯一判据源）
│   ├── FieldMaskingResponseBodyAdvice.java           # 新增：@ControllerAdvice，序列化前收口
│   └── BuiltinWriteGuard.java                        # 新增：snapshot / validateBuiltinWrite / restore
├── service/
│   ├── FieldPermissionService.java                   # 改：upsert 重写 + permissionForKey / builtinPermissionsForRole / validateBuiltinWrite
│   ├── CustomerService.java                          # 改：create/update 四点接护栏
│   ├── OpportunityService.java                       # 改：create/update 四点接护栏
│   ├── ExportExecutor.java                           # 改：writeCustomers/writeOpportunities 的 cell() 走 plan
│   ├── CustomerExcelService.java                     # 改：7 列那套同样接 plan
│   └── ScheduledExportServiceImpl.java               # 改：按任务业主角色算掩码
├── controller/FieldPermissionController.java         # 改：GET available-fields
└── dto/field/                                        # 改：FieldPermissionRequest 加 fieldKey、放开 fieldId；Response 补 fieldName

backend/src/test/resources/schema-h2.sql              # 改：3 处（CREATE 块加列 / 改可空 / 加唯一约束），行尾标 -- V91
backend/src/test/java/.../SchemaParityIT.java         # 改：MIRRORED_MIGRATIONS 加 "91"（刻意不用区间）
backend/src/test/java/.../support|service|integration/ # 新增：6 个测试类（T1–T16）

frontend/src/pages/settings/FieldPermissionPage.tsx   # 改：字段列表调新端点 + 显示名称
frontend/src/pages/settings/FieldPermissionPage.test.tsx  # 新增（T17）
frontend/src/types/fieldPermission.ts                 # 改：加 fieldKey?
frontend/src/services/fieldPermission.ts              # 改：availableFields
frontend/src/i18n/{zh-CN,en-US}/*.json                # 改：新端点相关文案（键/行数落点同批收口）

docs/INSTALL.md / README.md / PROJECT_FEATURES.md / CRM_FEATURE_COMPARISON.md   # 改：见落点清单
```

**结构决策说明**：判据源、收口点、护栏三件放在**同一个包**（`support`）——它们是同一条链上的三个环节，分开会掩盖「共用同一个 plan」这一事实。**不新增包**（`com.crm.aspect` 之类）：仓里没有 AOP 范式，`@ControllerAdvice` 已有先例。

---

## 分步与提交（7 次，每次可回退）

| # | 提交 | 内容 |
|---|---|---|
| C1 | `docs(102): 立项` | 九件工件（含 `data-model.md`、`contracts/field-permission-builtin.md`、056 回引与漂移对照表）+ 登记：`specs/README.md` 102 行与编号说明段、`specs/roadmap.md` 102 行 + 计数 100→101 + 债务 8 条。**不含任何代码** |
| C2 | `feat(102): V91 迁移与内置字段注册表` | `V91__field_permission_builtin_fields.sql`、`schema-h2.sql`（3 处 `-- V91`）、`SchemaParityIT` 加 `"91"`、`INSTALL.md`（正文 + **补 V90 行** + V91 行）、`support/BuiltinFieldRegistry` + `BuiltinField`、`FieldPermissionService` 的 builtin 方法族与 `upsert` 重写、`FieldPermissionRequest`。**既有路径行为不变**（内置行尚无法写入） |
| C3 | `feat(102): 出参收口点与写侧回补` | `support/FieldMaskPlanner`、`support/FieldMaskingResponseBodyAdvice`、`support/BuiltinWriteGuard`；`CustomerService.create/update`、`OpportunityService.create/update` 四处接线。⚠️ 这是**唯一**带行为变更的提交；既有用例预期**仍全绿**（未配置内置权限 ⇒ plan 为空 ⇒ 全直通），**若红先查是不是 `restore` 写错了字段** |
| C4 | `feat(102): 导出接同一判据源` | `ExportExecutor.writeCustomers/writeOpportunities`（`cell(plan, key, value)` 形态，**列在格空**）、`CustomerExcelService`、`ScheduledExportServiceImpl` 传任务业主角色（`userMapper` 已注入） |
| C5 | `feat(102): 配置面（可用字段端点与前端）` | `GET /field-permissions/available-fields`、`FieldPermissionResponse.fieldName` 赋值、`FieldPermissionPage.tsx` / `types` / `services`、i18n 两文件 + `PROJECT_FEATURES.md` 三处键数/行数落点（**实测值**、旧值逐字保留） |
| C6 | `test(102): 内置字段权限用例` | T1–T17（6 个后端测试类 + 1 个前端测试文件）。⚠️ **C2–C5 落地时允许新代码暂无对应用例**，门禁在 C6 之后跑第一次；C2–C5 各做 `mvn -B -o -q compile` 级编译自证 |
| C7 | `docs(102): 交付登记与文档订正` | 对比文档 5 处（`:19-23` / `:273` / `:285` / `:349` / `:367`）「订正不静默」+ `README.md:163` + `INSTALL.md` 复核 + 102 行状态改 `✅` + roadmap 最后更新段（交付读数）+ `falsification-evidence.md` 实测输出 + `tasks.md` 勾选 + 实做订正 |

⚠️ **C2 的 `upsert` 重写与 C5 的配置面之间是「后端已能收 `fieldKey`、前端还发不出来」的中间态**（两个端点都在后端，前端此刻调用旧路径仍正常）⇒ 中间态**不红**；但 C5 必须跑前端五道门禁。

---

## 验证

### 用例清单（本项唯一的**行为层**证据）

| # | 用例 | 文件 | 会因什么缺陷变红 |
|---|---|---|---|
| T1 | 注册表自检：坏属性名/坏类型 ⇒ `validate()` 抛异常 | `support/BuiltinFieldRegistryTest` | **属性名打错 = 静默空操作**（本设计最关键的护栏） |
| T2 | 注册表条数与成员（11 条，逐条 (entityType, fieldKey)） | 同上 | 条目被误删/误加（含「不许加必填字段」的回归） |
| T3 | `plan`：SALES+HIDDEN(phone) ⇒ 含 phone；ADMIN ⇒ 空；未配置 ⇒ 空 | `support/FieldMaskPlannerTest` | 判据源错（**全链都会跟着错**） |
| T4 | `upsert` 双空 / 双非空 ⇒ **422 `FIELD_PERMISSION_INVALID`** | `service/FieldPermissionServiceBuiltinTest` | 不变式没强制（该码**首次**被真正抛出） |
| T5 | `upsert` 未知 `fieldKey`（或属别的实体）⇒ 422 | 同上 | 垃圾配置入库 |
| T6 | `upsert` 幂等：同 (角色,实体,fieldKey) 保存**两次** ⇒ 第二次是 UPDATE | 同上 | `.eq(fieldId, null)` 未分支 ⇒ **第二次撞唯一键 500**（事实 15） |
| T7 | `permissionForKey`：三态 + ADMIN 恒 EDITABLE + 未配置 ⇒ EDITABLE | 同上 | 内置查找退化成 `permissionFor(..., null)` ⇒ **fail-open** |
| T8 | 客户 HIDDEN(phone)：**列表 / 详情 / 公海**三处响应均为 null；ADMIN 仍见真值 | `integration/BuiltinFieldMaskingIT` | 收口点没生效 / 只覆盖一种信封 / **漏了公海那份装配**（正对照防「全 null」假绿） |
| T9 | 商机 HIDDEN(expectedAmountMin)：`OpportunityResponse` 与 `OpportunityDetailResponse` 均 null | 同上 | `isAssignableFrom` 没做 ⇒ 只覆盖父类 |
| T10 | **旗舰 1**：客户 HIDDEN(phone)，`PUT` 省略该字段 ⇒ **库中仍是原值**（非 null） | `integration/BuiltinFieldWriteGuardIT` | 写侧没回补 ⇒ **省略即销毁** |
| T11 | **旗舰 2**：商机 HIDDEN(expectedAmountMin)，`PUT` 省略 ⇒ **保持 5000 而非 0** | 同上 | `restore` 没在 `apply` **之后** ⇒ 被 `null → 0L` 盖掉（事实 4） |
| T12 | 提交 HIDDEN 内置字段 ⇒ **422 `FIELD_HIDDEN`** | 同上 | 提交侧没管（只做了读侧） |
| T13 | 改 READ_ONLY 内置字段 ⇒ **422 `FIELD_READ_ONLY`**；**原样回传 ⇒ 成功**（正对照） | 同上 | READ_ONLY 被做成「一律拒绝」或「一律放行」 |
| T14 | available-fields：内置 11 + 自定义 N；未知 `entityType` ⇒ 422 | `integration/FieldPermissionAvailableFieldsIT` | 端点没接注册表 / **静默返回空表** |
| T15 | 导出（客户）：SALES 的 xlsx 里 phone **列存在且格空**；ADMIN 有值 | `integration/BuiltinFieldExportIT` | **只修了 JSON、忘了 xlsx** |
| T16 | 导出（定时）：任务的**业主是 SALES**、环境主体是 ADMIN ⇒ **仍按 SALES 掩码** | 同上 | 用环境主体（null→ADMIN）⇒ 定时导出仍泄漏（事实 7） |
| T17 | 前端：选项含内置字段、提交带 `fieldKey`、表格显示 `fieldName` 而非数字 id | `pages/settings/FieldPermissionPage.test.tsx` | 管理员**配不出**内置字段（本项最容易被漏的「可用性」面） |

⚠️ **T16 是唯一能抓住定时导出入口的用例**（该路径**无请求主体**）；**T15 是唯一能抓住 xlsx 的用例**。
⚠️ `mvn` 单跑**不覆盖前端**：T17 必须单独跑（本机默认 worker 池超订 ⇒ 只跑该文件，不跑全量）。

### 门禁

```bash
cd backend && mvn -B spotless:apply     # spotless 是 verify 相位首个门禁，比用例失败更早中止
cd backend && mvn -B verify             # 不传 -DargLine（会静默废掉 jacoco）
ls backend/target/jacoco.exec           # 必须存在；并核对 jacoco:check 打印了结论行
cd frontend && pnpm run i18n:check && pnpm run lint && pnpm run typecheck && pnpm run ui:check && pnpm run zh:check
cd frontend && pnpm exec vitest run src/pages/settings/FieldPermissionPage.test.tsx   # 定向，不跑全量
```

- **判据**：后端「**失败集合 ⊆ 4 例已批准偏差 + 本项新增全绿**」（**非** exit 0）；`jacoco:check` 必须打印「**All coverage checks have been met.**」；前端五道全绿且 `i18n:check` 的键/行数**实测值**与 `PROJECT_FEATURES.md` 三处一致；`zh:check` 的硬编码中文台账**不得增加**。
- **交付态读数只取那一次完整 `verify`**；门禁跑完**不再跑 Maven**（会覆盖 `jacoco.exec`，让交付块的字节数变成假话）。
- ⚠️ 本仓已知现象：**同一棵未改动的树两次 verify 的分母会漂移**（IDE 语言服务原地增量编译改写 `target/classes`）⇒ 交付块**取那一次并写明出处**，不去「确认哪一次更准」。

### 定向破坏（逐条做、逐条**被观测到转红**、逐条 `cp` 备份回写还原；期间不提交）

⚠️ 每条先写一句「它该改变哪条**可观察行为**」，跑完核对那条行为**确实变了**——没变就是**空操作**；看到红先读**是不是手段的红**（CRLF/spotless/编译错都不是目的的红）。**禁用 `git checkout` 还原**（用 `cp` + `git hash-object` 判据）。**就地改一律用 Edit 工具**（本仓 Java 源是 CRLF，脚本重写会把整个文件翻成 LF 并让 spotless 报 BUILD FAILURE）。

| # | 破坏 | 该红的判据 |
|---|---|---|
| D1 | 收口点直接返回原体（不遍历） | T8/T9 ⇒ 本项读侧主张 |
| D2 | 只处理 `CustomerResponse`，不做 `isAssignableFrom` | T9 ⇒ 详情型子类被漏 |
| D3 | 只取 `ApiResponse.data`，不处理 `PageResult.items` | T8 的**列表段** ⇒ 列表泄漏而详情正常 |
| D4 | 去掉 `restore` | T10/T11 ⇒ **省略即销毁**（旗舰） |
| D5 | 把 `restore` 挪到 `apply` **之前** | T11（金额被 `null → 0L` 盖掉）⇒ 专杀「顺序无关」的错觉 |
| D6 | 导出的 `cell()` 不做掩码（只修 JSON） | T15 ⇒ xlsx 泄漏 |
| D7 | 定时导出改用环境主体（`SecurityUtil`） | T16（环境主体是 ADMIN ⇒ 掩码消失） |
| D8 | `upsert` 去掉「恰好一个非空」判定 | T4 |
| D9 | `upsert` 退化成 `.eq(fieldId, ...)`（不按 identifier 分支） | T6 ⇒ 第二次保存撞唯一键 |
| D10 | `permissionForKey` 内部改调 `permissionFor(role, entity, null)` | T7 + T8（**全链 fail-open**） |
| D11 | 注册表删掉 `phone` 条目 | T2（条数）+ T8/T10（行为） |
| D12 | 注册表加入 `name`（必填字段） | T2 的「不许加必填字段」断言 ⇒ 防后人顺手加 |
| D13 | 让 `plan` 把 READ_ONLY 也算进掩码集合 | T13 的正对照（原样回传应当**成功**）⇒ 证明三态没被压成二态 |
| D14 | 对**被缓存**的对象做就地置 null（人为把 plan 塞进某个缓存后再请求两次） | **预期仍全绿** ⇒ 如实记为「**没有端到端判据看着这条结构性风险**」，并写明为什么（三个缓存都不存出参 DTO）——**不许**写成「已验证无风险」 |

### 手工冒烟（隔离实例，两次启动）

**配方照 `research.md` §12**：临时端口 + 独立 schema + 另一个 Redis db，收尾 `DROP` / `REVOKE` / `FLUSHDB` 并**核对共享库未动**。**不与并行会话抢 8081/5173**、**不在共享开发库上写数据**。

1. **默认档**：起隔离实例（未配任何内置权限）⇒ `GET /customers` 与 `GET /opportunities` 的 JSON **与改动前逐字相同**（默认零影响）。
2. **配置档**：在**隔离库**里配 `SALES + CUSTOMER + phone + HIDDEN` ⇒ 以 SALES 令牌读列表/详情/公海，三处 `phone` 均为 `null`；以 ADMIN 令牌读同样接口，`phone` 为真值；`PUT` 省略 `phone` ⇒ 库中值不变。
3. **导出档**：SALES 触发客户导出 ⇒ xlsx 的 `电话` 列在、格空。

---

## 明确不做

- **不接 LEAD/TICKET/ORDER/CONTRACT 等实体的内置字段**（用户裁决的实体范围）。
- **不加必填字段**（`name`/`company`/`customerId`）——HIDDEN 一个必填字段会让实体**完全不可编辑**；⇒ 上游点名的「客户名」这一半本项不覆盖，且**不假装覆盖**。
- **不改 056 的任何文件**（授权依据）；**不改 `V64`**；**不改 `V87` 与 `RoleConstants` 的历史论述**。
- **不修**自定义字段 READ_ONLY 被省略即删除的既有缺陷（债务 2）；**不改** 063 的脱敏口径与 `mask()` 的 fail-open（债务 4/8）。
- **不引入全局 `@JsonInclude`**（会改动全仓所有响应体形状）；**不给收口点加缓存**。
- **不新增服务层的 `permission` 白名单**（`@Pattern` 已覆盖，事实 12）。
- **不给掩码做「字段级豁免清单」**、**不新增 opt-out 注解**（那是一套新的对外配置面）。
- **不动 `.i18n-keys/**`**（gitignored 的遗留临时件）、**不动 `frontend/coverage/**`**。
- **不碰 `.specify/feature.json`**；**不跑任何 `/speckit-*`**。

---

## 风险

| 风险 | 缓解 |
|---|---|
| 收口点遍历对象图时踩 JDK 类型 ⇒ `InaccessibleObjectException` / 性能塌方 | JDK 类型白名单跳过 + 身份集环guard + 深度上限；ADMIN/匿名走快路径 |
| 就地置 null 毒化某个未来的缓存 | 今天三个缓存都不存出参 DTO（**证据排除**，不是推理）；D14 如实记为「**无判据看着**」 |
| 写侧回补把**非掩码**字段也回补 ⇒ 正常编辑被吞 | `snapshot/restore` **只**遍历注册表条目，且只回补 plan 命中的键；T10/T11 用**非掩码**字段做正对照 |
| `restore` 顺序错（放在 `apply` 之前） | 计划里**逐字钉死**顺序 + D5 专杀 |
| 掩码按父对象传播 ⇒ 误伤 CONTACT / SALES_OPPORTUNITY 的字段 | 按实体判定（事实 13）+ 用例覆盖嵌套载体 |
| 新类进分母挤压 0.73 余量 | 4 个类全部由 T1–T7 真覆盖；`jacoco:check` 结论行必须打印；**不接受**用 `common/**` 躲门禁 |
| `field_id` 改可空后既有自定义字段路径被波及 | 实测已否掉「toMap NPE」（事实 15），真风险是 `= NULL` ⇒ 按 identifier 分支 + T6/T7 |
| `INSTALL.md` 只加 V91 行 ⇒ 看着像本项跳号 | 同批**补 V90 行**并带日期 ⚠️ 标注「本行此前缺失」（**既有**遗漏） |
| 键数/行数三处落点漏改 | C5 一次收齐，判据 = **旧值仍可 grep** + `i18n:check` 实测 |
| 中文断言踩 ISO-8859-1 | 优先断 `error.code`；确需断文案时显式 `StandardCharsets.UTF_8` |
| spotless 重排打断行式留痕判据 | 引用长句的留痕用 `<br>` 自占一行，改完重跑 `spotless:apply` 确认不被折回 |
| 交付读数被后一次 `mvn test` 冲掉 | 门禁后不再跑 Maven；引用**整次** verify 的读数并写明出处 |
| **上游那句「客户名」被读成本项已覆盖** | `:273` 的 ⚠️ 块**必须**写明必填字段永久排除；`spec.md` §6 与 `:367` 的 ⚠️ 一起交代 |

---

## 提交纪律

`ListAgents` 确认无并行会话写同一批文件 → **逐路径 `git add`，禁用 `git add -A` / `git commit -a`**（本仓多会话共用工作区；同文件里的对方 hunk 也会被扫走）→ 提交信息末尾带 `Co-Authored-By: Claude Code <noreply@anthropic.com>`（逐字，见本会话的归属声明）。**不把提交自己的哈希写进它携带的文件**；**复选框绝不回填/预勾**（`tasks.md` 只在交付时勾）。
**订正不静默**：原文逐字保留 + 带日期 ⚠️ 块，粒度到**每一列**，自查判据是「**旧值仍能被 grep 到**」（排除行首注释后统计，并显式许可测试里的负断言）。`tasks.md` 的 `## 实做订正` 是**三列**。
若同伴工作被卷入，用 `git reset --soft` 重做，**绝不修改或丢弃另一会话的未提交工作**。
