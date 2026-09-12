# 契约：自定义对象定义端点的授权语义（084）

**Type**: 授权语义契约（非请求/响应结构契约）

**Why this file exists**: 项目章程原则一「契约优先」为**不可协商**，且规定「契约不得被**静默**修改」。
本规格不新增端点、不改任何请求/响应结构，但**改变既有端点的授权判定语义**——原先由角色名（`hasRole('ADMIN')`）把守的端点，改为由权限码把守。按 V80–V84 已确立的口径（「无端点变更则无契约、有端点语义变更则有契约」），此处必须留档。
规格假设 2 的原文声称「只调整谁能通过闸门，不改变闸门的判定语义」，与本契约不相符——该偏差已记于 `plan.md` 的「与规格假设的偏差」D2，待批准后回改。

---

## 1. 范围

只覆盖 `CustomObjectController`（`/api/v1/custom-objects`）的**对象定义**端点。
`/{id}/records*` 系列的 5 个端点（自定义对象**记录**的数据面，现为 `hasAnyRole('ADMIN','SALES')`）**不在本契约范围内**，本次不动。

## 2. 变更前后对照

| 端点 | 变更前的判定 | 变更后的判定 | 判定语义是否变化 |
|---|---|---|---|
| `GET /api/v1/custom-objects`（对象定义列表） | `hasRole('ADMIN')` | `@RequirePermission("custom_object:read")` | **是**（且该码为本次新增，见 §3） |
| `POST /api/v1/custom-objects` | `hasRole('ADMIN')` | `@RequirePermission("custom_object:create")` | **是** |
| `PUT /api/v1/custom-objects/{id}` | `hasRole('ADMIN')` | `@RequirePermission("custom_object:update")` | **是** |
| `POST /api/v1/custom-objects/{id}/toggle` | `hasRole('ADMIN')` | `@RequirePermission("custom_object:update")`（拟；实施时确认） | **是** |
| `DELETE /api/v1/custom-objects/{id}` | `hasRole('ADMIN')` | `@RequirePermission("custom_object:delete")` | **是** |
| `GET/POST/PUT/DELETE /{id}/records*` | `hasAnyRole('ADMIN','SALES')` | **不变** | 否 |

**请求/响应结构与状态码口径**：全部不变。仅「404 还是 403 / 还是 200」这一维度上的结果会变（对被新放行的角色而言从 403 变为正常响应）。

## 3. 新引入的权限码

| 码 | 类型 | 授予 | 理由 |
|---|---|---|---|
| `custom_object:read` | **新增**（`RoleConstants.PERMISSION_DEFS`） | `ADMIN`、`ANALYST` | 定义列表页首屏必须能读；字典里原本只有写码，拿写码当读码会让「只读地打开页面」需要创建权限——那是错的口径 |
| `custom_object:create/update/delete` | **既有**（`V75:252-254` 已授 `ANALYST`） | 不变 | 本规格不新增授予，只让既有授予**第一次真的生效** |

## 4. 生效后的授权结果（可验证的预期）

| 主体 | 变更前 | 变更后 |
|---|---|---|
| `ADMIN`（内置管理员） | 可访问，凭角色名 | 可访问，凭全部四个码（迁移中授予，保持角色页勾选状态与实际一致） |
| `ANALYST` | **一律 403**（角色名不符），尽管它持有三个写码、且菜单里被授予了「自定义对象」 | 可访问：读凭新增读码；写凭 `V75` 早已授予的三个码 |
| 其他角色（`SALES`/`SUPPORT`/`VIEWER` 等） | 403 | **仍 403**（既不持有这四个码，也未被授予「自定义对象」菜单） |

**这一行是本契约的重点**：变更**没有**把端点放开给「所有登录用户」，而是从「一个隐式的角色名」换成「一组显式的、可审计的权限码」。授权的**强制点仍在服务端**（章程原则三），撤掉的只是前端那道按角色名整组隐藏的 UI 门（FR-N01）。

## 5. 先例：这不是新政策，而是同一决定在相邻模块的重复

`CustomFieldController` 走的是同一条路：`V75` 已给 `ANALYST` 授 `custom_field:create/update/delete`，而该 Controller 长期是类级 `hasRole('ADMIN')`；`V84` 撤门接线并**新增了 `custom_field:read` 读码**（该迁移 §3 原文即「本迁移新增读码」），此后 `ANALYST` 真的能配置自定义字段。

本契约把同一处置应用到 `custom_object`。批准人可据此把它视为**一致性修正**（把一处遗漏的对齐补上），而非一次新的扩权政策——但因其事实后果是「`ANALYST` 获得对象定义能力」，仍按规格 FR-N24 要求记录批准人与日期。

## 6. 批准

| 项 | 值 |
|---|---|
| 批准人 | **龙星** |
| 批准日期 | **2026-09-12** |
| 关联需求 | FR-N06（补齐权限码的处置）、FR-N24（批准记录） |

已批准，本契约对应的改动（新增读码 `custom_object:read`、授予 ADMIN/ANALYST、撤掉 `CustomObjectController` 定义端点的类级角色门）可以实施。

## 7. 验证方式

- **静态**：`MenuAccessGrantAlignmentTest` 的 C1/C2 断言（见 `research.md` 决策 ③）——它会证明「持有 `custom-objects` 菜单的角色都持有 `custom_object:read`」，并证明该码确实被某个端点校验（否则该码是死码，断言同样变红）。
- **端到端**：`quickstart.md` 的手工场景——以 `ANALYST` 身份的账号登录，侧边栏出现「数据分析 → 自定义对象」，点开不 403，列表有数据。
