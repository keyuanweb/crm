# 调研：086 不可收口清单收尾（096）

**Created**: 2026-09-16

本文件的核心不是过程记录，而是**对 086 那份清单的逐行判据复核**——哪 2 行是真缺口、哪 4 行是判据用错。
这与 086 把「不可收口清单」当交付物是同一种做法。

---

## 1 逐行复核表

086 `research.md` §2 的清单共 6 行。**逐行复核结论如下**（原文措辞见 §1.1）：

| # | 清单行 | 086 的判据 | 本项复核 | 处置 |
|---|---|---|---|---|
| 1 | `approval/ApprovalCenterPage` 通过/驳回/转交 | 「三个端点零注解、类上也无 `@PreAuthorize`。判权在 `checkApprover(task)`——按**任务分配人**放行，与角色码无关。挂任何码都会与后端判据不一致」 | 086 **对后端判据的描述完全正确**，但推出的「要收口需要什么」是**错的**——它建议「把审批权从任务分配改成角色码」，那是**削弱** | **改前端**（按归属渲染），后端一行不改——⚠️ **注意严重性**：这**不是**「当下可观察的假按钮」（列表端点本就按 `approverId` 过滤），而是**判据没有显式表达**，见 §3 的 2026-09-16 订正 |
| 2 | `exports/ExportCenterPage` 下载 | 「无注解。硬挂 `export:create` 会把『任何登录用户都能下载』变成『仅 SALES/SUPPORT/ADMIN 可见』，而后端仍放行 → **真切断**」 | 「硬挂号会真切断」**正确**；但把结论写成「后端先给 `:67` 加码」是**判据用错**——后端**已有**真实数据范围判定，**不该**加码 | **订正判据**（不该设码） |
| 3 | `custom-object/CustomObjectRecordPage` 删除记录 | 「只有 `@PreAuthorize("hasAnyRole('ADMIN','SALES')")`，5 个记录端点全无码，字典无 `custom_object_record:*` 族。挂 `custom_object:delete` 会**双向错**」 | **完全正确**，且本项实测发现它比原文更严重——**记录面根本没有数据范围过滤**（§2） | **建码族**（本项做） |
| 4 | `components/CommentSection` 删除评论 | 「类级 `@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")`，字典无 `comment:*`。类注释已书面裁决，单独立项」 | **完全正确** | **建码族**（本项做） |
| 5 | `stats/DashboardPage` 设置目标 | 「无注解，闸门是方法体内联的『`userId` 为空 ⇒ 仅管理员』。`kpi:view` 被 ANALYST 持有 → 挂它反而给 ANALYST 露出必然 403 的按钮」 | 086 的诊断**正确**；但「后端先加码」是**判据用错**——那是一处**数据范围判定**，**不该**加码 | **订正判据**（不该设码） |
| 6 | `components/FollowUpTimeline` 编辑 | 「有码，但该判据下只有『编辑』，属**排除类**（编辑不收口）」 | **正确** | 维持不收口 |

**净结果**：6 行里 **2 行是真缺口**（3、4），**2 行的处置判据用错**（2、5），
**1 行的处置是削弱**（1，改走前端），**1 行维持**（6）。

### 1.1 判据为什么用错——本仓有两条判据，086 用的是第三条

086 清单用的是一条**机械判据**：「**端点无权限注解 ⇒ 待加码**」。

本仓自己有两条更准的判据，**射程不同、不得互相顶替**：

- **`PermissionEnforcementIT` 类头**：「读接口什么时候需要设码」不看读不读，看
  **这条读路径有没有数据范围**——**有**范围过滤的不设码（范围已在保护），**没有**的必须设码。
- **`V81` 头注释判据①**：端点改造前**有闸门** ⇒ 补授范围 = 那个闸门原先放行的角色集合，
  一个不多一个不少（保留事实能力）。

**两族的射程**：

| 判据 | 回答的问题 | 本项用在哪 |
|---|---|---|
| 「有没有数据范围」 | **撤门之后要不要设码**（自限范围的模块撤门不设码） | 用于**确认换码是必须的**：两族都无范围过滤 ⇒ 必须设码（§2、§3） |
| V81 判据① | **换码时补授给谁** | 两族的**补授名单**全由它决定 |
| 086 的「无注解 ⇒ 加码」 | —— | **本项不用**：它把「端点无注解」直接等同于「缺保护」，于是对**已有范围判定**的两处（导出下载、仪表盘设目标）也开出了「加码」工单 |

> ⚠️ 本项两族的性质是「**换码**」（角色门 → 权限码），**不是**「撤门」，所以补授范围由判据① 决定；
> 第一条判据在此只用于**确认换码的正当性**。**写明这一点，是为了不让读者以为本项在绕开它。**

---

## 2 两族的实测事实

### 2.1 自定义对象记录面（真缺口，且比 086 记的更严重）

**端点**（`CustomObjectController`，5 个）：`records`（GET `/{id}/records`）、`recordDetail`、
`createRecord`、`updateRecord`、`deleteRecord`——**全部**只挂
`@PreAuthorize("hasAnyRole('ADMIN','SALES')")`，**无码**。

**改造前的真实闸门**：**只有那道角色门**。实测 `CustomObjectRecordService`：

```
grep -n "resolveVisibleOwnerIds|DataPermissionService|EntityAccessService|SecurityUtil|currentUserId" \
     backend/src/main/java/com/crm/service/CustomObjectRecordService.java
→ import com.crm.security.SecurityUtil;                            （仅 import）
→ record.setCreatedBy(SecurityUtil.currentUserId());              （仅写 createdBy）
```

**没有任何范围过滤调用。** 而三处文档**都声称记录面「靠数据范围过滤」**——这是**不实的**：

| # | 落点 | 原文（节录） | 处置 |
|---|---|---|---|
| ① | `CustomObjectController` 类 javadoc | 「改造前就是 `hasAnyRole('ADMIN','SALES')`，**且靠数据范围过滤**，不在 084 范围内」 | **订正**（原文留痕 + 带日期 ⚠️） |
| ② | `RoleConstants` 的 `custom_object` 组注释 | 「`/{id}/records*` **不设码**——那是业务面，**靠菜单 + 数据范围**」 | **订正** |
| ③ | `V87` 头注释 | 「记录面是业务数据、**由数据范围过滤**」 | **只登记不改**：已应用的迁移改动会破坏 Flyway checksum |

**② 的「靠菜单」也是错的**：菜单只控制**前端可见性**，不是服务端闸门
（084 的整个前提就是「菜单授权不构成访问控制」，见章程原则三）。且「自定义对象」菜单
**不在** SALES 的菜单表里（V46 授给 ADMIN；V85 补 `custom_object:read` 给 ADMIN/ANALYST），
所以它连「菜单」这半边也对不上。

**结论**：记录面是权限模型里唯一一处「**既无范围保护、又不可配置**」的写面（5 个端点含 3 个写）。
**必须设码**。

### 2.2 评论面（真缺口）

**端点**（`CommentController`，3 个）：`list`（GET）、`create`（POST）、`delete`（DELETE `/{id}`）。
**类级** `@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")`，方法级**无**。

**改造前的真实闸门**：类级门 **+** 服务层两处：

- `CommentService.checkEntityVisible`（`063`）：只对 `CUSTOMER`/`LEAD`/`OPPORTUNITY` 三种实体查
  `EntityAccessService`，`default -> true`（`TICKET` 等直通）；且 `ADMIN` 直接 return。
  ⇒ 它保证的是「**看得见就能评**」，**不是**范围过滤。
- `CommentService.delete`：`if (!comment.getAuthorId().equals(current) && !isAdmin) throw FORBIDDEN`
  ⇒ 「作者或管理员可删」。

**类注释已书面裁决**（`CommentController` 类 javadoc）：

> 要么给评论建码族并明确授予名单，要么把这些角色从协作场景里排除，二选一，不宜在权限接线里顺手决定。

**本项选「建码族 + 明确授予名单」**，名单 = 判据①（原门放行集合）。

---

## 3 补授名单（V81 判据①，零扩权零收窄）

原闸门放行的角色集合**逐字**取自注解字面量。⚠️ `hasAnyRole` 匹配的是**角色 code**，
13 个预置角色里只有 `ADMIN` / `SALES` / `SUPPORT` 三个**字面相等**——
`SALES_REP` / `SALES_MANAGER` / `SUPPORT_AGENT` / `SUPPORT_MANAGER` **都不等于** `'SALES'` / `'SUPPORT'`，
故**改造前就被这道门挡在外面**。

| 码族 | 原闸门 | 补授名单（判据①） | 码数 |
|---|---|---|---|
| `comment:*` | `hasAnyRole('ADMIN','SALES','SUPPORT')` | `ADMIN`, `SALES`, `SUPPORT` | 3 |
| `custom_object_record:*` | `hasAnyRole('ADMIN','SALES')` | `ADMIN`, `SALES` | 4 |

**13 个预置角色全表**（V46 三 + V75 十）：
`ADMIN` / `SALES` / `SUPPORT` / `SALES_MANAGER` / `SALES_REP` / `SUPPORT_MANAGER` / `SUPPORT_AGENT` /
`MARKETING_MANAGER` / `MARKETING_SPECIALIST` / `FINANCE_MANAGER` / `FINANCE_ACCOUNTANT` / `ANALYST` / `VIEWER`。

**补授后逐角色的放行集合（改造前后对照）**：

| 角色 | `comment:*` 改造前 | 改造后 | `custom_object_record:*` 改造前 | 改造后 |
|---|---|---|---|---|
| ADMIN | ✅（门 + 切面直通） | ✅ | ✅ | ✅ |
| SALES | ✅ | ✅ | ✅ | ✅ |
| SUPPORT | ✅ | ✅ | ❌ | ❌ |
| SALES_MANAGER / SALES_REP | ❌ | ❌ | ❌ | ❌ |
| SUPPORT_MANAGER / SUPPORT_AGENT | ❌ | ❌ | ❌ | ❌ |
| MARKETING_* / FINANCE_* / ANALYST / VIEWER | ❌ | ❌ | ❌ | ❌ |

⇒ **逐字相同**。唯一变化：管理员从此能在角色页上勾选这 7 个码。

**「不扩权」的正面证据**：SALES_REP / SUPPORT_AGENT 是 081 新增的一线角色，
**改造前后都进不来**（评分类场景里最可能被误以为"应该能"的两个角色）。
这条要**定向破坏验证**：把 SALES_REP 加进 `comment:*` 的补授名单 ⇒ 对应断言必须转红。

---

## 4 被否决的处置：审批权改按角色码

清单第 1 行要求的「把审批权从『任务分配』改成『角色码』」**被否决**。理由：

`ApprovalEngineService.checkApprover` 的判据是**逐任务**的：

```java
Long current = SecurityUtil.currentUserId();
if (task.getApproverId() == null) { throw new BusinessException(ErrorCode.FORBIDDEN); }
if (!task.getApproverId().equals(current)) { throw new BusinessException(ErrorCode.FORBIDDEN); }
```

调用点：`approve` / `reject` / `transfer`。特征：

- **比任何角色码都严**：角色码回答「这个人**能**审批」，`checkApprover` 回答「**这个任务分给了他**」。
- **对 ADMIN 也不通融**：没有 `isAdmin` 例外（与 `CommentService.delete` 有意不同）。
- 挂 `approval:approve` ⇒ 有码者可审批**任何**任务 ⇒ 从「分给你才能审」退到「有码就能审」= **削弱**。

**前端那处的真实性质**：`ApprovalCenterPage` 三个操作链接的渲染条件只有 `row.status === 'PENDING'`，
**整页零判权**——真正决定放行的规则（归属）没有出现在渲染条件里。**改前端即可，后端一行不改。**

> ⚠️ **2026-09-16 订正（本节的断言，原文逐字保留如下）**：
> 「⇒ 非被分配人看到一个必然 403 的按钮。」
>
> **为什么原文不实（实测推翻）**：`ApprovalEngineService.todos(userId)` / `done(userId)` 的查询条件是
> `eq(ApprovalTask::getApproverId, userId)`（`ApprovalEngineService.java:266-283`），而
> `ApprovalCenterPage` 的两个 tab 打的正是 `/approvals/todos`、`/approvals/done`
> （`frontend/src/services/approvalService.ts:38-46`）⇒ 该列表**只含本人的任务**，
> 非被分配人的行**不会出现在数据里**，也就点不到按钮。**这不是「当下可观察的假按钮」。**
>
> **变的是什么、不变的是什么**：改法不变（仍只改前端、仍不引入权限码），**理由变了**——从
> 「修一个可观察缺陷」改为「**把靠取数隐式成立的判据显式写进渲染条件**」：页面渲染规则与
> `checkApprover` 同源，不再依赖「列表端点恰好按 approverId 过滤」这一隐式性质（列表端点一改、
> 或这一列被复用到别处，就会露出恒 403 的按钮）。
> **一个直接后果**：`quickstart.md` D9 的用例必须**桩住取数**返回一条 `approverId != 我` 的 PENDING
> 任务，否则它打不中——在今天的后端行为下，"只判 status" 与 "判归属" 走的是同一批数据。

> `approval:approve` 是**死码**（字典有、零端点校验）。本项**不**碰它——把它接到端点上是上述削弱，
> 属**明确不做**。

## 5 被否决的处置：给导出下载 / 仪表盘设置目标挂码

两处**都已存在真实的数据范围判定**，挂号是**假收口**：

| 端点 | 已有闸门 | 硬挂码的后果 |
|---|---|---|
| `ExportController#download`（`@GetMapping("/{id}/download")`，零注解） | `ExportJobService.downloadPath`：`if (!job.getCreatedBy().equals(userId) && !"ADMIN".equals(role)) throw EXPORT_FORBIDDEN`。`SystemEnhancementIT` 钉着「SUPPORT 下载他人导出 → 403」 | 挂 `export:create` 会把「**本人**可下载」收窄成「SALES/SUPPORT/ADMIN 可下载」——**真切断** |
| `StatsController#setSalesTarget`（`@PutMapping("/sales-targets")`，零注解） | 方法体内联：`isAdmin` + `request.getUserId() == null` 分支（全局目标仅 ADMIN、个人目标仅本人） | `kpi:view` 归 ADMIN+ANALYST（`V83`）⇒ 会给 ANALYST 露出**必然 403** 的按钮；挂别的码同理 |

⚠️ 后者的「**保持原样**」是 `StatsController` 类 javadoc 的**既有书面决定**，
`check-perms.mjs` 白名单里 `DashboardPage.tsx` 的理由也一直是这么写的。
本项**订正的是 086 清单那两行的工单方向**，**不是**改代码。

## 6 复核中确认的既有事实（本项直接复用，不另立）

- **`@RequirePermission` + `PermissionAspect`**：方法级注解，`"ADMIN".equals(role)` **直通**，
  否则查 `roleService.permissionsOf(role)`，不含则抛 `PERMISSION_DENIED`。
- **两层 403 必须分开断言**：权限码拒绝 = `PERMISSION_DENIED`；数据范围拒绝 = `FORBIDDEN`。
  两者文案相同、只有 `error.code` 不同。
- **`role_permission` 无唯一键**：重复 INSERT 不报错、只留垃圾行 ⇒ 每条 INSERT 前核对组合不存在。
- **最高迁移 V89**（无 V72）⇒ 本项新迁移为 **V90**。
- **纯授权迁移也须登记**：`SchemaParityIT.MIRRORED_MIGRATIONS` 加 `"90"` +
  `schema-h2.sql` 落 `-- ---------- V90__… ----------` 段头。
- **两道后端护栏会**自动**覆盖新码**：`RequirePermissionCatalogTest`（注解用码 ⊆ 字典，
  `orphans` 为空）、`PermissionMatrixIT`（已授予码 ⊆ 字典）。
- **前端登记的码必须被端点真实校验**（`FrontendPermissionCodeAlignmentTest` 断言 2）——
  故本项**只登记真要 gating 的码**，不把 7 个码全登记。

## 7 一处既存文档债（只登记，不改）

`frontend/scripts/check-ui.mjs` 的 R6 注释称「实测 11 处、其中 9 处不该翻译」，
而 `R6_ALLOWED` 的计数和是 **10**——注释与代码差 1。改它要先判那 1 处是什么，**不在本项射程**。
