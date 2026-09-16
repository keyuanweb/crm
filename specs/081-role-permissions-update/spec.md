# 081 角色权限更新 — 规格（**事后回填**）

> ⚠️ **本件是事后回填，写于 2026-09-16，不是立项期产物。**
> 081 的**实际编写顺序无法追溯**（本目录只有 `design.md` 与 `tasks.md`，没有任何当时的规格评审记录），
> 因此**不得**据本件声称 081 走过 spec-first 流程；本件也**不是**对 081 的口径追认，
> 它只是把**今天实测到的事实**补成一份可引用的规格。
> **`design.md` 与 `tasks.md` 的原文一字未动**——它们是 081 的历史凭据，本件是**新增**，不是改写。
> 回填的由来与五项遗留账的处置见 `specs/097-debt-ledger-closeout/`（本件是 097 的 ④ 号交付物）；
> 逐条取证与命令见该目录 `research.md` §4。

**状态**：✅ 已交付（`c9b3378`，2026 年 8 月批次）· **迁移**：`V75__role_permissions_update.sql`（配合既有的 `V46__role_permissions.sql`）

---

## 1 这一项要解决什么（事后归纳，非当时记录）

「角色」在本仓最早只有三个硬编码概念（`ADMIN` / `SALES` / `SUPPORT`，`V46` 建立 `role` / `role_menu` /
`role_permission` 三表时 seed 的）。081 要把角色模型铺开到**按职能分工的一套预置角色**，
并让「菜单可见性」与「操作权限」都从**数据**（而不是散落在 Controller 上的角色字面量）里派生。

### 1.1 兑现的用户故事

**US1（唯一）**：作为**系统管理员**，我要能在一套预置角色上按菜单与操作授予权限，
从而不必改动代码就能调整「谁能看到什么菜单、谁能在哪个模块做哪些操作」。

**验收（写成兑现事实）**：

| # | 验收 | 今天的事实 |
|---|---|---|
| 1 | 预置角色按职能铺开 | **13 个**（见 §2，不是 `design.md` 写的 11 个——口径差见 §4） |
| 2 | 三张表承载角色 / 菜单 / 权限 | `role` / `role_menu` / `role_permission`（DDL 在 `V46`） |
| 3 | 菜单可见性由数据派生 | `role_menu(role_id, menu_key)` ↔ `MENU_TREE` 双射（**护栏**：`MenuAccessGrantAlignmentTest`） |
| 4 | 操作权限由数据派生 | `role_permission(role_id, permission_code)` ↔ `RoleConstants.PERMISSION_DEFS`（**护栏**：`PermissionMatrixIT`、`RequirePermissionCatalogTest`） |
| 5 | 数据范围 ALL/DEPT/SELF | `role.data_scope` 列 + 判定类（**手动调用，非全局拦截**——见 §4 第 6 行） |
| 6 | 内置角色不可删除 | `role.built_in` 列 + 服务层校验 |
| 7 | 角色页可勾选 | `RoleListPage.tsx` 消费 `GET /roles/permission-defs` 与 `GET /roles/menu-tree` |
| 8 | 有测试覆盖 | 单测 `RoleServiceTest`、集成 `RoleIT`、E2E `frontend/e2e/role-permissions.spec.ts`（**均存在**） |

---

## 2 实际交付（逐项可核）

| 项 | 实际 |
|---|---|
| **角色** | **13 个**：`V46` 三个（`ADMIN` 数据范围 ALL / `SALES` SELF / `SUPPORT` DEPT）+ `V75` 十个（`SALES_MANAGER`、`SALES_REP`、`SUPPORT_MANAGER`、`SUPPORT_AGENT`、`MARKETING_MANAGER`、`MARKETING_SPECIALIST`、`FINANCE_MANAGER`、`FINANCE_ACCOUNTANT`、`ANALYST`、`VIEWER`），全部 `built_in = 1` |
| **表** | `role`（`code` 唯一、`data_scope`、`enabled`、`built_in`、逻辑删 `deleted`、乐观锁 `version`）、`role_menu(role_id, menu_key)`、`role_permission(role_id, permission_code)` |
| **权限模型** | **扁平码表**：`实体:动作` 字符串（如 `customer:read`、`quote:approve`）。字典的权威处是 Java 常量 `RoleConstants.PERMISSION_DEFS`（分组 → `children[{code,label}]`），**不落库** ⇒ 库里只有「谁持有哪些码」，没有「有哪些码」 |
| **动作词表** | **开放词表**，实测 20+ 种（`manage` / `create` / `update` / `delete` / `read` / `view` / `export` / `approve` / `assign` / `merge` / `transfer` / `send` / `restore` / `renewal` / `purge` / `payment` / `scheduled` / `reply` / `import` / `pool_manage`…），**不是** `design.md` §2.2 那张固定 10 项的 `VIEW/CREATE/EDIT/…` 表 |
| **菜单模型** | `MENU_TREE`（分组 → `item(key,title)`，**52 个菜单键**）+ `role_menu` 行；菜单的权威名称在 `MENU_TREE`，**不在**库里 |
| **初始化** | **Flyway 迁移**（`V46` seed 三角色 + `V75` 加十角色并铺菜单/权限）+ Java 常量字典。**没有**启动期初始化服务（见 §4 第 5 行） |
| **管理 API** | `RoleController`：`GET /roles`（列表）、`POST` / `PUT /{id}` / `DELETE /{id}`、`GET /roles/options`、`GET /roles/menu-tree`、`GET /roles/permission-defs`；除 `options` 外全部挂 `role:manage` |
| **数据范围** | `DataPermissionService` / `EntityAccessService`，**由业务代码手动调用**（实测 15 个类引用），**没有全局拦截器** |
| **护栏（081 之后才立，不属 081 的交付物）** | `PermissionMatrixIT`、`RequirePermissionCatalogTest`、`MenuAccessGrantAlignmentTest`、`FrontendPermissionCodeAlignmentTest` |

---

## 3 与 `design.md` / `tasks.md` 的偏差（**逐条**）

**读法**：左列是 081 自己的工件里写的，中列是今天实测的事实，右列是证据。
**哪些是「计划改过」、哪些是「实现走偏」，081 的工件里没有记录 ⇒ 本件不替它下结论**（见 §5 末）。

| # | 081 的工件写的 | 实际 | 证据 |
|---|---|---|---|
| 1 | T001：创建 **11 个**预置角色（`design.md` §1.1 的 11 行**含 ADMIN**） | 库存 **13 个** | `design.md` §1.1 的 11 行**不含** `SALES`/`SUPPORT`，而这两个 `V46` 早已有 ⇒ 3 + 10 = 13；`V75` 的 `INSERT INTO role` 10 行 |
| 2 | `V75` **自己的头注释**：「新增 **8** 个预置角色」 | 同一文件 **INSERT 了 10 个** | `V75__role_permissions_update.sql` 头注释 vs 其后的 `INSERT`（10 行）——**这是第三个数字，且它自己就错了** |
| 3 | T002/T003/T004：**40+ 权限模块** × 11 × 40+ 的「角色-权限」与「角色-菜单」关联 | **没有「权限模块」这个东西**（全仓无 `permission_module` 表、无该实体）；`role_permission` 存的是**扁平码字面量**，`role_menu` 存的是**菜单键** | `grep -rln "permission_module" backend/src/main/resources/db/migration/` → 空；`V46` 的 DDL |
| 4 | T005：`RolePermission` 实体**加 `action` 字段** | **无** `action` 列，实体也没有该字段（动作已含在码里：`customer:read`） | `V46` 的 `role_permission` DDL（3 列：id / role_id / permission_code）；`entity/RolePermission.java` |
| 5 | T006：`RoleMenu` **加 `path` 字段** | **无** `path` 列；菜单用 `menu_key` 关联 `MENU_TREE`，路径由前端 `App.tsx` 的路由表给出 | `V46` 的 `role_menu` DDL（3 列）；`entity/RoleMenu.java` |
| 6 | T007：`RolePermissionInitializerService` 启动时初始化、幂等 | **全仓没有这个类**；初始化由 **Flyway 迁移**承担（迁移天然幂等），字典是 Java 常量、**不需要也不应该落库** | `grep -rln "RolePermissionInitializer" backend/src` → 只命中 `tasks.md` 自己 |
| 7 | T008/T012：API 与角色页「支持**按模块和操作类型**分配权限、显示**权限矩阵**」 | 实际是**按权限分组勾码**（`permission-defs` 返回分组→码）+ **按菜单树勾菜单**，**没有**「模块 × 操作」矩阵这一形态 | `RoleController` 的 `/permission-defs`、`/menu-tree`；`RoleListPage.tsx` |
| 8 | T009：数据范围控制，「**查询时自动过滤**」 | 列与判定类都在，但**是手动调用**（15 个类），**非全局拦截** | `CRM_FEATURE_COMPARISON.md` 里「**数据权限（行级）**」那一行（末尾「仍非全局拦截器」+ 2026-09-16 的调用点订正块） |
| 9 | T013/T014：前端菜单权限控制、操作权限控制 | **交付了，但是两批事**：菜单按 `role_menu` 渲染；**操作权限的收口在 1.5 / 086 / 096 才逐模块做完**（`@RequirePermission` + `PermissionAspect`），081 当时只提供了**数据模型** | `usePermission.ts`、`PermissionAspect`、`specs/086-…`、`specs/096-…` |
| 10 | T015：单元测试「**覆盖率 80%+**」 | 覆盖**存在**（`RoleServiceTest`、`RoleIT`），但**没有「80%+ 口径」的留痕**（覆盖率门槛是后来才立的）；**哪些用例属 081 未逐一考证** ⇒ 只记「存在覆盖」 | `backend/src/test/java/com/crm/service/RoleServiceTest.java`、`.../integration/RoleIT.java` |
| 11 | T016：**E2E 测试** | **已交付**：`frontend/e2e/role-permissions.spec.ts`，**文件头自己写着**「端到端测试（081-role-permissions-update）」 | `git log --follow -- frontend/e2e/role-permissions.spec.ts` → `c9b3378`（081 批次），后由 `a12f956`（083）修复其中两条失效断言 |
| 12 | T017：更新角色权限文档 | 见 §5：**本件就是那次「补文档」的事后版本**；当时是否有别的文档产出**未查见** | `specs/README.md` 的 081 行与编号说明 |

⚠️ **一处「偏差」性质的说明**：上表第 11、12 行与第 10 行的前半是**兑现**，不是偏差——列出来是为了
**让「写了但没做」与「写了也做了」在同一张表里可分**，避免读者把整张表读成「081 什么也没做到」。
**081 真正没落地的只有「模块 × 操作矩阵」这一整套建模**（第 3/4/5/6/7 行是它的各个侧面），
其余是**换了实现方式**或**推迟到别项**。

---

## 4 「11 个预置角色」的口径差（**这个数字要小心**）

同一个数在三处写成三个值，来源与对错如下：

| 出处 | 写的 | 是什么 |
|---|---|---|
| `design.md` §1.1 | **11** | 该表列的 11 行 = **ADMIN + `V75` 新增的 10 个**。它**没有**列 `SALES` 与 `SUPPORT`——即它记的是「081 视野内的角色」，**不是库存** |
| `V75` 头注释 | **8** | **错**（同一文件 INSERT 了 10 个） |
| `specs/README.md` 081 行 | **11** | 转抄自 `design.md` §1.1 |
| **库存（实测）** | **13** | `V46` 3 + `V75` 10；`SELECT count(*) FROM role` 的口径 |

⇒ **引用时一律以 13 为准**（它是唯一可从库/迁移直接数出来的数）；要引用「11」时必须写明
「= `design.md` §1.1 的口径（含 ADMIN，不含 `SALES`/`SUPPORT`）」，否则会被读成「本仓只有 11 个角色」。
**本件不改 `design.md` 原文**，故三处旧值仍可 grep 到（订正不静默）。

---

## 5 未验证 / 未回填（如实声明）

- **不判断历史动机**：上表只写**可核的差异**。「计划改过」与「实现走偏」在 081 的工件里**没有任何记录**，
  本件**不替它下结论**；引用本件时不要把某一行的原因写成「因为当时决定改成 X」。
- **081 的 `tasks.md` 里一个勾选框都没有**（`[ ]` / `[x]` 均 0 处）——它是一份**描述式任务清单**，
  不是带勾选状态的执行清单。故 081 **既不是「全部勾选完成」也不是「0/N 未完成」**，
  这两种说法都不适用（这一条是订正 097 立项初稿的措辞：仓里既有「0 勾选但代码在」也有「0 勾选且代码不在」两种，
  081 属**第三种**：**根本没有可勾的框**）。
- **本件不回填 081 的任何历史勾选行**，也不改 `design.md` / `tasks.md`。
- **未做**：把「13 个角色」这个口径同步进 `design.md`（那是改写历史凭据，需另立）；
  `V75` 头注释里的「8」同样**只登记不改**（已应用迁移，Flyway checksum 冻结）。
- **未验证**：081 当年是否有过 E2E 实跑记录、以及 `role-permissions.spec.ts` 在今天是否仍能跑绿
  （E2E 打的是**已在跑的后端**，本项不做手工验证，故**不声称**跑过）。
