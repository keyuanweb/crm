# 102-builtin-field-permission：技术决策记录

**本文件记的是「为什么这么做」的机制级理由，以及**如实登记的缺口**。凡写「实测」的，都是读了实现或真跑过（含 H2 2.2.224 探针与 `Collectors.toMap` 探针的逐字读数）；凡属推断的，都写明是推断。

---

## §1 与 056 的关系：**兑现待扩展项**，不是偏离冻结契约

### 1.1 授权依据（`specs/056-field-permission/**`，**逐字回引，本项一字不改**）

| 落点 | 原文（逐字） |
|---|---|
| `spec.md:46` | `- 权限仅作用于自定义字段（内置字段不在 v1 范围，后续扩展）。` |
| `spec.md:86` | `- v1 仅作用于自定义字段（内置字段权限后续扩展）。` |
| `plan.md:25`（Constraints） | 同义表述（内置字段权限属后续扩展） |

⇒ **056 一期把「内置字段」明文留成待扩展项**，本项正是去兑现它。⚠️ **方向与 101 相反，两者不得混用**：101 是「062 的契约写下了不实行为 ⇒ 偏离 + 偏差台账」；本项是「056 自己写着『后续扩展』⇒ 现在扩展」。**故 056 全部工件一个字符不改，也不产生偏差台账。**

### 1.2 但 056 与实现之间**本就有一组漂移**（登记，**不改 056**）

这组漂移**不是本项造成的**，也不是本项要修的；登记它的唯一理由是：如果漂移不写清，「本项未偏离契约」这句话就没有落脚点。

| 面 | 056 契约/模型写的 | 实现实际是 | 处置 |
|---|---|---|---|
| 请求体标识 | `roleId: 2`（数字） | `roleCode VARCHAR(30)`（角色码字符串） | 登记 |
| 端点集 | 含 `PUT /field-permissions/{id}` | **该端点不存在**（只有 upsert 型 `POST`） | 登记 |
| 列定义 | `data-model.md:10,12,17`：`role_id BIGINT NOT NULL`、`field_id BIGINT NOT NULL`、唯一约束 `uk_field_perm（role_id, entity_type, field_id）` | `V64`：列名是 **`role_code VARCHAR(30)`**（索引也建在 `role_code` 上）、`field_id BIGINT NOT NULL` | 登记；`field_id` 的可空性本项**改在 V91**（不改 `V64`） |

⚠️ 登记**不等于**认可：这三处是 056 一期的既有事实，本项**既不修也不掩盖**（债务 8）。

---

## §2 为什么复用同一张 `field_permission` 表

用户裁决已定「复用同一张表」，这里只补**机制上的支撑与代价**：

- **支撑**：这张表的语义是「(角色, 实体) 上的字段权限行」，内置与自定义只差**标识方式**（`field_key` 字符串 vs `field_id` 外键指向 `CustomField.id`）。权限判定、ADMIN 直通、三态值域、upsert 形状**全部可以共用** ⇒ 新开一张表会让「同一件事有两套语义」。
- **代价（必须写明）**：`field_id` 必须**改可空**，于是「恰好一个非空」这条不变式**从 schema 层掉到应用层**——见 §3 的实测：**双 NULL 行在 H2 与 MySQL 都能插入**。本项接受这个代价，把它登记为**债务 1**，并用 `upsert` + T4/T5 守住。
- **否掉的替代**：① 新开 `builtin_field_permission` 表 ⇒ 判定逻辑、端点、前端三处都要分叉；② 内置字段权限不进 DB（代码常量表）⇒ 管理员**根本配不了**，等于本项没做（本项的理由就是「配置存在但语义为假」，换成「配置不存在」并没有更好）。

---

## §3 V91 的迁移形状，与「两条唯一索引」的实测支撑

### 3.1 形状

```sql
-- V91__field_permission_builtin_fields.sql（要点）
ALTER TABLE field_permission MODIFY COLUMN field_id BIGINT NULL;      -- 改可空
ALTER TABLE field_permission ADD COLUMN field_key VARCHAR(64) DEFAULT NULL;
ALTER TABLE field_permission ADD UNIQUE KEY uk_field_perm_builtin (role_code, entity_type, field_key);
-- 旧索引 uk_field_perm (role_code, entity_type, field_id) 保留不动
```

⚠️ **`V64__field_permission.sql` 不得编辑**（已应用的迁移永不编辑）。变更全部落在 V91。
⚠️ **不加 `CHECK` 约束**——本仓 89 个迁移里**零 `CHECK` 约束**，而且这**不是巧合**：`V79__opportunity_stage.sql:15` 留了明文先例「同理不做 CHECK 约束：停用一个阶段不应让历史行变成非法数据」。
（判据口径须自证：`grep -ri CHECK` 在迁移目录里**有 5 处命中**，逐条都不是约束——`V21` 的枚举值 `'TRANSFER/CASH/CHECK/OTHER'`、`V50` 的列名 `check_in_time`、`V79` 的**上面那句先例注释**、`V90` 的 `checksum` 与 `checkEntityVisible`。⇒ 说「零 CHECK」时**必须**同时给出这个口径，否则下一个人跑同样的 grep 会得到 5，并把一句真话读成假话。）

### 3.2 实测读数（H2 2.2.224，本项探针）

| 插入 | 结果 | 含义 |
|---|---|---|
| 同 (角色, 实体) 两行**内置**（`field_id` 均 NULL） | **通过** | H2 视多个 NULL **彼此不等** ⇒ **与 MySQL 同向**；⇒ 「两条唯一索引」的形状**可行**——每个 (角色,实体) 可以有**多行**内置配置 |
| 重复的**自定义**行（`field_id = 5`） | **被拒** | 旧索引 `uk_field_perm` 仍在工作（自定义路径没被本项放松） |
| **双 NULL 行**（两列都空） | **通过** | ⚠️ 「恰好一个非空」**没有** DB 级约束 ⇒ 只能由 `upsert` 保证（**债务 1**） |

⚠️ 这条实测是**形状可行性的全部依据**：如果 H2 把 NULL 当作彼此相等，第二条索引会拒绝**第二个**内置字段配置 ⇒ 本方案当场不成立。

---

## §4 为什么是**单一收口点**，而不是逐装配点调用

用户裁决已定方向，这里记**否掉逐点调用的机制理由**：

- 现有 FLS 范式是**pull 型**：15 个调用点在 Service 方法里自觉调 `readValues/readValuesBatch`（056 一期 + `008cbb9`）。**新增一条读路径忘了调，FLS 就静默失效**——这是「靠自觉」的范式。
- 本项要覆盖的是 **53 个文件里的手写装配点**（实测 `grep -rln` = 53，**不是** 57）+ 一条**第二份装配**（`CustomerPoolService.toResponse:216`）。逐点接入等于**再造 53 个可以被遗忘的点**，且每加一个响应 DTO 就多一个。
- 选**序列化**这一层（`ResponseBodyAdvice#beforeBodyWrite`）的收益：**容器（`ApiResponse.data` / `PageResult.items`）与子对象遍历是机械的**，装配点写在哪里都不影响判定；新增 DTO 只要在注册表覆盖范围内就**自动**被管。代价是：**导出不经过它**（见 §8/§9）、**`principal` 语义要写清**（见 §13 债务 4）。
- 实测支撑「没有别的收口点可用」：`@Json*` 注解 **0**、`HandlerMethodReturnValueHandler` **0**、HTTP 层 `WebMvcConfigurer`/`HandlerInterceptor` **0**、`aspect/` 目录**不存在**、`@RestControllerAdvice` 只有 `GlobalExceptionHandler`（只处理异常）⇒ **这一层是新建的，不是复用的**。

---

## §5 掩码形态：**置 `null`**，而不是删键

- 置 `null` ⇒ **与「这个字段本来就没值」不可区分** ⇒ **没有存在性侧信道**（客户端无法靠「键在不在」推断「被遮蔽了」）。这是选它的**安全**理由。
- 不选「删键」：需要按载体动态构造 Map 或改序列化器，**且会引入侧信道**。
- **不引入全局 `@JsonInclude`**（那是另一个层面的全局行为变更，会改动**全仓所有响应体**的形状）⇒ 代价是字段呈 `null` 而非缺键，**前端类型必须允许 null**（**债务 6**）。

---

## §6 写侧：为什么必须**回补**，且必须回补在 `apply` **之后**

### 6.1 为什么只有「回补」这一条路

逐字读 `FieldPermissionService.java:119-147` 得到的真实语义（**推翻两条转述**）：

- HIDDEN：**提交即 422**。
- READ_ONLY：判据是 `existing == null || !existing.equals(v.getValue())` ⇒ **`existing == null` 也算 changed ⇒ 422** ⇒ READ_ONLY =「**必须原样回传，任何差异都 422**」，**不是**「首次写入放行」。

⇒ 客户端**提交** HIDDEN 会 422、**改** READ_ONLY 会 422。**写侧唯一的缺口是「省略」**：客户端看不见（HIDDEN）就**必然省略**，而 `CustomerService.apply` / `OpportunityService` 的装配**无条件覆盖**。
⇒ 「拒绝省略」在语义上**不可实现**（HIDDEN 字段客户端根本没有能力提交，提了就 422）⇒ 正当处置只有**回补库中原值**（照 `saveValues` 的「看不见不等于该被删除」）。

### 6.2 为什么顺序**不可交换**（这是本项最容易被写错的一行）

`OpportunityService.create:120-121` 与 `update:145,147` 是 `req.getX() == null ? 0L : req.getX()` ⇒ 省略一个 HIDDEN 金额字段，落库的是 **0**（**比 null 更坏：一个看起来合法的值**）。

```
require(id) → snapshot → validateBuiltinWrite(422) → apply(含 null→0L) → restore → updateById
                                                        ↑ 必须在这一步之后
```

⇒ **`restore` 放在 `apply` 之前 ⇒ 0 会盖掉回补的原值**（D5 专杀这种写法；T11 是唯一能抓住它的用例）。
⚠️ 这个缺陷**今天就能复现**（无需本项的任何配置改动即可写出 `0`），本项只是让它**在掩码场景下**不再发生。

---

## §7 `create` 上 HIDDEN 的语义：**等价于「不可设置」**（明文声明，不是漏做）

`create` 没有「库中原值」⇒ **无值可回补**。于是 HIDDEN 字段在 create 上有且只有两种可能：省略 ⇒ 取默认值/null；提交 ⇒ 422。
⇒ **没有任何人能创建带该字段值的记录**。这是 HIDDEN 在 create 上的**唯一自洽语义**，但必须**明文声明**（`spec.md` §6、`contracts` §4），否则会被读成漏做；也意味着**若业务需要该字段在创建时可填，就不能配 HIDDEN**——**没有判据能检测「管理员配错了 HIDDEN」**（登记）。

---

## §8 导出：为什么照「**列在、格空**」，而不去删列

既有先例（实测）：`ExportExecutor.writeHeader` 为**全部**自定义字段写表头，而 HIDDEN 自定义字段的格子取 `valueByField.getOrDefault(fieldId, "")` ⇒ **空串**——因为过滤版 `readValuesBatch` 里**根本没有它们**。

⇒ 内置字段照此：**判定源接同一个 `FieldMaskPlanner`，格置空，列头不动**。
**不发明第二种读法**的理由：① 删列会让**列数随角色变化**，导出文件的表头结构不再稳定（下游按列号解析的脚本会错位）；② 「列在格空」是**本仓已经在用的**语义，改它是另一个规格。
⚠️ **导出不经过 §4 的收口点**（xlsx 不走 Jackson）⇒ **JSON 全绿不代表 xlsx 正确**，T15/T16 是唯一防守（**假绿通道②**）。

---

## §9 定时导出：为什么必须取**任务业主**的角色

`ScheduledExportServiceImpl.executeExport:216` 从**调度线程**进入 `executeExportWithRowCount` ⇒ `SecurityUtil.currentPrincipal()` 为 **null**。若沿用「无主体 ⇒ ADMIN 直通」的口径（这与 §4 收口点的 fail-open 同形），**定时导出会把掩码整体旁路掉**——而它恰恰是「无人值守地生成一份完整文件」的那条路径，泄漏面比交互式导出更大。

⇒ 本项**只**把**字段掩码**接到任务业主的角色（`userMapper` 已注入，取其角色）。
⚠️ **但这是一处混合态，必须写明**：063 的 `mask()`（打码）与 `visibleOwnersOrNull`（行级可见范围）在该路径上**仍然 fail-open** ⇒ **不得**含混成「定时导出已修好」（**债务 4**）。**D7** 专杀「用环境主体」的写法；**T16** 是唯一能抓住这条路径的用例。

---

## §10 `upsert` 重写：三处，以及两条实测

### 10.1 三处判定

① `fieldId` 与 `fieldKey` **恰好一个非空**，否则 **422 `FIELD_PERMISSION_INVALID`**（056 定义了却**从未抛过**的码）；
② `fieldKey` 必须在注册表中**且属于该 `entityType`**，否则 422；
③ 查询**按 identifier 分支**（`.eq(fieldKey)` vs `.eq(fieldId)`）。

### 10.2 两条实测（决定了第 ③ 条的写法）

| 探针 | 读数 | 后果 |
|---|---|---|
| `Collectors.toMap` 的 null 键 | `NULL-KEY OK -> {null=HIDDEN, 1=HIDDEN, 2=EDITABLE}`；`DUP-KEY THREW: java.lang.IllegalStateException`；`NULL-VALUE THREW: java.lang.NullPointerException` | ⚠️ **推翻「一个内置行会让自定义字段读路径 500」的假设**：null **键**合法 ⇒ 真后果只是 `hiddenFieldIds` 的 Set 里多一个 `null` 元素，**静默不洁而非崩溃** ⇒ 把内置行排除出 custom 映射仍然做，但**理由是卫生，不是防炸** |
| MyBatis-Plus `.eq(column, null)` | 生成 `column = NULL`（**永不匹配**） | ⚠️ `upsert` 不分支 ⇒ 第二次保存**必然再 INSERT** 并撞唯一键；`permissionFor(..., null)` ⇒ **回落 `EDITABLE`（fail-open）** |

⇒ `permissionForKey` **不得**退化成「传 null 给 `permissionFor`」（D10 专杀）。**债务 3**：加 javadoc + T7，但**没有机制**阻止后人误用。

---

## §11 注册表：为什么 `Map` 而不是 `Set`、为什么必须有 `@PostConstruct` 自检

- **入选规则**（写进 javadoc，防后人随手加）：① 用户可见的业务数据；② entity / request / response **三侧都有同名载体**；③ **不是必填**；④ `null` 不承载额外语义。
- **必须排除**（否则本项会**制造**新缺陷）：**必填字段**（`CustomerRequest.name`/`company` 是 `@NotBlank`、`OpportunityRequest.customerId` 是 `@NotNull`）——HIDDEN 一个必填字段会让实体**完全不可编辑**；`ownerId`（javadoc 是「空 = 公海」⇒ 置 null 不是「看不见」而是「**变成公海**」，**这是在说谎**）；`ownerName`（只在两条装配路径上被赋值，遮蔽会时有时无）；派生列（`customerName`、`salesOpportunityCount`，无对应实体列）。
- **`@PostConstruct validate()`** 用反射逐载体解析属性名与类型，**任一失败即让 ApplicationContext 启动失败** ⇒ **「属性名打错 = 静默空操作」在本设计下不可能发生**（这是本设计最关键的护栏；T1 专钉它，D11 反向证明它在承重）。
- **查找用 `Map<String, BuiltinField>`**（键 `entityType + ":" + fieldKey`），**不用** `Set<BuiltinField>` / `contains`：后者会把 record 的 `equals`/`hashCode` 拉进 jacoco 覆盖率义务（在 `support/**` **不是**排除包的前提下，那是白付的覆盖成本）。
- **条数（11）由测试断言钉住**：CUSTOMER 7（`contactPerson` `phone` `email` `address` `remark` `status` `campaignId`）+ OPPORTUNITY 4（`expectedAmountMin` `expectedAmountMax` `remark` `status`）。

---

## §12 隔离实例配方与冒烟口径

**硬约束**：**不与并行会话抢 8081 / 5173**；**不在共享开发库上写任何数据**（本项的读侧冒烟也要配置数据才能观测 ⇒ **必须**用隔离实例）。

```bash
# ① 独立 schema（MySQL 在 WSL 里；root 口令 123456；crm_user 无 CREATE DATABASE 权限）
#   ⚠️ 先确认 crm_user 的 host：SELECT user,host FROM mysql.user; —— 本仓实测是 localhost
#   （不是 %，用 % 会 ERROR 1410）。按查到的那个 host 写 GRANT。
# ② 独立端口 + 独立 schema + 另一个 Redis db 启动后端（直启 target/classes 或另跑 spring-boot:run）
# ③ 在隔离库里配权限行（本项必须配置才能观测掩码），然后跑三档冒烟：
#    a. 默认档（不配任何内置权限）⇒ 响应与改动前逐字相同
#    b. 配置档（SALES + CUSTOMER + phone + HIDDEN）⇒ SALES 三处响应 phone 为 null；ADMIN 为真值
#       ⇒ PUT 省略 phone ⇒ 库中值不变
#    c. 导出档 ⇒ SALES 的 xlsx 里「电话」列在、格空
# ④ 收尾（三项都要做，并核对共享库未动）：DROP 隔离 schema / REVOKE / FLUSHDB 另一个 db
# ⑤ 核对共享库未动 + 8081/5173 未重启（比对进程启动时间）
```

⚠️ 本项**没有**「只读冒烟」可用：掩码在**未配置**时是逐字直通（那是**默认档**该验的），要观测**生效**就必须往**隔离库**写配置行。

---

## §13 覆盖缺口与假绿通道（**如实登记**）

### 13.1 三条假绿通道（写用例时必须逐条对上）

| # | 通道 | 为什么全绿也不能采信 | 防守 |
|---|---|---|---|
| ① | **未配置 ⇒ plan 为空 ⇒ 收口点逐字直通** | 既有用例**全部**在「未配置」状态下跑 ⇒ **既有全绿不代表新机制正确** | T8–T13 必须**真的配置**权限行 |
| ② | **导出不经过 Jackson 收口点** | JSON 全绿与 xlsx 无关 | T15（xlsx）+ T16（无主体的定时路径） |
| ③ | **定时导出没有请求主体** | 用 `SecurityUtil` 取主体的写法在单测里**恒为 null** ⇒ 「看起来在取主体」的代码也是错的 | T16 必须把**任务业主**设成受限角色，且 D7 反向证明 |

### 13.2 债务 8 条（**必须同步写进 `specs/roadmap.md`**；只写在本文件里等于没登记）

1. **「`field_id` 与 `field_key` 恰好一个非空」无 DB 级约束**（实测：H2 与 MySQL 都允许双 NULL 行）⇒ 仅 `upsert` 保证；且 `SchemaParityIT` **自陈不覆盖列级漂移、标记位置与内容漂移** ⇒ 这类错行没有任何自动判据能发现。
2. **自定义字段的 READ_ONLY 被省略 ⇒ 值被删**（`CustomFieldService.java:254-265` 的原值回补**只覆盖 `hidden`**，**不含 READ_ONLY**）——**既有**缺陷、本项**不修**（属自定义字段族、056 契约覆盖的行为，修它需要自己的偏离登记）。⚠️ 这是本项研究中最有价值的**邻近发现**：本项在**内置**侧做了回补，而**自定义**侧的同类缺陷仍在。
3. **`permissionFor(role, entity, null)` 回落 `EDITABLE`（fail-open）** ⇒ 内置查找必须走 `permissionForKey`；有 javadoc 与 T7，但**没有机制**阻止后人误用。
4. **定时导出路径上的混合态**：本项只把**字段掩码**接到任务业主的角色；063 的 `mask()` 与 `visibleOwnersOrNull` 在该路径上**仍 fail-open** ⇒ **不得**写成「定时导出已修好」。
5. **覆盖范围有限**：只覆盖 CUSTOMER + OPPORTUNITY；`ContactResponse.phone/email/remark`（属 CONTACT）与 `SalesOpportunityResponse.amount`（属 SALES_OPPORTUNITY）**不被遮蔽**（按实体判定、不传播——刻意避免误伤）；**必填字段永久排除** ⇒ 上游点名的「**客户名**」这一半**本项不覆盖**。
6. **掩码后字段呈 `null` 而非缺键**（不引入全局 `@JsonInclude`）⇒ 与「无值」不可区分（**无侧信道**，这是有意的），但前端类型必须允许 null；产品若需区分「被遮蔽」与「空值」，那是另一个规格。
7. **收口点每响应算一次 plan、未加缓存**（换取「配置一改即生效」）⇒ 每响应 1–2 次查询，本项**不声称**它零成本。
8. **056 契约与实现的既有漂移**（`roleId` vs `roleCode`；`roleName` 不存在；`FieldPermissionResponse.fieldName` **只声明从不赋值**；`PUT /field-permissions/{id}` 不存在；`data-model.md` 的列定义；`FIELD_PERMISSION_INVALID` 与 `FIELD_PERMISSION_NOT_FOUND` **声明了但全仓从未被抛出**）⇒ 登记、**不改 056**。
   另：`CustomerExcelService` **不调** `mask()` ⇒ 实现时**先核实它的调用点权限门**，若对非 ADMIN 开放则 063 的脱敏在该路径 fail-open（**只登记，不改 063 口径**）。
   另（本次勘察**新发现**，**不修**）：`FieldPermissionRequest.roleCode` 用的是 `@NotNull`（`FieldPermissionRequest.java:12`）而**不是 `@NotBlank`** ⇒ `roleCode: ""` **能通过校验**并写进一行永不匹配任何角色的配置（**净效果与「未配置」相同 ⇒ fail-open EDITABLE，无害但脏**）。**不修的理由**：这是 056 契约覆盖的 DTO 的既有行为，收紧它要付一条新校验路径的覆盖成本，且与本项四条裁决无关。登记在此，供后续批次判断。

### 13.3 一条「**没有判据看着**」的结构性风险（不得写成「已验证无风险」）

**就地置 `null` 会毒化缓存——如果将来有缓存持有出参 DTO。** 今天以**证据**排除：全仓只有三个手工缓存（`rolePermissions` / `visibleOwnerIds` / `opportunityStages`），**没有一个存出参 DTO**；`@Cacheable` 被 `CacheConfig` javadoc 刻意禁用。
⇒ **D14 的预期是「破坏后仍全绿」**，且必须如实记为「**没有端到端判据看着这条结构性风险**」——**不许**写成「已验证无风险」。这是本项**唯一**一处「计划预期破坏不转红」的条目。
