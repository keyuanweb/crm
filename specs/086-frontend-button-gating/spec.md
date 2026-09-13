# 功能规格：前端按钮级权限收口

**Feature Branch**: `086-frontend-button-gating`

**Created**: 2026-09-13

**Status**: Draft

**Input**: User description: "1.5 权限接线只剩前端半场未交付。后端注解已补齐（115 个码被 `@RequirePermission` 真实校验、172 个注解点），前端却只有 8 个页面接了线——45 个页面把删除/审批/启停按钮无条件渲染给所有人，销售点下去必然 403。只收口高权与破坏性操作，新建/编辑/导入保持现状。"

## 背景：本规格的由来

`CRM_FEATURE_COMPARISON.md` v2.0 量出本项目的「按钮级权限覆盖率 2.3%」——这是一个**诚信缺口**：后端已是真闸门，界面却宣称人人可用。它比"功能缺失"更糟，因为用户**看不出真假**：按钮在那里，点下去只换来一个 403，而正确的行为是它压根不该出现。

`specs/081-role-permissions-update` 与 1.5 批次已经铺好了全部地基：

| 地基 | 现状 |
|---|---|
| 后端权限字典 | `RoleConstants.PERMISSION_DEFS`，137 个唯一码 |
| 后端强制点 | `@RequirePermission` + `PermissionAspect`（ADMIN 直通），**115 个码被真实校验** |
| 前端常量集中 | `frontend/src/constants/permissions.ts`，**17 个码** |
| 前端判定原语 | `hooks/usePermission.ts` 的 `hasPerm`（ADMIN 直通）、`hooks/usePerms.ts`、`components/PermissionGuard.tsx` |
| 已接线页面 | **8 个**（Customer/Product/Campaign/Contract/Quote/Order/Lead/Role） |
| 双向护栏 | `FrontendPermissionCodeAlignmentTest`：登记的码必须 ⊆ 字典 **∧** ⊆ 被真实校验的集合 |

**唯一缺的就是"把剩下的页面接上"**。本规格只做这件事，不加后端注解、不改字典、不动迁移。

### 本规格的独特风险：错一个码是「静默失效」

`permissions.ts` 的头部注释（`:7-10`）已写明：`hasPerm` 走 `permissions.includes(code)`，字典里不存在的码**永远不会被授给任何角色**，于是它对除 ADMIN 外的所有人恒为 `false`——**按钮无声无息地消失，没有报错、没有 403、控制台也没有任何提示**。

所以本规格的重心不是"写多少行 JSX"，而是**逐条对上码**，以及**让错码能被机器抓到**（US4）。这也是为什么规格把"护栏"单列为一个用户故事，而不是收尾的杂项。

---

## 用户场景与测试 *（必填）*

### 用户故事 1 - 无权者看不到破坏性操作（优先级：P1）

客服专员打开客户列表。他持有「客户」菜单，也能读客户，但没有删除权。改造前他看到每行末尾的「删除」、以及工具栏的「批量删除」与行选择框——三者都是**必然 403 的死按钮**。改造后它们**全部不出现**，界面只剩他真能做的事。

**为什么是这个优先级**：这是最大宗的一类（约 30 个页面），也是伤害最直接的一类——误点删除的心理成本最高，而"看得见却做不到"是权限体验里最刺眼的部分。

**独立测试**：以 `role: 'SALES'` + `permissions: []` 的用户渲染页面，断言删除按钮**不在**；再给该用户加上对应码，断言按钮**在**。

**验收场景**：

1. **Given** 一个未持有 `customer:delete` 的非 ADMIN 用户，**When** 打开客户列表，**Then** 行内「删除」与工具栏「批量删除」**均不渲染**，且行选择框也不出现。
2. **Given** 该用户随后被授予 `customer:delete`，**When** 重新打开该页，**Then** 删除按钮出现（**有码必有按钮**——收口不得把有权者的能力也收掉）。
3. **Given** 任意页面上的删除按钮，**When** 与该按钮所调端点的 `@RequirePermission` 码比对，**Then** 两者**逐字相同**（不新增码、不复用近似码）。
4. **Given** 端点无任何权限注解（见 research.md 的「不可收口清单」），**When** 审查该按钮，**Then** 它**不被收口**——因为挂码会把"后端放行"变成"前端藏掉"，那是一次真收窄。

---

### 用户故事 2 - 高权操作与不可逆终态同样不出现（优先级：P1）

销售经理打开用户管理页，他没有用户管理权；客服打开工单详情页，他没有分配权。他们看不到「重置密码」「数据权限」「分配工单」——这些是高权操作。同样地，「赢单/输单」「关闭工单」「彻底删除」「立即执行归档」这些**不可逆**动作，对无权者也不出现。

**为什么是这个优先级**：与 US1 同类（都是"别让他看见"，而非"别让他做到"），但这一类的单点危害更高：重置密码是账号接管入口，彻底删除与立即归档是不可逆的数据操作。

**独立测试**：同 US1 的双向断言，覆盖 `user:manage` / `ticket:assign` / `opportunity:update` / `recycle:purge` / `retention:execute` 各一条。

**验收场景**：

1. **Given** 未持有 `user:manage` 的登录用户，**When** 打开用户管理页，**Then** 启停/重置密码/数据权限三个按钮均不渲染。
2. **Given** 未持有 `ticket:assign` 的登录用户，**When** 打开工单详情页，**Then** 「分配」按钮不渲染；**且**当他同时持有 `ticket:reply` 时，「发送回复」仍然渲染（**同页不同码各自独立**，不得整页一刀切）。
3. **Given** 工单详情页的三个状态流转按钮（开始处理/标记已解决/关闭工单），**When** 收口，**Then** 三者**同判据整组收**——它们打的是同一个端点 `POST /tickets/{id}/transition`、同一个码 `ticket:update`，界面上不该出现第二、第三个判据。
4. **Given** 回收站的「彻底删除」与「恢复」，**When** 收口，**Then** 两者按**各自的码**判断（`recycle:purge` / `recycle:restore`）——虽然同页，但权限语义不同。
5. **Given** 看板拖拽改阶段，**When** 收口，**Then** **不收**（可逆、且是销售最高频操作；见 research.md 决策 7）。

---

### 用户故事 3 - 已接线页面不再有漏网（优先级：P2）

`LeadListPage` 的「删除线索」、`ContractDetailPage` 的「终止合同」与「删除附件」——这三个按钮所在的页面**已经在 086 之前接过线了**，所以它们一直被当作"已完成"而漏掉。同时，`PermissionGuard` 与 `usePerms` 在改造前是**零引用的死代码**：`PermissionGuard` 从未被用，`usePerms` 只有 8 个页面在用。

**为什么是 P2**：数量少（4 处）、影响面小（三个页面的其余按钮都已正确收口），但它们是**审查盲区**的实证——"这批做完了"这个判断本身需要被验证，而不是被假定。

**独立测试**：对 `LeadListPage` 与 `ContractDetailPage` 各自跑双向断言。

**验收场景**：

1. **Given** `LeadListPage`，**When** 以无 `lead:delete` 的用户渲染，**Then** 「删除线索」不渲染；有码时渲染。
2. **Given** `ContractDetailPage`，**When** 以无 `contract:update` 的用户渲染，**Then** 「终止合同」与「删除附件」均不渲染——**且已收口的「审批」按钮的判据不受影响**（审批按 `contract:approve` + 状态，两码独立）。
3. **Given** `PermissionGuard` 组件，**When** 086 完成后，**Then** 它**至少有一个非测试引用**（详情页/整块条件渲染场景），不再是死代码。

---

### 用户故事 4 - 挂错码能被机器抓到（优先级：P1）

一名开发者想给页面加权限判断，顺手写下 `user.role === 'ADMIN' || hasPerm(...)`。这在改造前是**正确**写法，在 `hasPerm` 对 ADMIN 直通的今天却是**错误的**：它会让真正的授权角色看不到按钮（ADMIN 那半边多余，而 `hasPerm` 才是完整判据）。更常见的错法是**把码写错一个字符**或**复制粘贴时键名重复**——三者都表现为"按钮神秘消失"，且**全部现有测试都抓不到**。

**为什么是 P1**：这是本规格**唯一能防住复发**的机制。145 个判定点写完就没有第二次机会去逐一复核；没有这道护栏，本规格交付的是"此刻正确"，而不是"持续正确"。前车之鉴就在本仓库：`i18n` 早已有键不对齐的问题，而**直到引入 `i18n:check` 才被机器看见**。

**独立测试**：故意制造三种缺陷各一次，断言脚本**非零退出**。

**验收场景**：

1. **Given** 某文件新增 `user.role === 'ADMIN'` 的**权限判断**，**When** 运行 `pnpm perms:check`，**Then** 非零退出并指出文件与行号。
2. **Given** 白名单内的合法短路（`hasPerm` 自身、`PermissionGuard`、`menuVisibility`）或**非权限**的角色字面量（角色→Tag 颜色、选文案），**When** 运行，**Then** 通过（**白名单必须能挡住误报**，否则开发者会学会忽略这个脚本）。
3. **Given** `PERMS` 中两个不同的键指向**同一个码**，**When** 运行，**Then** 非零退出。
4. **Given** 代码引用了 `PERMS.xxx` 而 `xxx` 未定义，**When** 运行，**Then** 非零退出。
5. **Given** 门禁本身，**When** 用上述三种缺陷各验证一次，**Then** 三次都确实转红——**未验证过会红的门禁等于没有门禁**（本仓库 083 的核心教训）。

---

### 边界场景

- 端点**无权限注解**时的收口（`approval` 通过/驳回/转交、导出下载、自定义对象记录、评论删除、销售目标设置）——见 research.md「不可收口清单」。
- **纯本地 state** 的操作（表单内删行、筛选项删除、只读 `Switch`）——不挂码。
- 挂**读码**的"导出"类按钮（报价单 PDF = `quote:read`、合同附件下载 = `contract:read`）——不挂码（恒真的空动作）。
- `SegmentListPage` 的删除端点挂的是 `tag:manage`，**不是** `segment:manage`（死码）。
- 后端**无 DELETE 端点**的页面（`SalesOpportunityListPage`）——不是漏接线，不收口。

---

## 需求 *（必填）*

### 功能需求

- **FR-B01**：`frontend/src/constants/permissions.ts` 必须登记本规格用到的全部权限码。每条都必须**存在于 `PERMISSION_DEFS`** 且**被至少一个 `@RequirePermission` 校验**（由 `FrontendPermissionCodeAlignmentTest` 机械校验）。**不得登记死码。**
- **FR-B02**：收口范围**仅限**高权与破坏性操作——删除/批量删除、审批、分配与认领、导出、执行与手动触发、启用停用、角色与权限配置，以及不可逆的业务终态变更（工单解决/关闭、赢单/输单、彻底删除）。**新建/编辑/导入不收口。**
- **FR-B03**：每个被收口的按钮，其判定码必须与该按钮所调端点上的 `@RequirePermission` 码**逐字相同**。**不得新增权限码、不得复用语义相近的码。**
- **FR-B04**：**端点无权限注解的按钮一律不收口。** 这类按钮及其原因必须记入 `research.md` 的「不可收口清单」，作为后端待加码工单。
- **FR-B05**：判定必须用 `usePerms`（列表工具栏 / 行内动作）或 `PermissionGuard`（详情页 / 整块条件渲染）。**不新增第三种写法。**
- **FR-B06**：**保持 `hasPerm` 对 `role === 'ADMIN'` 直通的语义不变。** `CustomerListPage.test.tsx` 依赖它；且它使每个码天然对管理员放行。禁止写 `isAdmin || hasPerm(...)`。
- **FR-B07**：权限判据与业务状态判据**并存**（`&&`），不得相互替代。例：`canApprove = hasPerm(PERMS.contractApprove, user) && status === 'PENDING_APPROVAL'`。
- **FR-B08**：已接线页面上**漏网的范围内按钮**必须一并收口（`LeadListPage` 删除线索、`ContractDetailPage` 终止合同 / 删除附件）。
- **FR-B09**：新增 `frontend/scripts/check-perms.mjs` + `package.json` 的 `perms:check` + CI 步骤，与 `i18n:check` / `menu:check` 同形。它必须实现 spec.md US4 验收场景 1–4 的四项检查，并跳过注释行。
- **FR-B10**：每个被收口的码**至少有一条双向渲染测试**（有码 ⇒ 按钮在；无码 ⇒ 按钮不在）。**负向用例必须使用非 ADMIN 用户**（ADMIN 在 `hasPerm` 里直通，永远拿不到"看不见"的结论）。
- **FR-B11**：本规格**不产生** Flyway 迁移、**不修改**后端 Java 代码、**不改变**任何接口契约。

### 关键实体

| 实体 | 说明 |
|---|---|
| `PERMS` | `frontend/src/constants/permissions.ts` 的权限码登记表。**权威来源是后端 `PERMISSION_DEFS`**，本表是它的前端投影 |
| `hasPerm(code, user)` | 判定原语。`!user → false`；`role === 'ADMIN' → true`；否则 `permissions.includes(code)` |
| `usePerms(codes)` | 列表页批量判定 hook。**必须在顶层无条件调用**，返回新对象、不可作 `useMemo`/`useEffect` 依赖 |
| `PermissionGuard` | 详情页/整块条件渲染的包裹组件。**ADMIN 直通**，支持 `permission` / `permissions` / `role` / `roles` / `fallback` |
| `check-perms.mjs` | 新增护栏脚本。四查：`role === 'ADMIN'` 硬判断（带白名单）、`PERMS` 值不重复、无未定义引用、跳过注释 |

---

## 成功标准 *（必填）*

1. **界面诚实**：以任意非 ADMIN 角色登录，**界面上出现的每个写操作按钮都真能执行成功**（不再有必然 403 的按钮）。
2. **有权者不被误伤**：对每个被收口的码，持有该码的角色**仍能看到按钮**。覆盖率 100%：`EXISTING_PERMISSION_HOLDERS ∩ GATED_BUTTONS` 无一被藏掉。
3. **零新增权限码**：`PERMISSION_DEFS` 与所有 `@RequirePermission` 注解**零改动**（`git diff` 可验证）。
4. **护栏有效**：`pnpm perms:check` 在三种故意注入的缺陷下各转红一次，并已实测记录。
5. **门禁全绿**：`pnpm typecheck && lint && i18n:check && menu:check && perms:check && test:coverage` 与 `mvn -B verify` 均通过。
