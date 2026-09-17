# 契约：字段级权限的内置字段（102）

**Base**: `/api/v1/field-permissions`（`@RequirePermission("field_permission:manage")`，056 的 1.5 收口）
**上游**: `CRM_FEATURE_COMPARISON.md` P0 第 1 条 + 2.9 节 FLS 行
**日期**: 2026-09-17

---

## 1. 为什么这份契约存在

章程原则一要求「契约不得被静默修改」。本项对**对外可观测行为**有四处变更，其中三处**改了请求/响应形状**：

1. **新增端点** `GET /field-permissions/available-fields?entityType=X`（此前不存在，管理员的字段下拉只列自定义字段）。
2. **`POST /field-permissions` 的请求体**：`fieldId` 由**必填**改为**可选**、新增 `fieldKey`（两者二选一）。
3. **响应体掩码**（新行为）：被配成 HIDDEN 的内置字段，在**受限角色的响应里呈 `null`**——这是一条全新的可观测语义。
4. **导出**：xlsx 里该字段**格空**（列头保留）。

⚠️ **同时必须说清一件相反的事**：本项**不是**「偏离 056 的冻结契约」。056 自己把内置字段写成了**后续扩展**（§2.1 逐字）⇒ 本项是**兑现它**。**056 的全部工件一字不改**，也**不产生偏差台账**——方向与 101 相反（101 是 062 写下了不实行为 ⇒ 偏离 + 台账），**两者不得混用**。
⚠️ 但 056 的契约与**实现之间**本就有一组漂移（§2.2/§2.4 逐字 vs 实现），本项**不改 056**，把漂移在**本文件与 `research.md` §1.2 同纸存证**——否则「本项未偏离契约」这句话没有落脚点。

---

## 2. 被扩展/被取代的原文（**逐字保留，可 grep**）

### 2.1 056 的范围声明（`specs/056-field-permission/spec.md:46` 与 `:86`，**逐字**）

```
- 权限仅作用于自定义字段（内置字段不在 v1 范围，后续扩展）。
```

```
- v1 仅作用于自定义字段（内置字段权限后续扩展）。
```

### 2.2 056 契约的端点和请求体（`specs/056-field-permission/contracts/field-permission.md`，**逐字**）

` :7`：

```
配置列表（按角色/实体筛选）。**Query**: `roleId`、`entityType`、`page`、`pageSize`。
```

`:14`（响应项）：

```
    { "id": 1, "roleId": 2, "roleName": "销售", "entityType": "CUSTOMER",
      "fieldId": 5, "fieldName": "手机号", "permission": "READ_ONLY", "createdAt": "..." }
```

`:23`：

```
创建/更新（覆盖式 upsert：角色+实体+字段 唯一）。**Body**: `{ "roleId": 2, "entityType": "CUSTOMER", "fieldId": 5, "permission": "READ_ONLY" }`。**Response 201**。
```

`:25`：

```
## PUT /field-permissions/{id} / DELETE /field-permissions/{id}

编辑 / 删除。
```

### 2.3 056 契约的错误码表（同文件 `:46-53`，**逐字**）

```
| code | status | 含义 |
|---|---|---|
| FIELD_PERMISSION_INVALID | 422 | 权限值不合法（须 HIDDEN/READ_ONLY/EDITABLE） |
| FIELD_HIDDEN | 422 | 该字段对当前角色隐藏，不可写入 |
| FIELD_READ_ONLY | 422 | 该字段对当前角色只读，不可修改 |
| FIELD_PERMISSION_NOT_FOUND | 404 | 权限配置不存在 |
```

### 2.4 056 数据模型的列定义（`specs/056-field-permission/data-model.md`，**逐字**）

`:10` / `:12` / `:17`：

```
| role_id | BIGINT | NOT NULL | 角色 |
| field_id | BIGINT | NOT NULL | 自定义字段 id |
唯一约束：`uk_field_perm`（role_id, entity_type, field_id）。
```

### 2.5 实现在 056 当时的实际形状（**对照用，实测**）

| 面 | 056 契约/模型写的 | 实现实际是 |
|---|---|---|
| Query 参数名 | `roleId` | **`roleCode`**（`FieldPermissionController.java:45`） |
| 响应项标识 | `"roleId": 2, "roleName": "销售"` | **`roleCode`**（字符串），**无 `roleName`**（`FieldPermissionResponse.java:11`） |
| **`fieldName`** | 契约里**有值**（`"手机号"`） | **字段声明了但从不赋值**（`setFieldName` 全仓只出现在 `CustomFieldService.java:295,348`，作用于另一个 DTO） |
| `PUT /field-permissions/{id}` | 契约里有 | **不存在**（实现只有 GET / POST / DELETE，`FieldPermissionController.java:41,52,61`） |
| 唯一约束的列 | `role_id` | **`role_code`**（`V64`） |
| 错误码 | 4 枚 | 4 枚**都在 `ErrorCode` 里声明**，但 **`FIELD_PERMISSION_INVALID` 与 `FIELD_PERMISSION_NOT_FOUND` 全仓从未被抛出**（`grep` 实测：两枚各只命中 `ErrorCode.java:125,128` 一处声明） |

⚠️ 最后一行是本项的直接动因之一：`FIELD_PERMISSION_INVALID` 从 056 起**声明了 8 个多月、一次都没抛过** ⇒ 它的**首个**用途出现在本项（`upsert` 的「恰好一个非空」判定，§3.2）。

---

## 3. 变更对照

### 3.1 新增端点：可配字段列表

```
GET /api/v1/field-permissions/available-fields?entityType=CUSTOMER
```

**Response 200**（复用 `PageResult` 形）：

```json
{
  "items": [
    { "fieldId": null, "fieldKey": "phone", "fieldName": "电话", "builtin": true },
    { "fieldId": 5,    "fieldKey": null,    "fieldName": "手机号", "builtin": false }
  ],
  "total": 2, "page": 1, "pageSize": 20
}
```

- `fieldName`：内置项由注册表提供中文标签（照 `CUSTOMER_HEADERS` 的既有做法，中文由后端给）；自定义项取既有来源。
- **未知 `entityType` ⇒ 422 `FIELD_PERMISSION_INVALID`**（**不静默返回空表**——空表会让「这个实体没有可配字段」与「我拼错了实体名」不可区分）。
- 权限门与既有端点一致（`field_permission:manage`）。

### 3.2 `POST /field-permissions` 的请求体

| 面 | 前（056 起） | 后（102） |
|---|---|---|
| `roleCode` | `@NotNull` | `@NotNull`（**不动**） |
| `entityType` | `@NotBlank` | `@NotBlank`（**不动**） |
| `fieldId` | `@NotNull Long` | **`Long`（去掉 `@NotNull`）** |
| `fieldKey` | — | **`@Size(max=64) String`（新增）** |
| `permission` | `@NotBlank` + `@Pattern(^(HIDDEN\|READ_ONLY\|EDITABLE)$)` | **一字不动** |

**新增的 422 判定**（由 `FieldPermissionService.upsert` 抛出，**Bean Validation 表达不了跨字段规则**）：

| 条件 | code |
|---|---|
| `fieldId` 与 `fieldKey` **都为空** | `FIELD_PERMISSION_INVALID` |
| `fieldId` 与 `fieldKey` **都非空** | `FIELD_PERMISSION_INVALID` |
| `fieldKey` **不在注册表里**，或**不属于该 `entityType`** | `FIELD_PERMISSION_INVALID` |

⇒ **该码自 056 起首次真正被抛出**（§2.5 末行）。

### 3.3 响应体掩码（**新的可观测语义**）

对**受限角色**（非 ADMIN）而言，被配成 **HIDDEN** 的**内置**字段，在下列响应里**呈 `null`**：

- 列表段（`PageResult.items`）、详情、**公海列表**（走的是另一份装配代码）；
- 两层信封（`ApiResponse.data` 与 `PageResult.items`）内部；
- `*DetailResponse` 子类（`CustomerDetailResponse` / `OpportunityDetailResponse`）**同样**被覆盖。

**四条边界（必须同时被告知，否则会被读成比实际更强）**：

1. **ADMIN 恒不受限**（与自定义字段同口径）；**未配置 ⇒ 逐字直通**（默认零影响）。
2. **置 `null`，不删键** ⇒ 与「这个字段本来就没值」**不可区分**（这是**有意**的：无存在性侧信道）。
3. **掩码按「字段所属实体」判定，不向子对象传播** ⇒ `ContactResponse.phone/email/remark`（属 CONTACT）与 `SalesOpportunityResponse.amount`（属 SALES_OPPORTUNITY）**不被** CUSTOMER/OPPORTUNITY 的配置遮蔽（**刻意避免误伤**，同时**登记为本项未覆盖**）。
4. **只覆盖 CUSTOMER + OPPORTUNITY 的 11 个字段**；**必填字段永久排除** ⇒ 上游点名的「**客户名**」这一半**本项不覆盖**。

### 3.4 写路径（入参）

| 输入 | 结果 |
|---|---|
| 提交 HIDDEN 内置字段 | **422 `FIELD_HIDDEN`** |
| 改 READ_ONLY 内置字段（值不同） | **422 `FIELD_READ_ONLY`** |
| **原样回传** READ_ONLY 值 | **成功**（正对照） |
| **省略** HIDDEN/READ_ONLY 字段 | **回补库中原值**（**这是本项的核心修复**：此前省略即被覆盖成 null/`0`） |
| **create** 时的 HIDDEN 字段 | 省略 ⇒ 取默认值；提交 ⇒ 422 ⇒ **等价于「不可设置」**（**明文声明**，不是漏做） |

### 3.5 导出

被配成 HIDDEN 的内置字段，在受限角色触发的 xlsx 导出里：**列头保留、格为空**（照自定义字段的既有先例）。**三个导出入口适用**，含**定时导出**（按**任务业主**的角色算，而非环境主体）。

### 3.6 明确**不变**的部分

- `permission` 的三态值域与语义（HIDDEN 提交 422 / READ_ONLY 必须原样回传）**逐字不变**。
- `GET /field-permissions` 与 `DELETE /field-permissions/{id}` 的**形状不变**（`roleCode` / 无 `roleName` 这几处**既有漂移**保持原样，**本项不修**，见 §5）。
- **自定义字段的 FLS 行为逐字不变**（本项只**增**内置字段这条路，不改既有路径）。
- **权限码零新增**（沿用 `field_permission:manage`）；**错误码零新增**（复用 056 的三枚）。
- **不引入全局 `@JsonInclude`** ⇒ 其余所有响应体的形状**一字不变**。

---

## 4. 生效后的可观测行为（可验证的预期）

| # | 预期 | 由谁钉 |
|---|---|---|
| 1 | SALES 配 `CUSTOMER+phone+HIDDEN` 后，列表/详情/**公海**三处 `phone` 均 `null`；ADMIN 为真值 | T8 |
| 2 | 商机同理，且 `OpportunityDetailResponse` 也被覆盖 | T9 |
| 3 | `PUT` 省略 HIDDEN 的客户字段 ⇒ **库中仍是原值** | T10 |
| 4 | `PUT` 省略 HIDDEN 的 `expectedAmountMin` ⇒ **保持 5000 而非 0** | T11 |
| 5 | 提交 HIDDEN ⇒ 422 `FIELD_HIDDEN`；改 READ_ONLY ⇒ 422 `FIELD_READ_ONLY`；**原样回传 ⇒ 成功** | T12 / T13 |
| 6 | `upsert` 双空/双非空/未知 `fieldKey` ⇒ 422 `FIELD_PERMISSION_INVALID`；**同键保存两次是 UPDATE** | T4 / T5 / T6 |
| 7 | `available-fields` 含内置 11 条 + 自定义 N 条；未知 `entityType` ⇒ 422 | T14 |
| 8 | xlsx 里该列**在**、该格**空**；**定时导出（业主受限）同样** | T15 / T16 |

---

## 5. 与 056 的关系（边界声明）

- **056 一字不改**：`spec.md` / `plan.md` / `data-model.md` / `contracts/field-permission.md` **全部保持逐字**（授权依据：§2.1 它自己写的是「后续扩展」）。
- **本契约不取代 056 的契约**，而是**在其之上追加**内置字段这一支；两处冲突时**以本契约为准**（因为它是后订的，且 056 契约的 `PUT` 一行**从未被实现**）。
- §2.5 那组漂移**逐条登记、逐条不修**（`roleId` vs `roleCode`、`fieldName` 从不赋值其一由本项**顺带修好**、`PUT` 端点不存在、`FIELD_PERMISSION_*` 两码从未抛出其一由本项**首次使用**）。
- ⚠️ **`FieldPermissionResponse.fieldName` 的赋值**是本项**唯一**改到 056 覆盖面的地方（原来**只声明、从不赋值**）⇒ 它让 056 契约里 `"fieldName": "手机号"` 那一行**从假变真**，属**收口**而非偏离。

---

## 6. 验证方式

| 面 | 用例 |
|---|---|
| 注册表与判据源 | `BuiltinFieldRegistryTest`（T1/T2）、`FieldMaskPlannerTest`（T3） |
| 配置面与不变式 | `FieldPermissionServiceBuiltinTest`（T4–T7）、`FieldPermissionAvailableFieldsIT`（T14） |
| 读侧掩码（含公海/详情子类/两种信封） | `BuiltinFieldMaskingIT`（T8/T9） |
| 写侧回补与 422（**两条旗舰**） | `BuiltinFieldWriteGuardIT`（T10–T13） |
| 导出（含**无请求主体**的定时导出） | `BuiltinFieldExportIT`（T15/T16） |
| 配置界面可用性 | `FieldPermissionPage.test.tsx`（T17） |
| 默认档零影响 | 既有全量用例**逐字全绿**（未配置 ⇒ plan 为空 ⇒ 收口点直通） |

⚠️ **假绿通道（写用例时必须对上）**：① 未配置状态下**所有**既有用例都绿，**不能**用来证明新机制正确；② JSON 全绿**不代表** xlsx 正确（导出不经过 JSON 收口点）；③ 定时导出**没有请求主体**，用 `SecurityUtil` 取主体的写法在单测里恒为 null。逐条详见 `research.md` §13.1。
