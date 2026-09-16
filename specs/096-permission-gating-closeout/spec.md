# 096-permission-gating-closeout：086「不可收口清单」收尾

**Created**: 2026-09-16
**状态**: 实施中（立项阶段先登记，勾选框不预勾）
**形制**: **改造类**（改生产代码），与 086/088/090/091/094/095 同类；**无新端点、无 DTO 变更**
**上游**: `specs/086-frontend-button-gating/research.md` §2「不可收口清单」；`V87` 头注释点名的「全仓仅剩两处角色字面量」

---

## 1 由来

086 把前端 48/101 页的写操作收口到 47.5%，并在 `research.md` 的**「不可收口清单」**里记下 **6 行**
「端点无权限注解 ⇒ 想收口得先改后端建模」，作为**后端待加码工单**。1.5 批 1~3（V80–V87）把其余模块
按码接线后，`V87` 的头注释**点名了剩下的两处**：

> · CommentController 类级 hasAnyRole('ADMIN','SALES','SUPPORT')——字典里没有 comment:* 码族，
>   撤门又不设码等于单向扩权（见其类注释，批 2 的唯一一处刻意保留，属待裁决的锁死）。
> · CustomObjectController 的 /{id}/records 五个记录端点 hasAnyRole('ADMIN','SALES')——084 显式排除在
>   范围外：记录面是业务数据、由数据范围过滤，只有对象定义面按码（见其类注释）。
> 这两处若日后要动，应各自立项（建码族 / 明确授予名单），不要混进权限接线批次里顺手改。

**本项就是那次「各自立项」。** 同时把清单其余 4 行**逐条判到底**——判下来是**判据用错**，不是缺口。

### 1.1 四条实测结论（决定本项的形状）

| 清单行 | 原文要求的处置 | 实测结论 |
|---|---|---|
| 自定义对象记录（3）| 「建码族后单独立项」 | **真缺口 ⇒ 本项做**。字典无 `custom_object_record:*`；5 个记录端点只有角色门，**且服务层无任何数据范围过滤**（见 §1.2） |
| 评论删除（4）| 「建 `comment:*` 码族 + 授予名单」 | **真缺口 ⇒ 本项做**。字典无 `comment:*`；类级门 + 服务层可见性校验 |
| 审批中心三按钮（1）| 「把审批权从任务分配改成角色码」 | **不该改** ⇒ 改为**按任务归属渲染**（前端）；后端一行不改 |
| 导出下载（2）| 「后端先给 `:67` 加码」 | **不该加码**（已有真实数据范围判定）⇒ 订正清单判据 |
| 仪表盘设置目标（5）| 「后端先加码」 | **不该加码**（方法体内联数据范围判定）⇒ 订正清单判据 |
| 跟进编辑（6）| 「不收口」 | 维持不收口（编辑属排除类） |

### 1.2 判据：为什么记录面**必须**设码，而导出下载**不能**

本仓有**两条不同的判据**，射程不同，不得互相顶替：

1. **「撤门之后要不要设码」看数据范围**（`PermissionEnforcementIT` 类头）：
   「读接口什么时候需要设码」不看读不读，看**这条读路径有没有数据范围**——**有**范围过滤的不设码
   （范围已在保护），**没有**范围过滤的必须设码。
2. **「换码时补授给谁」看原闸门**（`V81` 头注释判据①）：
   端点改造前**有闸门** ⇒ 补授范围 = 那个闸门原先放行的角色集合，**一个不多一个不少**（保留事实能力）。

**本项两族都是「换码」（角色门 → 权限码），不是「撤门」，故补授范围由判据 2 决定**；
判据 1 在此只用于**确认换码是必须的**：

- `CustomObjectRecordService` **无任何数据范围过滤**——全类 `SecurityUtil` 只出现在
  `record.setCreatedBy(SecurityUtil.currentUserId())` 一处，没有 `resolveVisibleOwnerIds` /
  `DataPermissionService` / `EntityAccessService` 调用。⇒ 记录面**今天只靠那道角色门**，必须设码。
- `CommentService.checkEntityVisible` 只保证「**看得见**就能评」，且 `TICKET` 一类直通 ⇒
  它不是记录面的范围过滤。⇒ 评论面同样必须设码。

> ⚠️ **判据 2 的例外**（V87 头注释已明文，本项遵守）：**已被任何端点校验过的码，绝不补授**。
> 本项新增的 7 个码**全都从未被任何端点校验过**（字典里此前不存在），故不触发该例外。

---

## 2 用户故事

### US1 自定义对象记录面按码受管（P1）

**角色**：管理员 / 销售；**诉求**：记录面的增删改查能在角色页上独立勾选，而不是写死在角色名里。

**为什么**：今天「自定义对象记录」的访问范围由一条 `hasAnyRole('ADMIN','SALES')` 硬编码决定，
`CustomObjectController` 类 javadoc 与 `RoleConstants` 的 `custom_object` 组注释**都声称**
记录面「靠数据范围过滤」——**实测不成立**（§1.2）。于是这 5 个端点**既没有范围保护、又不可勾选**，
是权限模型里唯一一处「无保护且不可配置」的写面。

**验收**：改造后逐角色放行集合与改造前**逐字相同**（见 SC-096-001），且管理员能勾选。

### US2 评论面按码受管（P1）

**角色**：管理员；**诉求**：评论的读/写/删能在角色页上勾选，SALES_REP / SUPPORT_AGENT 这类
081 角色不再被一条写死的类级门挡在协作场景外。

**为什么**：`CommentController` 类注释把这道门定性为「**一处已知的、待裁决的锁死**」，
并写明「要么给评论建码族并明确授予名单，要么把这些角色从协作场景里排除，二选一」。
**本项选前者**，且授予名单取**原门放行集合**（判据①），故**不扩权**：SALES_REP / SUPPORT_AGENT
**仍然进不来**，但管理员现在可以把它们勾进来。

**验收**：同 US1；另加「撤门不得等于扩权」——见 SC-096-002。

### US3 审批中心操作按钮按任务归属渲染（P1，纯前端）

**角色**：审批列表的查看者；**诉求**：界面上不出现「按后端判据点下去必然 403」的按钮。

**为什么**：`ApprovalCenterPage` 三个操作链接（通过/驳回/转交）的渲染条件只有业务状态
`row.status === 'PENDING'`，**整页零判权**。而后端 `ApprovalEngineService.checkApprover` 逐任务判
`task.getApproverId() == 当前用户`（`approverId` 为 null 则 403），**对 ADMIN 也不通融**。

> ⚠️ **2026-09-16 订正——本节初稿的下一句原文是**：「⇒ 非被分配人在「我的审批」列表里看到的按钮**必然 403**。」
> **该断言不成立**，实测推翻：列表两个端点本身就按 `approverId` 过滤
> （`ApprovalEngineService.todos/done`，`eq(ApprovalTask::getApproverId, userId)`），而本页两个 tab 正是
> 打这两个端点 ⇒ **非被分配人的任务根本不出现在这个页面的数据里**，也就无从点。
> 故本项**不是修一个当下可观察的缺陷**，而是**把靠取数隐式成立的判据显式写进渲染条件**
> （渲染规则与 `checkApprover` 同源，不再依赖「列表恰好过滤过」）。
> 直接后果落在 SC-096-006 的用例上：它**必须桩住取数**构造一条他人任务，见 `quickstart.md` D9。

**⚠️ 清单原文要求的「把审批权改成角色码」被否决**：`checkApprover` 比任何角色码都**严**
（角色码只说明「这个人能审批」，不说明「这个任务分给了他」）。挂码 = 有码者可审**任何**任务，
是**削弱**授权。本项只修前端的假按钮，**后端一行不改**。

### US4 清单两行判据订正（P2，纯文档）

**角色**：下一个读这份清单的人；**诉求**：不要照着一行用错判据的工单去改后端。

## 3 功能需求

### 3.1 后端

- **FR-001** `RoleConstants.PERMISSION_DEFS` 新增 **7 个码**：
  - `comment:read` / `comment:create` / `comment:delete`（3 个，对应 3 个真实端点）
  - `custom_object_record:read` / `:create` / `:update` / `:delete`（4 个，对应 5 个真实端点，
    其中 `records` 列表与 `recordDetail` 详情**共用一个读码**）
  - **不新增** `comment:update` / `custom_object_record:*` 之外的任何码：**只建被端点真引用的码**。
- **FR-002** `CommentController`：**撤掉类级** `@PreAuthorize("hasAnyRole('ADMIN','SALES','SUPPORT')")`；
  三个端点各挂方法级 `@RequirePermission`（`list`→`comment:read`、`create`→`comment:create`、
  `delete`→`comment:delete`）。
- **FR-003** `CustomObjectController`：5 个记录端点由 `@PreAuthorize("hasAnyRole('ADMIN','SALES')")`
  改挂 `@RequirePermission`（`records`/`recordDetail`→`custom_object_record:read`、
  `createRecord`→`:create`、`updateRecord`→`:update`、`deleteRecord`→`:delete`）。
  **对象定义面（6 个端点）一行不动**。
- **FR-004** 新增迁移 **`V90__comment_and_custom_object_record_codes.sql`**（**纯授权 INSERT，无 DDL**），
  按 **V81 判据①** 补授：
  - `comment:*` → `ADMIN` / `SALES` / `SUPPORT`（= 类级门原先放行的集合）
  - `custom_object_record:*` → `ADMIN` / `SALES`
  - 每条 INSERT 前核对 (角色, 码) 组合不存在（`role_permission` **无唯一键**，重复只留垃圾行）。
- **FR-005** 同步 `backend/src/test/resources/schema-h2.sql` 落 `-- ---------- V90__…` 段头，
  并把 `"90"` 加进 `SchemaParityIT.MIRRORED_MIGRATIONS`。
- **FR-006** 订正三处**不实的**「记录面靠数据范围过滤」表述（原文留痕 + 带日期 ⚠️）：
  ① `CustomObjectController` 类 javadoc；② `RoleConstants` 的 `custom_object` 组注释；
  ③ **登记**在 `V87` 头注释（**已应用的迁移不可改**——改了会破坏 Flyway checksum，故只登记不改）。
  同时改写两处「刻意保留/不动」的**书面裁决**，说明本项已按它的要求建码族并定名单。

### 3.2 前端

- **FR-007** `frontend/src/constants/permissions.ts` 登记本项**真要 gating** 的码
  （`commentCreate`/`commentDelete`/`customObjectRecordCreate`/`customObjectRecordDelete` 等，
  以接线点实际需要为准）；**每个登记的码必须同时被端点校验**（否则
  `FrontendPermissionCodeAlignmentTest` 断言 2 判红）。
- **FR-008** `src/components/CommentSection.tsx`：删除链接的判据由 `role === 'ADMIN' || 作者本人`
  改为 **`hasPerm(comment:delete) && (ADMIN || 作者本人)`**——镜像后端
  `CommentService.delete` 的「码闸门 ∧（作者 ∨ ADMIN）」。发布输入区按 `comment:create` 渲染。
- **FR-009** `src/pages/custom-object/CustomObjectRecordPage.tsx`：新增/编辑/删除按对应码渲染
  （该页**整页零判权**）。
- **FR-010** `src/pages/approval/ApprovalCenterPage.tsx`：三个操作链接的渲染条件由
  `row.status === 'PENDING'` 改为 **`row.status === 'PENDING' && row.approverId === user.id`**。
- **FR-011** 更新 `scripts/check-perms.mjs` 白名单中 `CommentSection.tsx` 与 `DashboardPage.tsx`
  的**理由**（两处 count 不变）——`CommentSection` 由「不可收口」改为「数据归属规则的 ADMIN 例外」
  （与 `CustomerDetailPage` 同形）；`DashboardPage` 改为「数据范围判定，非角色名拦人」。
- **FR-012** 若新增文案，`i18n/{zh-CN,en}.ts` **双语同一次提交**（`i18n:check` 双向校验）。

### 3.3 文档

- **FR-013** 订正 `specs/086-frontend-button-gating/research.md` §2 清单的**第 2、5 行**
  （导出下载、仪表盘设置目标）：判据订正为「**不该设码**」并写明理由。
  **原文逐列逐字保留 + 追加带日期 ⚠️ 块，不改写、不删除**（仓规：订正不静默）。
- **FR-014** 本目录 `research.md` 给出**清单 6 行的逐行复核表**（对照 086 的原判据与本项实测）。
- **FR-015** `specs/README.md` 模块表加 096 行 + 迁移表加 V90 行（并订正该表标题的迁移计数）；
  `specs/roadmap.md` 的 `## 当前进度` 加 096 行（**立项时勾选框留空、不预勾**）。

### 3.4 测试

- **FR-016** `PermissionEnforcementIT` 追加两族的分角色断言：
  - **有码角色** ⇒ 非 403；**无码角色** ⇒ `PERMISSION_DENIED`
  - **两层 403 分开断言**：权限码拒绝 = `PERMISSION_DENIED`，数据范围拒绝 = `FORBIDDEN`
    （两者文案相同、只有 `error.code` 不同——只断言 HTTP 403 会让「码没生效、恰好被范围兜住」
    与「码正常工作」看起来一样）。
  - **负向用例必须用非 ADMIN 令牌**（ADMIN 在切面直通，用管理员令牌整批接线错误也能全绿）。
- **FR-017** `PermissionEnforcementIT` / 既有的授权矩阵测试须证明**零行为变化**：
  ANALYST / VIEWER / FINANCE_* / MARKETING_* / SALES_REP / SUPPORT_AGENT 等
  **既不在补授名单里的角色，改造后仍被拒**（这是「不扩权」的可核证据）。
- **FR-018** 前端新增/扩展用例：`CustomObjectRecordPage.perm.test.tsx`（**新建**）与
  `CustomerDetailPage.perm.test.tsx`（扩评论码）、`ApprovalCenterPage` 的**按归属渲染**用例
  （**不是 perm 用例**——它不含权限码）。
- **FR-019** **定向破坏留痕**：每条声称是护栏的断言都被**观测到在破坏下转红**，逐条**逐字节还原**，
  破坏期间不提交。留痕写入 `falsification-evidence.md`。

## 4 非目标（明确不做，且各有理由）

1. **审批权改按角色码**（清单第 1 行的字面要求）——会**削弱**现有判据（§2 US3）。
2. **给导出下载、仪表盘设置目标挂码**——两处已有真实的数据范围判定，挂号是**假收口**：
   导出下载会从「本人可下载」收窄成「有码者可下载」；仪表盘会把必然 403 的按钮露给 ANALYST。
3. **`custom_object_record:*` 授给 ANALYST**（尽管它持有 `custom_object:*` 定义面码）——
   今天记录面的门不让他进，判据① 要求保留事实能力。要开通**须由管理员在角色页勾**。
4. **新建 `comment:update`** 及任何**没有被端点引用**的码。`follow_up:delete` / `quote:delete` /
   `ticket:approve` 那类死码的**处置也不在本项**（不在本项射程，只登记）。
5. **改 `V87` 头注释**（已应用的迁移，checksum 冻结）——只登记。
6. `check-ui.mjs` R6 注释与 `R6_ALLOWED` 计数差 1 的订正；`frontend/src/App.tsx.bak` 的清理；
   `specs/081` 缺 `spec.md`；`PROJECT_FEATURES` §九「V72 空缺」——均**只登记**，不在本项。
7. **不动任何已交付规格的历史勾选行**（回填 `[x]` 等于把历史改写成真话）。

## 5 成功判据

- **SC-096-001（零行为变化）**：对**每一个**预置角色，两族端点的放行/拒绝结果
  改造前后**逐字相同**。唯一变化是「管理员能在角色页勾选」。
- **SC-096-002（不扩权）**：改造前被类级门挡住的角色（SALES_REP / SUPPORT_AGENT / ANALYST /
  VIEWER / FINANCE_* / MARKETING_*）**改造后仍被拒**，且拒绝码为 `PERMISSION_DENIED`。
- **SC-096-003（码真的生效）**：无码角色的 403 带 `PERMISSION_DENIED`，
  **不是**被服务层数据范围兜出的 `FORBIDDEN`（两层分开断言）。
- **SC-096-004（前后端不漂移）**：`FrontendPermissionCodeAlignmentTest` 两条断言绿
  （登记的码 ⊆ 字典 ∧ ⊆ 被真实校验的集合），`check-perms.mjs` 四项绿。
- **SC-096-005（迁移可核）**：`SchemaParityIT` 绿（含 `MIRRORED_MIGRATIONS` 与 `schema-h2.sql` 段头）。
- **SC-096-006（判据显式化）**：取数返回一条 `approverId != 当前用户` 的 `PENDING` 任务时，
  `ApprovalCenterPage` **不渲染**三个操作链接（用例须**桩住取数**，见 US3 的 2026-09-16 订正）；
  后端行为**一行未改**。
- **SC-096-007（文档不静默）**：086 `research.md` 被订正的两行**原文仍可 grep 到**。
- **SC-096-008（门禁）**：`mvn -B verify` 退出码 0（覆盖率 ≥ 0.73，阈值未下调）；
  前端七道门禁退出码 0，`ui:check` 冻结台账**不增长**，覆盖率阈值未动。

## 6 未验证边界（如实声明）

- **手工冒烟需 8081 上的后端**。按仓规**不得擅自重启可能归并行会话所有的共享后端**；
  未获用户放行时，本项**不声称**做过手工冒烟，只报门禁读数。
- 本项**不新增任何 e2e**，不做浏览器 UI 验证。
- 前端用例运行在 jsdom（**无布局引擎**），且 `src/test/setup.ts` 把 `matchMedia` **恒桩成
  `matches: false`**——本项预期不触及断点分支；若不触及，在 `falsification-evidence.md` 里
  **写明「未涉及」**而不是沉默。
