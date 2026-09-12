# 契约：多币种端点的授权语义（084）

**Type**: 授权语义契约（非请求/响应结构契约）

**Why this file exists**: 与 `authorization-semantics.md` 同因，但是**第二处**同类的端点授权语义变更。

084 的新增文件 `contracts/authorization-semantics.md` 在 §1 明确把范围限定为「`CustomObjectController` 的对象定义端点」，
其理由在 §5 写明：本次的实质扩权点是**数据分析师对自定义对象定义**的能力。然而同一规格在 T020 又发生了**第二处**同类变更——
`CurrencyRateController` 的 5 个守卫也由角色字面量改为权限码（V86）。它不在第一份契约的范围内，也一度不在 `plan.md` 的
D1/D2/D3、不在 `spec.md` 的 FR-N24 批准记录之内，**唯一留痕是 `V86` 的 SQL 注释，且那条注释只写「已批准」，没有批准人姓名**。

按章程**原则一（契约不得被静默修改，不可协商）**与**治理节（任何偏差必须明确说明理由并经批准）**，以及 **FR-N24**
（「必须在本规格内记录批准人与批准日期后方可实施」）的措辞——记录必须落在**规格文档**里、且必须**署名**。本契约与本节
即为补上这一条。**这不是对「要不要改」的重新审议**：用户已于 2026-09-12 就 T015 报出的最后一条 C1 红项裁决「接线到权限码」
（而非「收回菜单授权」）。本契约补的是**记录位置与署名**，不是决策本身。

> **为什么不扩写第一份契约、而是另起一份**：第一份契约的 §1 范围声明与 §6 批准记录是**已批准的在案文本**。
> 把它的范围改写为「涵盖多币种」需要修改一段已生效的批准记录，并使「批准了 A」与「批准了 A+B」在同一段落里混为一谈。
> 两处变更分属不同模块、不同权限码集合、不同受影响角色，各自独立可评审，故按模块各留一份，交叉引用。

---

## 1. 范围

只覆盖 `CurrencyRateController`（`/api/v1/currencies`）的 5 个端点。本规格不改动其他任何 Controller 的鉴权注解。

## 2. 变更前后对照

| 端点 | 变更前的判定 | 变更后的判定 | 判定语义是否变化 |
|---|---|---|---|
| `GET /api/v1/currencies`（汇率列表） | `hasAnyRole('ADMIN','SALES')` | `@RequirePermission("currency:read")` | **是**（且该码为本次新增，见 §3） |
| `POST /api/v1/currencies/convert`（金额折算） | `hasAnyRole('ADMIN','SALES')` | `@RequirePermission("currency:read")` | **是** |
| `POST /api/v1/currencies` | `hasRole('ADMIN')` | `@RequirePermission("currency:manage")` | **是** |
| `PUT /api/v1/currencies/{id}` | `hasRole('ADMIN')` | `@RequirePermission("currency:manage")` | **是** |
| `DELETE /api/v1/currencies/{id}` | `hasRole('ADMIN')` | `@RequirePermission("currency:manage")` | **是** |

**变更前状态的事实依据**：`git show b44a2ca~1:backend/src/main/java/com/crm/controller/CurrencyRateController.java`
——`:39` 与 `:70` 为 `hasAnyRole('ADMIN','SALES')`（列表、折算），`:47`、`:54`、`:62` 为 `hasRole('ADMIN')`（增、改、删）。

**请求/响应结构与状态码口径**：全部不变。仅「403 还是正常响应」这一维度会变（对被新放行的角色而言从 403 变为正常响应）。

## 3. 涉及的两个权限码

| 码 | 类型 | 授予 | 理由 |
|---|---|---|---|
| `currency:read` | **新增**（`RoleConstants.PERMISSION_DEFS`，`common/RoleConstants.java:333`） | `ADMIN`、`SALES`、`FINANCE_MANAGER`（`V86`） | 「能看汇率」与「能改汇率」不应是同一个集合。若拿写码当读码，则只读角色要么看不到、要么连带拿到改汇率的能力——`@RequirePermission` 的类注释写的就是这条判据 |
| `currency:manage` | **既有**（`V75:228` 授予 `FINANCE_MANAGER`；全仓仅此一处授予） | **不新增** | `FINANCE_MANAGER` 从 `V75` 起就持有它，却因类级角色字面量恒 403——「矩阵上写了、实际拿不到」的典型。本次是让**已发布的矩阵兑现**，不是新授 |

## 4. 生效后的授权结果（可验证的预期）

| 主体 | 变更前 | 变更后 |
|---|---|---|
| `ADMIN`（内置管理员） | 可访问，凭角色名 | 可访问；**不依赖 `currency:read`**——`PermissionAspect.checkPermission` 对 `ADMIN` 内建恒放行（`security/PermissionAspect.java`）。`V86` 仍把它列入授予，是为保持角色页勾选状态与字典一致 |
| `FINANCE_MANAGER` | **一律 403**（既非 `ADMIN` 也非 `SALES`），尽管它持有 `currency:manage`、且 `V75` 已授予它「多币种」菜单 | 可读（新增读码）**也可写**（其 `V75` 既有的 `currency:manage` 第一次真的生效） |
| `SALES` | 可读、可折算（凭角色字面量）；不可写 | 可读、可折算（凭 `V86` 补授的读码，**保持现状不变**）；仍不可写（不持有 `currency:manage`） |
| 其他角色（`SUPPORT`/`VIEWER` 等） | 403 | **仍 403**（既不持有这两个码，也未被授予「多币种」菜单） |

**这一行是本契约的重点**：与第一份契约相同，变更**没有**把端点放开给「所有登录用户」，而是从「角色名集合」换成「显式可审计的权限码」。
授权的**强制点仍在服务端**（章程原则三）：`@RequirePermission` 由切面在 Controller 方法调用前判定，前端不参与该判定。

**扩权范围的如实说明**（与 `V86` 的注释一致，此处为准）：

- `FINANCE_MANAGER` 的能力从「看得见、打不开」变为「能读也能改」——**这是本契约唯一的实质扩权**。其中的「改」来自它
  早已持有、却从未被校验过的 `currency:manage`；按用户确认的口径「接受角色可用范围扩到其**已发布矩阵所宣称**的范围」，
  这不是新增授予，而是兑现既有承诺。
- `SALES` 的读码是**补授以维持现状**，不是扩权：它在改造前凭角色字面量本就能读汇率与做折算。写码不授——它改造前也改不了汇率。
- 除上述外，**无任何角色获得任何新能力**；新增/删除币种仍只有 `ADMIN` 与 `FINANCE_MANAGER` 能做。

## 5. 与第一份契约的关系

两者是**同一决定的第二次应用**，不是两项政策：

| | `authorization-semantics.md`（契约一） | 本契约（契约二） |
|---|---|---|
| 模块 | `CustomObjectController` 对象定义端点 | `CurrencyRateController` 全部端点 |
| 变更形态 | 类级 `hasRole('ADMIN')` → 按权限码 | 逐端点角色字面量 → 按权限码（读写分码） |
| 新增读码 | `custom_object:read`（`V85`） | `currency:read`（`V86`） |
| 实质扩权主体 | `ANALYST` 获得对象定义能力 | `FINANCE_MANAGER` 获得汇率读写能力 |
| 触发来源 | 菜单已授、端点恒 403（FR-N01 断链） | 同左（`V86` 注释称此为该批次的「最后一条 C1 红项」） |

同一处置在 `V80`–`V84` 已批量应用过（`V84` 新增 `custom_field:read` 的手法与本契约完全一致）。

## 6. 批准

| 项 | 值 |
|---|---|
| 批准人 | **龙星** |
| 批准日期 | **2026-09-12** |
| 关联需求 | FR-N06（补齐权限码的处置）、FR-N24（批准记录） |
| 覆盖的决策 | T015 报告的 C1 红项（`FINANCE_MANAGER × currencies`）：用户 2026-09-12 裁决「接线到权限码」而非「收回菜单授权」 |

已批准，本契约对应的改动（新增读码 `currency:read`、授予 ADMIN/SALES/FINANCE_MANAGER、`CurrencyRateController` 五个守卫改为按码放行）可以实施。

**本契约对批准记录的补充**：`V86` 的 SQL 注释原先只写「（FR-N24，需批准的偏差，已批准）」而**无批准人姓名**。该注释是留痕，
但它不是 FR-N24 所要求的记录位置，本条予以补全并署名。SQL 注释保留原文不改（历史留痕），其权威性以本节为准。

## 7. 验证方式

- **静态**：`MenuAccessGrantAlignmentTest` 的授权护栏——「菜单已授 ⇒ 持有对应权限码」必须对 `FINANCE_MANAGER × currencies`
  成立，且断言该码确实被某个端点校验（否则该码是死码，断言同样变红）。
- **端到端**：`PermissionEnforcementIT.menuIaBatchGatesByCode`（`integration/PermissionEnforcementIT.java:697`）——
  覆盖「已授角色真能打开」与「无码者仍被挡住」两侧；本契约的读/写两侧均在该用例的断言范围内。
- **手工**：`quickstart.md` 的手工场景——以 `FINANCE_MANAGER` 身份的账号登录，多币种页可读且可增删改；以 `VIEWER` 身份登录，
  同一路径仍为 403。
