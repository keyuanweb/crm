# 验收记录：菜单信息架构与授权可见性收口（084）

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md) | **Quickstart**: [quickstart.md](./quickstart.md)

本文件是 tasks.md 各阶段「结果留痕」的落点。每条记录都写明**怎么测的**（命令或推导方式）与**测到了什么**，
而不是只写结论。规格明确要求「以实验/实测证明，而非以设计论证代替」（FR-N22、SC-N06）的地方，证据一律在此。

---

## T002 判据基准：改造前的角色可见集合

**抓取方式**（在**任何**行为变更之前）：以一次性脚本按现有代码推导，不启服务、不改任何文件：

1. 权威菜单项：解析 `backend/src/main/java/com/crm/common/RoleConstants.java` 的 `MENU_TREE` → **56 项 / 11 组**
2. 角色授权：按文件名序解析 `backend/src/main/resources/db/migration/` 全部 83 个 `.sql`，
   逐条应用 `INSERT INTO role_menu` / `UPDATE role_menu SET menu_key` / `DELETE FROM role_menu`
   → **13 个角色**（`ADMIN`/`SALES`/`SUPPORT` 三个内建 + V75 的 10 个预置）
3. 可见性规则（改造前的现行为）＝`App.tsx` 的 `filterByMenus` + `:494` 的 `isAdmin` 硬门：
   - 非 `ADMIN`：`granted ∩ 可路由菜单键`，**再减去** `adminOrgRoutes`/`adminConfigRoutes`/`adminAuditRoutes`
     三个路由数组里的项（即被整组硬门掉的部分）
   - `ADMIN`：全量（`visibleMenus === undefined` 分支）
   - 「首页」（`stats`）是置顶独立项（`statsMenuItem`），**不过滤**，故所有角色都看得见

### 基准表

| 角色 | 授权项数 | 其中可路由 | **当前可见项数** | 被 `isAdmin` 硬门隐藏 |
|---|---|---|---|---|
| ADMIN | 28 | 28 | **56**（全量兜底） | 0 |
| ANALYST | 7 | 7 | **5** | 2 |
| FINANCE_ACCOUNTANT | 8 | 8 | **8** | 0 |
| FINANCE_MANAGER | 13 | 13 | **9** | 4 |
| MARKETING_MANAGER | 15 | 15 | **12** | 3 |
| MARKETING_SPECIALIST | 11 | 11 | **11** | 0 |
| SALES | 22 | 22 | **22** | 0 |
| SALES_MANAGER | 26 | 26 | **23** | 3 |
| SALES_REP | 21 | 21 | **21** | 0 |
| SUPPORT | 8 | 8 | **8** | 0 |
| SUPPORT_AGENT | 10 | 10 | **10** | 0 |
| SUPPORT_MANAGER | 16 | 16 | **12** | 4 |
| VIEWER | 12 | 12 | **12** | 0 |

### 被 `isAdmin` 硬门隐藏的明细（＝FR-N01/N02 要修复的清单）

| 角色 | 隐藏项 |
|---|---|
| ANALYST | `custom-objects`、`settings/custom-fields` |
| FINANCE_MANAGER | `currencies`、`departments`、`roles`、`users` |
| MARKETING_MANAGER | `departments`、`roles`、`users` |
| SALES_MANAGER | `departments`、`roles`、`users` |
| SUPPORT_MANAGER | `departments`、`roles`、`sla-policies`、`users` |

**合计 16 处「已授权却不可见」，落在 7 个互不相同的菜单键上、5 个角色上。**两者是同一事实的两种计数：
「7」是去重后的菜单键数，「16」是（角色 × 键）对数。本表以 16 为准作为逐角色比对基准，避免用 7 掩盖某些角色缺得更多。

### 与规格描述的口径核对

- 被硬门掉的路由：**19 条**（`adminOrgRoutes` 5 + `adminConfigRoutes` 10 + `adminAuditRoutes` 4），
  去重后 **18 个菜单键**（`/workflows/logs` 借 `workflows`）——与规格「三组共 19 项」一致
- **死授权 = 0 条**：全部 13 个角色的授权键都在 `MENU_TREE` 里存在，故 T015 的 C4 预期为**零红项**
  （`V80` 已清掉 `board`/`usage-map`）。这一点在 T017 的迁移注释里要写明「本例无需 DELETE」

### 供 T014 解析器参考的**真实语句形态**（侦察所得，实施时不必再猜）

`role_menu` 在迁移里共有 **6 种**语句形态（4 类 DML：INSERT / UPDATE / DELETE，其中 INSERT 有三种 WHERE 写法），
**白名单必须覆盖全部 6 种**，否则会静默漏解析：

| # | 形态 | 出处 |
|---|---|---|
| 1 | `INSERT INTO \`role_menu\` (...) SELECT r.id, m.menu_key FROM \`role\` r JOIN (SELECT 'a' AS menu_key UNION SELECT 'b' ...) m WHERE r.code = 'X'` | V46、V75、V80 |
| 2 | 同上但 `WHERE r.code IN ('X','Y')` | V80 |
| 3 | `INSERT INTO \`role_menu\` (...) SELECT r.id, 'k' FROM \`role\` r WHERE r.code = 'X'`（单键直写） | V80 |
| 4 | `UPDATE \`role_menu\` SET \`menu_key\` = '新' WHERE \`menu_key\` = '旧'`（**改键，必须模拟**） | V80 |
| 5 | `DELETE FROM \`role_menu\` WHERE \`menu_key\` IN ('a','b')` | V80 |
| 6 | `INSERT ... WHERE r.code <> 'ADMIN'`（取补集） | V80（`approvals`） |

**注意**：`role_menu` 这个串在 `V46` 里出现 4 次，其中 **1 次是 `CREATE TABLE`**、只有 3 次是 DML。
T014 的「解析到的语句数 == 文件中出现次数」断言必须把 DDL 计入，或改为只统计 DML 出现次数——
否则该防呆断言会在 V46 上误报。

---

## T008 路由与清单的双向对齐（改造 `check-i18n.mjs` 的输入源）

**改造前的输入**：`App.tsx` 里手写的 `MENU_I18N_KEYS`（58 条）；**改造后**：生成物 `menuManifest.ts`
的 `i18nKey` 集合 + `menuKeys.ts` 的 `COARSE_ALIASES`（别名表不在本脚本里复刻——复刻一份必然漂移）。

改造把「路由 → 菜单键」的检查从**单向**变成**双向**。原因是侧边栏改为清单驱动渲染之后，
多出两个不报错、只表现为「界面上少一项」的故障面：

| 方向 | 故障表现 | 修法提示 |
|---|---|---|
| 路由有、清单无 | 该路由在侧边栏**彻底消失**（改造前只是没文案，现在整项不见） | 补 `MENU_TREE` 后 `pnpm menu:gen`；若本就不是菜单项，挂进 `COARSE_ALIASES` |
| 清单有、路由无 | 角色页勾得上、用户永远看不到（084 要根除的那类断链） | 在 `App.tsx` 挂路由；若菜单项已废弃则从 `MENU_TREE` 删除 |

**实测**：两个方向的差集**皆为空**，即 56 个清单键 ↔ 56 个路由键构成**双射**
（58 条声明式路由 = 56 条 + 2 条借分组子页面）。这同时说明导入期那 6 处归属搬移没有留下悬空项。

**反向验证（人为制造不一致，三处逐一变红，随后全部还原、复跑转绿）**：

| 制造的篡改 | 实际输出 | 退出码 |
|---|---|---|
| 在 `customerRoutes` 里加一条 `/ghost-page` | `✗ App.tsx 有 1 条侧边栏路由在菜单清单里没有对应项（该项会从侧边栏消失）：/ghost-page → 菜单键 ghost-page` | 1 |
| 删掉 `/satisfaction` 的路由声明 | `✗ 菜单清单有 1 项在 App.tsx 里没有路由（角色勾得上、用户看不到）：satisfaction` | 1 |
| 把生成物里 `satisfaction` 的 `i18nKey` 改成 `nonexistentKey` | `✗ 菜单清单引用了不存在的 menu.* 键（1）：menu.nonexistentKey` | 1 |

第 3 条同时也被 `pnpm menu:check` 抓到（陈旧性校验），两个护栏对同一处篡改各自独立报错、互不替代。

## T010 行为保持：改造前后逐角色比对

**抓取方式**：一次性 Node 脚本（在仓库外，不入库）**同时实现两种推导**，两侧都从实际文件读取、不硬编码清单：

- 「改造前」＝ `filterByMenus` + `:494` 的 `isAdmin` 硬门 + 手写分组：遍历 10 个路由数组，非 ADMIN 额外**整组排除**
  `adminOrg/adminConfig/adminAudit` 三个数组
- 「改造后」＝ 生成物分组遍历 + `routeByMenuKey` 查表 + 借分组子页面；非 ADMIN 排除
  `admin/config/audit` 三组的 `i18nKey`（T013 之前仍保留的硬门）
- 授权数据仍由 T002 那套迁移解析得到（含 `UPDATE menu_key` 与 `DELETE` 的模拟，共 6 种语句形态）

| 角色 | 改造前 | 改造后 | 集合差异 |
|---|---|---|---|
| ADMIN | 58 | 58 | 无 |
| ANALYST | 5 | 5 | 无 |
| FINANCE_ACCOUNTANT | 8 | 8 | 无 |
| FINANCE_MANAGER | 9 | 9 | 无 |
| MARKETING_MANAGER | 13 | 13 | 无 |
| MARKETING_SPECIALIST | 12 | 12 | 无 |
| SALES | 23 | 23 | 无 |
| SALES_MANAGER | 24 | 24 | 无 |
| SALES_REP | 21 | 21 | 无 |
| SUPPORT | 8 | 8 | 无 |
| SUPPORT_AGENT | 10 | 10 | 无 |
| SUPPORT_MANAGER | 12 | 12 | 无 |
| VIEWER | 12 | 12 | 无 |

**与 T002 基准的口径核对**（两套独立推导互证）：本表的单位是**侧边栏项（path）**，T002 基准表的单位是**菜单键**，
两者差一个「借分组子页面」的常数：`ADMIN` = 56 键 + 2 别名 = 58 ✓；授了 `marketing` 的角色
（实测为 `MARKETING_MANAGER`/`MARKETING_SPECIALIST`/`SALES`/`SALES_MANAGER`，`/marketing/roi` 挂在它后面）
比基准多 1；其余角色逐一对等（`ANALYST` 5、`FINANCE_ACCOUNTANT` 8、`FINANCE_MANAGER` 9、`SUPPORT` 8 …）。
另一条别名 `/workflows/logs` 在本表里不产生任何加成——`workflows` 实测只授给了 `ADMIN`。
**13 个角色的可见集合与改造前完全一致，无增无减。**

**顺序**：13 个角色中有 6 个（含 `ADMIN`）的顺序与改造前不同——把 6 处**有意搬移**的键
（`tags`/`approvals`/`data-vision`/`quotas`/`data-retention`/`field-permissions`）从两侧摘掉后，
**13 个角色的顺序逐项相同**。即顺序差异 100% 由 FR-N13–N18 的归属变化解释，没有任何未归因的乱序。

---

## T013 删除硬门后：新出现的项必须**恰好**是 T002 记下的 16 处

US1 的核心验收（SC-N01「已授但永不出现 = 0」、SC-N02「可见 == 授权」）。做法：同一套脚本里再推一遍
「T013 之后的可见集合」（＝ `授权 ∩ 清单键`，ADMIN 走全量兜底），与 T010 的「改造前」逐角色相减。

| 角色 | 新可见项 | 与 T002 明细 | 授权项数 | 可路由 |
|---|---|---|---|---|
| ADMIN | 0 | ✓（全量兜底，本就不受硬门影响） | 28 | 56 |
| ANALYST | 2：`custom-objects`、`settings/custom-fields` | ✓ | 7 | 7 |
| FINANCE_MANAGER | 4：`currencies`、`departments`、`roles`、`users` | ✓ | 13 | 13 |
| MARKETING_MANAGER | 3：`departments`、`roles`、`users` | ✓ | 15 | 15 |
| SALES_MANAGER | 3：`departments`、`roles`、`users` | ✓ | 26 | 26 |
| SUPPORT_MANAGER | 4：`departments`、`roles`、`sla-policies`、`users` | ✓ | 16 | 16 |
| 其余 7 个角色（`FINANCE_ACCOUNTANT`/`MARKETING_SPECIALIST`/`SALES`/`SALES_REP`/`SUPPORT`/`SUPPORT_AGENT`/`VIEWER`） | 0 | ✓ | 8/11/22/21/8/10/12 | 同左 |

- **合计 16 处**，与 T002「被 `isAdmin` 硬门隐藏的明细」表**逐角色逐键一致**
- **可路由 == 授权项数**对全部 13 个角色成立 ⇒「可见 == 授权」（SC-N02）达成，且这是**无死授权**的直接体现
- 没有任何角色丢失可见项（脚本对每个角色另做了一次「是否丢了项」的检查，均为空）
- `ADMIN` 的 28 是 `role_menu` 里的键数，实际可见 56（全量兜底，FR-N04）——两者不等是设计使然，不是缺口

---

## T014 对照表与迁移解析器（测试支撑）

- **对照表**：`MENU_REQUIREMENTS` 逐项写满 **56 项**，顺序与 `MENU_TREE` 一致；每项是三种形态之一，
  且都带可复核的 `evidence`（Controller + 端点 + 实际注解原文）：
  - `CODE`（读接口挂 `@RequirePermission("码")`）
  - `OPEN`（读接口**无**注解，仅登录 + 行级数据范围）
  - `ROLE`（读接口按**角色名字面量**放行——字典里没有对应的码，记下来才能量出它挡住谁）
- **解析方式**：不查库、不起 Spring 上下文，从 `db/migration` 的 83 个 `.sql` 逐条推演（含 `UPDATE menu_key`
  改键重放、`DELETE` 重放、`WHERE r.code <> 'X'` 取补集）。
- **实测规模**：解析到 **75 条授权语句**，与文件中出现次数 **75** 相等（C5 断言）。
- **白名单设计当场生效**：首跑即失败在 `CREATE TABLE \`role_menu\``——`keywordOf` 只取首词，
  `INSERT INTO` / `CREATE TABLE` 这类两词开头全部落进 `unrecognized` 分支并**带语句原文抛错**。
  这正是要的行为（漏解析是静默故障：某角色凭空多出/少掉授权而无任何症状），补上两词前缀表后重跑通过。

## T015 五条断言：先运行、先变红

命令：`mvn -B -o test -Dtest='MenuAccessGrantAlignmentTest' -DargLine="-Dfile.encoding=UTF-8"`

**首跑结果：`Tests run: 5, Failures: 2`**（C1、C2 各一；C3 覆盖 56 项、C4 死授权、C5 解析器形态 三条为绿）。

### 红项清单（＝待修缺口，逐条记录）

| # | 断言 | 角色 × 菜单键 | 报出的实情 |
|---|---|---|---|
| 1 | C2 | `custom-objects → custom_object:read` | 对照表写了这个码，但**没有任何端点校验它**——`CustomObjectController` 当时整类是 `hasRole('ADMIN')` |
| 2 | C1 | `ANALYST × custom-objects` | `需要 custom_object:read；持有码=[custom_field:*, custom_object:create/update/delete, kpi:view, report:manage, report:view]` —— 写码有、读码无 |
| 3 | C1 | `FINANCE_MANAGER × currencies` | `需要 角色 ∈ [ADMIN, SALES]（CurrencyRateController（GET /currencies、POST /currencies/convert 为 hasAnyRole('ADMIN','SALES')））；角色名不在放行集合里` |

### 与规划预判的差异（重要）

规划时预期会有多条 ROLE 类红项（`playbook`、`quotas`、`mail-sync`、`open-platform`、`workflows`，
以及 `users`/`roles`/`departments`/`sla-policies` 对 5 个预置角色的 16 处）。**实测只有 2 条真实缺口**：
V80~V84 的四个批次已经把这些接线做完了（`users`/`roles`/`departments`/`sla-policies` 的四角色均已持有对应码；
`playbook`/`quotas`/`mail-sync`/`open-platform`/`workflows` 的菜单只有 `ADMIN` 持有，内建管理员在
`PermissionAspect` 里直通，故不成缺口）。以规格要求的「先量后修」为准，缺口清单**以本表的 2 条为准**。

### 反向验证：五条断言都不是空转

| 断言 | 是否可能假绿 | 防假绿的写法 |
|---|---|---|
| C1 | 解析器零命中 ⇒ 没有角色 ⇒ 零缺口通过 | 先断言 `roleCodes() ≥ 13`、`ANALYST` 持有 `stats/reports/custom-objects` |
| C2 | 字节码 pattern 写错 ⇒ 零违规通过 | 先断言「被校验的码 > 30」且含 `customer:merge`/`ticket:read` |
| C3 | 表与 `MENU_TREE` 都空 ⇒ 相等通过 | 断言 `menuKeys().hasSize(56)` |
| C4 | 同上（零授权 ⇒ 零死授权） | 由 C1 的输入断言兜底 |
| C5 | 计数相等是构造性的（不识别即抛异常，不会静默跳过） | 故另加三条**结果级**断言：改键重放后 `ADMIN` 持有 `stats/leaderboard`+`settings/custom-fields` 且无人再持旧键；`board`/`usage-map` 无人持有；`<> 'ADMIN'` 解成「除 ADMIN 外全部 12 个角色」持有 `approvals` |

## T019 / T020 守卫改造与 C1 红项修复

| 红项 | 修法 | 落点 |
|---|---|---|
| C2 + C1 ①`ANALYST × custom-objects` | 补齐读码（FR-N24 已批准，批准人 龙星／2026-09-12） | `RoleConstants` 新增 `custom_object:read`；`V85__menu_ia_and_custom_object_read.sql` 授 `ADMIN`+`ANALYST`；`CustomObjectController` 5 个**定义**端点改挂码（`/{id}/records*` 五端点逐字未动） |
| C1 ②`FINANCE_MANAGER × currencies` | **用户裁决：接线到权限码**（2026-09-12，两选一中的另一项是「收回菜单授权」） | 新增读码 `currency:read`；`V86__currency_read_and_gate.sql` 授 `ADMIN`/`SALES`/`FINANCE_MANAGER`；`CurrencyRateController` 读写分码（读 `currency:read`、写 `currency:manage`） |

**为什么 ② 选接线而不是收回菜单**：`FINANCE_MANAGER` 在 V75 里**已经持有** `currency:manage`，且它是
「多币种」菜单的唯一非 ADMIN 持有者——字典里那个码此前**无人校验**，是典型的「矩阵上写了、实际拿不到」。
接线让它兑现已发布的矩阵，与 084 的 `custom_object:read` 同一形态；SALES 补授 `currency:read` 只为
**保持其改造前已有的读/折算能力不变**（写码不授）。两处扩权都在迁移抬头写明了批准人与范围。

## T021 验收：机械部分

| 检查 | 命令 | 实测 |
|---|---|---|
| 五条断言全绿 | `mvn -B -o test -Dtest='MenuAccessGrantAlignmentTest'` | `Tests run: 5, Failures: 0` |
| 注解 ⊆ 字典 | `mvn -B -o test -Dtest='RequirePermissionCatalogTest'` | `Tests run: 1, Failures: 0` |
| 全部后端单测 | `mvn -B -o test` | `Tests run: 541, Failures: 0, Errors: 0` |
| 格式门禁 | `mvn -B -o spotless:check` | BUILD SUCCESS |
| 行为层（真登录打真接口） | `mvn -B -o test -Dtest='PermissionEnforcementIT#menuIaBatchGatesByCode'` | `Tests run: 1, Failures: 0`（新增，见下） |
| 既有安全类 IT | `-Dtest='PermissionEnforcementIT,CustomObjectIT,MultiCurrencyIT,SystemEnhancementIT,PermissionMatrixIT'` | `Tests run: 22, Failures: 0` |
| diff 范围（FR-N05） | `git diff --stat -- backend/src/main/java/com/crm/controller/` | **只有两个控制器**：`CustomObjectController`、`CurrencyRateController`（后者是 T020 已批准的偏差），其余 Controller 的鉴权注解逐字未动 |

**行为层断言的两次变异（证明它非空转）**：新增的 `PermissionEnforcementIT#menuIaBatchGatesByCode`
把两个控制器的端点在三种状态下实测——

| 变异 | 预期 | 实测 |
|---|---|---|
| 守卫改回 `hasRole('ADMIN')` / `hasAnyRole('ADMIN','SALES')`（改造前） | ANALYST / FINANCE_MANAGER 的 200 变红 | `Status expected:<200> but was:<403>`（`:711`） |
| 撤掉读端点的注解（"撤门但不设码"） | VIEWER / ANALYST 的 403 变红 | `Status expected:<403> but was:<200>`（`:741`） |
| 还原后复跑 | 绿 | `Tests run: 1, Failures: 0` |

**未执行的部分（明确记录，不含糊）**：quickstart 的 **D1/D2/D3 浏览器步骤本轮未做**（本会话无浏览器）。
其判据的覆盖关系是：

| quickstart 步骤 | 已被什么覆盖 |
|---|---|
| D1 第 2 步（已授权的项出现、未授权的不出现） | `MenuAccessGrantAlignmentTest` C1（**对任意角色**断言，不枚举）+ `menuVisibility.test.ts` |
| D1 第 3 步（点开「自定义对象」正常加载，不是 403） | `PermissionEnforcementIT#menuIaBatchGatesByCode`（真 HTTP 200） |
| D1 第 4 步（未授权项直输 URL → 403） | 同上（VIEWER → 403 `PERMISSION_DENIED`） |
| D2（10 预置 + 2 回归角色逐角色） | T013 的 13 角色集合比对 + C1 的通用式 |
| D3（ADMIN 全量兜底） | C1 的 `BUILT_IN_ADMIN` 分支 + `menuVisibility.test.ts` 的 FR-N04 用例 |
| **仍待人工**：侧边栏**实际渲染**层（分组标题、组内一项都没授权时整组不渲染） | 键级已覆盖，像素层未验 |

## T022–T027 验收：配置侧与使用侧的菜单名称、分组一致（US2）

### 先红后绿：T022 首跑实测的不一致清单

`mvn -B -o test -Dtest=MenuRouteAlignmentTest` 在**改写文案之前**首跑为红，列出 **13 处**不一致。
这份清单不是从 tasks.md 的散文抄来的——它是机械读出来的（权威 `MENU_TREE.title` 对比 `zh-CN.ts` 的 `menu.*`）：

| 维度 | 不一致项（侧边栏 → 权威处） |
|---|---|
| 项名 9 处 | `playbook` 销售Playbook → **销售 Playbook**；`renewal` 续约管理 → **合同续约**；`tickets` 客户服务 → **工单管理**；`slaCalendar` SLA日历 → **SLA 日历**；`tasks` 任务 → **任务管理**；`dataVision` 酷炫大屏 → **数据大屏**；`departments` 部门 → **部门管理**；`approvalFlows` 审批流配置 → **审批流**；`dataRetention` 数据保留 → **数据保留策略** |
| 组名 4 处 | `marketing` 营销中心 → **营销管理**；`service` 服务协作 → **客户服务**；`config` 流程与配置 → **流程配置**；`audit` 审计与维护 → **审计维护** |

**两套独立实现互证**：同一批数据另用一次性 Node 脚本（读生成物 + 求值两个语言文件）比对，得到的 9 + 4 项与
Java 断言的 13 条**逐条相同**。两套实现的价值在于「测试没有照着待修清单写」——照抄的清单只能证明测试与清单一致。

**与 tasks.md 的两处偏差（均已在上文各任务里记录，此处汇总）**：

| # | 任务原文 | 实测 | 处理 |
|---|---|---|---|
| ① | 名称不一致「7 处」 | **9 处**（多 `tasks`、`approvalFlows`） | 以实测为准。判据是需求本身（两侧逐字相同），不是那个数字；规划期的人工审计漏了这两项 |
| ② | T023/T024「同上两个文件」（`en` 同步改） | `en` 侧 **0 处**需要改 | 见下 |

**为什么 `en` 侧改动为 0**：权威名是中文，英文界面**无法**与之逐字相等，故 T022 对 en 只断言「键存在」。
逐条核对 9 项 + 4 组的英文名，每一条都已是英文且与权威名同义（`Tickets`=工单管理、`Departments`=部门管理、
`Data Vision`=数据大屏、`Renewals`=合同续约、`Data Retention`=数据保留策略、`Tasks`=任务管理、
`Approval Flows`=审批流、`Sales Playbook`=销售 Playbook、`SLA Calendar`=SLA 日历；组：
`Marketing`/`Service & Support`/`Configuration`/`Audit & Maintenance`），且英文页面文案侧用同一批词
（`pages.ticket.list.title = Tickets`）。改它们只会制造用户可见抖动，无一致性收益。
任务原文的「`en` 同步改以保证 FR-N11」读作**条件式**（改中文名时不要往 en 贴中文），该条件不触发；
FR-N11 的实体是**键对齐**，只改 value 不影响它，且已由 `i18n:check` 与 T022 第③条断言各自把住。

### 反向验证：三处人为篡改各自变红

护栏的价值全在「它真的会红」。三处篡改分别打在三个新维度上，**逐字节复原**后复跑全绿：

| # | 篡改 | 期望变红的断言 | 实测 |
|---|---|---|---|
| ① | `zh-CN.ts`：`tickets: '工单管理'` 改回 `'客户服务'`（项名） | `menuTitlesMatchTheAuthorityVerbally` | `Tests run: 5, Failures: 1`，失败断言即该条 |
| ② | `zh-CN.ts`：`audit: '审计维护'` 改回 `'审计与维护'`（组名） | 同上 | `Tests run: 5, Failures: 1`，失败断言即该条 |
| ③ | `menuManifest.ts`：把 `data-retention` 从「审计维护」挪回「数据分析」（分组归属） | `manifestGroupsMatchTheAuthority` | `Tests run: 5, Failures: 1`，失败断言即该条 |
| — | 复原（`cmp` 与备份逐字节一致）后复跑 | 全绿 | `Tests run: 5, Failures: 0` |

篡改 ③ 顺带证实了「生成器自己错了」这类故障**能被本类抓到而 `pnpm menu:check` 抓不到**：`menu:check` 比对的是
「磁盘上的生成物 vs 现跑一遍生成器的结果」，两个东西出自同一段代码，一致地错时它照样报绿。这是两道护栏分工的实证。

**首跑暴露的两处实现缺陷**（都属于「静默通过」形态，被防呆断言拦下——记录在此以说明那些防呆断言不是摆设）：

| # | 缺陷 | 若没有防呆断言会怎样 | 修法 |
|---|---|---|---|
| Ⓐ | `LOCALE_ENTRY` 缺 `MULTILINE`：`find()` 下 `^` 只匹配整段文本开头，`menu.*` **一条都取不到** | 「零违规通过」——断言全绿而什么都没检查 | 加 `Pattern.MULTILINE`，并在注释里写明缺它的后果 |
| Ⓑ | 生成物解析按 `\n` 整行切分 + `matches()`：CRLF 检出下每行尾部多一个回车，分组头两行**全部落空**，而菜单项行（用 `find()` 无锚点）照常命中 | 抛 `IllegalStateException`「菜单项出现在任何分组之前」——报错指向解析器，而真实原因是换行符 | 按「可选回车 + 换行」切分，注释写明实测到的现象 |

### 门禁实测（T027）

| 检查 | 命令 | 实测 |
|---|---|---|
| 五条断言 | `mvn -B -o test -Dtest='MenuRouteAlignmentTest'` | `Tests run: 5, Failures: 0` |
| 双语键对齐 + 菜单可渲染性 | `pnpm i18n:check` | `✓ 语言资源一致：zh-CN 2890 键 / en 2890 键；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条）` |
| 生成物陈旧性 | `pnpm menu:check` | `✓ 菜单清单是最新的（56 个菜单项）` |
| 类型 | `npx tsc --noEmit` | 0 错误 |
| 前端用例 | `pnpm test` | 20 文件 / 75 用例全绿 |
| 后端格式 | `mvn -B -o spotless:check` | BUILD SUCCESS |
| 受影响的 IT | `-Dtest='PermissionEnforcementIT#menuIaBatchGatesByCode'` | `Tests run: 1, Failures: 0` |

### T025 的实测结论：删键的数目比预期少，以及为什么

「生成物不引用」的键共 6 个，**只有 2 个能删**：

| 键 | 结论 | 依据 |
|---|---|---|
| `menu.board` | **删除**（值「数据大屏」，V80 改键前 `board` 项的遗留） | 全仓无引用；其值已被 `menu.dataVision` 的权威名取代 |
| `menu.planned` | **删除**（值「（规划中）」） | 全仓无引用 |
| `menu.customerMerge` | **保留** | `BreadcrumbNav.tsx` 的面包屑标签 |
| `menu.channelRoi` | **保留** | 同上（`/marketing/roi` 借分组子页面） |
| `menu.emailUnsubscribes` | **保留** | 同上 |
| `menu.workflowLogs` | **保留** | 同上（`/workflows/logs`） |

后 4 个若照 FR-N12 的字面「删掉不被生成物引用的键」处理，后果是面包屑渲染出 `menu.customerMerge` 这样的字面量——
等于用修 FR-N12 的动作制造出一批 FR-N12 的违规。**「不被生成物引用」不等于「无人使用」**，这正是要 grep 全仓而不只看生成物的原因。
另有动态拼键面已核查：`RecycleBinPage.tsx` 的 `t('menu.' + m.label)` 取值为 `customers`/`leads`/`contacts`/`opportunities`，不涉及被删的两个键。

**遗留观察（不在 T025 范围内，不擅自扩权）**：面包屑用的 `menu.customerMerge` / `menu.emailUnsubscribes`
与生成物的 `menu.merge` / `menu.unsubscribe` **值相同、指向同一页面**。今天不产生可见差异，
但同一个页面有两条文案键并存，是「两侧名称不一致」再长出来的土壤。建议在后续 spec 里把面包屑改为消费生成物的
`i18nKey`（一条 `menuKeyToI18nKey` 映射即可），而不必再留一套键。

## T028–T030 验收：归属调整的结果（US3）

### T028 FR-N19：归属调整没有改变任何角色的可见集合

**结构性依据**（一句话）：可见集合 ＝ 授权集合 ∩ 可渲染集合。授权表 `role_menu` 只有 `menu_key` 一列、
**没有分组列**（data-model.md §3），所以「某个菜单项属于哪个分组」根本进不了可见性的计算；而「可渲染集合」
也不因搬动而变——生成物里仍是同一批 56 项，且它是否与 `MENU_TREE` 逐键相等已被 C3 钉住。
两条合起来：搬动归属 ⇒ 可见集合不变。

**实测比对（改造前基准 vs 现状，逐角色）**

| 角色 | 改造前授权项数（§T002） | 现状 | 差 |
|---|---|---|---|
| ADMIN | 28 | 28 | 0 |
| ANALYST | 7 | 7 | 0 |
| FINANCE_ACCOUNTANT | 8 | 8 | 0 |
| FINANCE_MANAGER | 13 | 13 | 0 |
| MARKETING_MANAGER | 15 | 15 | 0 |
| MARKETING_SPECIALIST | 11 | 11 | 0 |
| SALES | 22 | 22 | 0 |
| SALES_MANAGER | 26 | 26 | 0 |
| SALES_REP | 21 | 21 | 0 |
| SUPPORT | 8 | 8 | 0 |
| SUPPORT_AGENT | 10 | 10 | 0 |
| SUPPORT_MANAGER | 16 | 16 | 0 |
| VIEWER | 12 | 12 | 0 |

新增断言 `MenuAccessGrantAlignmentTest#regroupingDidNotChangeAnyRoleGrantSet`（C6）把这张表变成每次构建都跑的检查：
13 个数字写进测试，任一角色的授权项数变化即变红（还守着「搬动归属时顺手重写授权种子」这条路径——那样只会静默少一个菜单）。
另把 §T002 的 16 处「已授权却被 `isAdmin` 硬门隐藏」固化为 containment 断言（ANALYST 2、FINANCE_MANAGER 4、
MARKETING_MANAGER 3、SALES_MANAGER 3、SUPPORT_MANAGER 4 ＝ 16），把两件事分开归因：
**授权侧一直是齐的，当年缺的是渲染**（渲染侧的守卫是 `menuVisibility.test.ts` 的 FR-N01 用例）。

**反向验证（C6 真会红吗）**：把基准里 SUPPORT 的 8 改成 9 →

```
SUPPORT：改造前 9 项，现在 8 项 → [approvals, contacts, customers, exports, knowledge, satisfaction, stats, tickets]
```

指名到角色、两侧计数，并把实际解析到的集合打印出来。复原（`cmp` 与备份逐字节一致）后复跑全绿。

这处反向验证打在基准数字上、**没有**往 `db/migration/` 放一个临时探针迁移：本工作区常有并行会话共用同一目录，
探针文件会进入别人的构建与 Flyway 扫描；而改错基准数字同样能让断言变红，失败信息里又已打印出真实解析结果，
足以证明它读的是真实数据。取舍记录在此，供复核。

### T029 SC-N03 的归属维度 ＝ 0

已由 §T022 的 `manifestGroupsMatchTheAuthority` 机械满足（逐组比对组名/顺序/成员/项名，两侧各 11 组），
本阶段核对现状并留痕：`data-retention` 在 `MENU_TREE` 属「审计维护」、生成物同组，
`pnpm menu:check` 报「✓ 菜单清单是最新的（56 个菜单项）」，归属维度不一致数 = 0，改造前那一例已消除。
反向验证见 §T022 篡改 ③（把生成物里的 `data-retention` 挪回「数据分析」→ 该断言变红，复原后全绿）。

### T030 三个边界情况

| 边界 | 机械形态 | 反向验证（实测变红的文本） |
|---|---|---|
| ① 借分组子页面恰好两条 | `subPageBorrowersStayExactlyTwo`：恰好 `/marketing/roi`→`marketing`、`/workflows/logs`→`workflows`；每条与 `COARSE_ALIASES` 指向同一菜单项；锚点在 `MENU_TREE` 里存在；路径有路由 | 把 `COARSE_ALIASES` 的 `/marketing/roi` 改成 `customers` → `["/marketing/roi 的锚点不一致：SUB_PAGE_AFTER_MENU_KEY='marketing' COARSE_ALIASES='customers'"]` |
| ② 空分组不渲染 | `App.render.test.tsx` 的 ANALYST 用例（见下） | 见下 |
| ③ 组内唯一项与组名同名 | `noItemEchoesItsGroupName`：非置顶的 55 项无一与所在组同名或同文案键；置顶组集合被断言恰好 `{home}` | 把生成物里 `tickets` 的标题改成「客户服务」→ `["项 tickets（客户服务）与它所在的分组同名"]` |

③ 的口径比「组内唯一项」更严：**任何**非置顶项都不得与所在组同名，不只唯一项。理由是这个同名形态本身有害
（分组头与子项并排显示同一个词），而「组内唯一项」只是它最常见的样子；更严的口径还没有误报——
55 项的实测分母写进了断言，置顶组也要求恰好 1 个，避免「生成物退化成空」或「置顶组变多」时断言空转。
非置顶的 55 项 ＝ 56 项 − 置顶组（`home`/`stats`）1 项。

#### 空分组不渲染的实测（T030 ②）

`App.render.test.tsx` 用 ANALYST 的 7 项真实授权（approvals / custom-objects / data-vision / exports /
reports / settings/custom-fields / stats）渲染整个 `App`：

- **出现**：工作台（approvals）、数据分析（reports/exports/data-vision）、流程配置（settings/custom-fields/custom-objects）、
  首页（置顶项，不过滤）
- **不出现**：客户管理、销售管理、交易管理、营销管理、客户服务、系统管理、审计维护
- 机制是 `App.tsx:559` 的 `if (!children.length) return []`（该组没有任何可见项时整组不渲染）
- 正向对照在断言里：先确认「有可见项的分组渲染了」，否则整个侧边栏都没渲染时，那批「不该出现」的断言会全部通过

### 顺带修掉的一处既有缺陷：两个渲染冒烟测试的 mock 从来没生效

这是本次实施中发现的既有缺陷，**不属于 084 的任何一条需求**。之所以一并修掉：它正好落在 T030 ② 与 T021
渲染层要用的那两种测试上，而且它的形态与本 spec 要根除的故障同源——**护栏看着是绿的，实际没在检查它声称的东西**。

| 文件 | 原来的 mock 路径 | 从该文件所在目录解析成 | 实际位置与正确写法 |
|---|---|---|---|
| `src/App.render.test.tsx` | `'../../services/{authService,leadService,customFieldService}'` | `E:\code\crm\services\`（**仓库外，不存在**；已核实无 vite alias） | `src/services/` → `'./services/…'` |
| `src/pages/leads/LeadListPage.render.test.tsx` | `'../services/{leadService,customFieldService}'` | `src/pages/services/`（不存在） | `src/services/` → `'../../services/…'` |

后果：mock 静默失效而测试仍然绿。`App.render.test.tsx` 尤其彻底——`fetchMe` 打真网络、用户永远未登录，
渲染产物里连 `ant-layout-sider` 都查不到，而它唯一的断言是 `not.toThrow()`。

**修法与加固**：路径改对，并各加一条**正向对照**（mock 真被调用过）：
`expect(fetchMe).toHaveBeenCalled()`、`expect(fetchLeads).toHaveBeenCalled()`。
反向验证：把路径改回错的 → 用例变红；改回后复跑绿。这条断言同时是「路径被再次改坏」的绊线。

顺带实测到的一处环境事实（已写进该文件注释，免得后人以为是自己写错了）：`App` 的 `<Suspense>` 边界包着整个
`Routes`，懒加载页面 chunk 在 vitest 里首次 transform 要 **2 秒以上**（实测 2313ms），这段期间整棵已挂载的树
被置为 `display:none`、侧边栏查不到；默认 1 秒的 `findBy*` 等待会在这里**假失败**，故该文件的等待统一放宽到 20 秒。

**T021 的浏览器层验收仍然待人工**：上面把「渲染层」变成了机械检查，但 quickstart 的 D1/D2/D3
（真实浏览器里侧边栏的实际观感、折叠、图标）不在自动化范围内。

## T031–T034 验收：单一真相源与防复发护栏（US4）

### T031 反向验证：三处不一致，三道断言各变红一次

命令与期望见 `quickstart.md` §B。三次都先备份、再单点篡改、跑完立刻按备份复原，**从未使用**
`git checkout -- .` / `git restore .`（本工作区常有多会话并行）。

| # | 维制造的破坏 | 结果 | 失败输出（节选，原样） |
|---|---|---|---|
| ① 名称 | `zh-CN.ts` 的 `menu.tickets`：工单管理 → 工单管理x | `Tests run: 7, Failures: 1` | `Expecting empty but was: ["项 tickets（menu.tickets）：侧边栏='工单管理x' 权威='工单管理'"]` |
| ② 授权 | `V85` 授权对象 `('ADMIN','ANALYST')` → `('ADMIN')` | `Tests run: 6, Failures: 1` | `Expecting empty but was: ["ANALYST 的 custom-objects：需要 custom_object:read（CustomObjectController#page（GET /custom-objects；084 前是 hasRole('ADMIN')，V85 起改挂 custom_object:read））；持有码=[custom_field:create, …, report:view]"]` |
| ③ 生成物陈旧 | `quotas` 销售管理 → 交易管理，不重生成 | `pnpm menu:check` 退出码 1 | `✗ 菜单清单已陈旧：…menuManifest.ts 与「从 MENU_TREE 重新生成的结果」有 7 行不一致。第 60 行 磁盘上：{ menuKey: 'quotas', … }, 重生成：], …` 并附修复命令 |

**还原证据**：三个文件 `cmp <备份> <现盘>` 全部一致；随后 `pnpm menu:check`「✓ 菜单清单是最新的（56 个菜单项）」、
`pnpm i18n:check`「✓ 语言资源一致：zh-CN 2890 键 / en 2890 键；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条）」、
`mvn -B -o test -Dtest='MenuRouteAlignmentTest,MenuAccessGrantAlignmentTest'` → `Tests run: 13, Failures: 0, Errors: 0`。

**踩坑记录（供后人省一次）**：`RoleConstants.java` 是 **CRLF** 行尾，第一次用带 `\n` 的多行字面量去篡改匹配不到
（`mutate.py` 断言命中 0 次并中止，**未落盘**），改用按行号操作的脚本后成功。另外第一次的挪动脚本把 `quotas` 所在行
连它的 `)),` 一起搬走，破坏了 `group(...)` 的括号闭合——`pnpm menu:check` 报的是「文案键冲突」而非「陈旧」。
这类"篡改本身写坏了"的输出不能当证据用，已重做为保持结构的挪动。
**订正**：`quickstart.md` 的 B2 原写「删掉 `V75` 里 `ANALYST` 的一条 `role_permission`（如 `custom_object:update`）」，
实测**删它不会变红**——菜单 `custom-objects` 要求的是 `custom_object:read`，而 `V75` 给 ANALYST 的是
`custom_object:create/update/delete`，读码在 `V85`。已把 B2 改准。

### T032 SC-N06 实验：单一真相源到什么程度

三种情形各做一遍（**结论不同**，这是本次实验最有价值的部分）：

| 情形 | ① 未重生成 | ② 重生成后 | ③ 后端断言 | 结论 |
|---|---|---|---|---|
| **C1 移动**（`quotas`：销售管理 → 交易管理） | `menu:check` 陈旧，7 行差异 | `✓ 最新（56 个菜单项）`；生成物里 `quotas` 在 `交易管理` 组内（第 66 行 / 组头第 63 行） | 7 项全绿 | **只改权威处一处即够** |
| **C2 新增**（`probe-experiment`「实验项」） | `menu:check` 陈旧，20 行差异 | `✓ 最新（57 个菜单项）`，但 `i18n:check` **报红**：`✗ 菜单清单引用了不存在的 menu.* 键（1）：menu.probeExperiment`、`✗ 菜单清单有 1 项在 App.tsx 里没有路由（角色勾得上、用户看不到）：probe-experiment` | **四道计数断言变红**，各自打出新项：对照表 `Expected size: 56 but was: 57 …"probe-experiment"`；文案键 `Expected size: 66 but was: 67 … "probeExperiment"`；名称断言 `["项 probe-experiment（menu.probeExperiment）：侧边栏='null' 权威='实验项'"]`；`examined` `but was: 56` | **不够**：还需补路由 + 两个语言文件 + 测试侧台账 |
| **C3 改名**（销售配额 → 销售配额（自测改名）） | `menu:check` 陈旧，1 行差异 | `menu:check` ✓、`i18n:check` ✓（键没变） | `menuTitlesMatchTheAuthorityVerbally` 报红：`["项 quotas（menu.quotas）：侧边栏='销售配额' 权威='销售配额（自测改名）'"]` | **不够**：还需改文案表 `zh-CN.ts`（en 侧按需） |

**为什么改名是两处而不是一处**：侧边栏渲染的是文案表的值，而 FR-N08–N10 刻意要求该值逐字等于权威名。
于是「改了权威处、忘了改文案」必然产生「角色页显示新名、侧边栏显示旧名」——正是 SC-N03 要归零的形态。
护栏让它当场变红，而不是留在界面上。**这不等于单一真相源没做到**：菜单的归属与排列（结构与顺序）确实只有一处作者；
名字是「一份定义 + 一份被强制跟随的译文」，与新增情形里的路由/文案同属"必须跟随"的载体，不是第二个定义者。

**回填到规格的订正（非静默）**：原文 SC-N06 写「把菜单项**改名或在组间移动**，只改权威定义处一处，两侧即同时正确」——
实测与之不符（改名不成立）。已在 `spec.md` 成功标准区：① 订正 SC-N06 为「移动一处、改名两处、新增四处」；
② 新增「SC-N06 实验」小节承载上表与原因；③ 在 FR-N20 下加「口径补充」，说明侧边栏中文名与权威处是
「被断言强制相等」而非「由代码派生」。原文保留其意图（以实验为证、新增情形不得与"只改一处"混为一谈）。

### T033 两道护栏都在常规构建入口（FR-N23）

| 护栏 | 入口 | 证据 |
|---|---|---|
| 生成物陈旧性 | `pnpm run menu:check` | `.github/workflows/ci.yml` frontend 作业：「Menu manifest is up to date」步骤，注释标注 FR-N23，排在 `pnpm run build` 之前 |
| 路由 ↔ 清单双向对齐、`menu.*` 键存在性 | `pnpm run i18n:check` | 同一作业的「i18n key parity」步骤 |
| 分组/成员/项名三维度一致（FR-N21 的机械形态） | `mvn -B verify` 的 surefire | 该类名为 `MenuRouteAlignmentTest`（`*Test`），随 verify 运行；只解析源码与迁移，**不起 Spring 上下文、不连库、不联网**——本会话全程 `mvn -B -o` 离线跑绿即为证 |
| 已授菜单 ⇒ 页面打得开 | 同上 | `MenuAccessGrantAlignmentTest`（`*Test`） |

### T034 失败信息质量（FR-N21 的「指出具体项」）

实跑到的四类输出（上面 T031/T032 已逐条贴出）：名称 → `项 tickets（menu.tickets）：侧边栏=… 权威=…`；
授权 → `ANALYST 的 custom-objects：需要 custom_object:read（出处…）；持有码=[…]`；
陈旧 → 「第 N 行 / 磁盘上 / 重生成」逐行两侧 + 修复命令；计数下限 → 打出越界值并列出新增的键。
**无一条只给「有 1 处不一致」这种计数式报错。**

**如实记两处"审读而非实跑"**：`C2`（对照表里的码必须真的被某端点校验）与 `C4`（不存在死授权）在正常态下
是**空跑**断言——它们的失败信息只有缺口出现时才可见，而这两类缺口不在 quickstart 的 B1–B3 中，本次未制造。
这两条只做了代码审读：C2 的报错是「菜单键 → 码（出处）」，C4 是「角色 → 死键」并附"谁也渲染不出来、
下次保存该角色会被静默删除"的说明与 V80 的修法先例。**若要求实跑，需要额外两次篡改**（在迁移里删/加一条
指向不存在菜单项的授权），留待需要时补做。

---

## T035–T037 收口：死代码清理、文档登记、全量门禁

### T035 占位机制死代码：确认已无残留

`App.tsx` 已被 T013 的派生重写整体替换，重写时未带上 `'planned' in r && r.planned` 分支。

| 检查 | 命令 | 结果 |
|---|---|---|
| 分支残留 | `grep -rn "planned" frontend/src/App.tsx` | **零命中** |
| 数据侧残留 | `grep -rn "planned: true" frontend/src/` | **零命中** |
| 类型侧残留 | `frontend/src/constants/menuManifest.ts` 的项类型 | 不含 `planned` 字段 |

即 research.md 决策 ⑤ 登记的"占位机制已死、可直接删"在 T013 重写时一并兑现，无需单独再删一次。
**如实说明**：这不是"特意执行了一次删除"，而是重写的副产品；本条只做了**核实**，不存在"删了什么"的 diff。

### T036 文档订正与登记：三处均已落地

① **假设 1/2 的订正**（`spec.md` 假设区）：两条各自保留原文并追加 ⚠️ 段，写明「在规划阶段被证伪」、
登记为 plan 偏差 D1／D2、并**按 FR-N24 批准后撤销原句**。这是本规格内"订正不静默"的做法——
原文不删，读者能看到"当初怎么想、后来怎么被推翻"。

② **FR-N13–N18 的非正式指称**：原先把归属落点写成「某域」，而"域"并非本项目实体（真正的落点是
`RoleConstants.MENU_TREE` 里的分组＝侧边栏一个折叠节）。现已一律改称**分组「X」**，并在 FR-N13 处
附一条术语订正说明解释为何改词。`grep "审计与维护域\|客户域"` **零命中**。

③ **FR-N24 批准记录**（`spec.md:145`）：批准人 **龙星**、批准日期 **2026-09-12**；批准范围＝
数据分析师（`ANALYST`）获得自定义对象**定义**的读、写能力，含新增读码 `custom_object:read` 及其
数据迁移（V85）。同一条款显式写明它**同时覆盖** plan 登记的 D1（新增迁移）与 D2（端点授权语义变更）。

④ **登记面**（`specs/README.md` + `specs/roadmap.md`）：README 模块表新增 084 行、版本行改
「V1~V86 迁移，85 张表」、编号说明补「084 与 083 的差别」（**产出最小契约**＋**含两条数据迁移**）、
迁移对照表新增 V85/V86 两行；roadmap 的「最后更新」「整体覆盖度」「当前进度」三处均更新。

### T037 前端五项门禁：全过

| 门禁 | 命令 | 结果 |
|---|---|---|
| Lint | `pnpm lint` | 0 problem |
| 类型 | `pnpm typecheck` | 0 error |
| i18n | `pnpm i18n:check` | ✓ 2890/2890 键；路由 58 / 清单 56 / 粗粒度别名 3 |
| 清单陈旧性 | `pnpm menu:check` | ✓ 最新（56 项，来源 `RoleConstants.MENU_TREE`） |
| 测试 + 覆盖率 | `pnpm test:coverage` | **20 文件 / 79 用例全通过，退出码 0** |

覆盖率实测：**statements/lines 46.86 / branches 70.03 / functions 21.55**，阈值
33.6 / 47.2 / 21.4 —— **三项阈值均未改动**（T037 要求不得下调，本次亦未上调）。

#### 本次唯一一次门禁转红，及其完整归因链（这段是 T037 的实质内容）

**现象**：084 重写 `App.tsx` 后，全局函数覆盖率落到 **20.19%（148/733）< 21.4**，`pnpm test:coverage` 失败。

**归因**：先怀疑是既有的运行间抖动。**用 083 的记录否证**：083 交付时同一门禁实测 **22.88%**
（`specs/083-engineering-consolidation/data-model.md` §4），余量 1.3–1.9 个百分点，
不可能一次抖动掉 2.7 点。再用 `coverage-final.json` 定位到具体函数——`App.tsx` 21 个函数中
**12 个未被调用**（起始行 314/374/388/519/631/648/649/650/652/653/693/695），全部落在
084 重写的菜单构建与 Shell 交互代码里。**结论：是本次重写造成的，不是抖动。**

**为什么 functions 这一项偏偏被重写打穿**：v8 的 functions 指标＝「被调用过的函数 / 函数总数」，
分母由 `coverage.include` 固定。重写把一个大文件拆成更多小函数，**分母增大而分子不动**，
于是即使 statements 与 branches 大幅上移，这一项仍单独下降。

**处置**：新增 3 条用例，**专打未被调用的函数**（而不是继续堆已覆盖路径的断言）——
`App.render.test.tsx` 新增 `describe('084：Shell 交互路径与窄屏形态')`：① 窄屏（<768px）分组拍平为
一级项 + `resize` 事件触发形态切换；② 顶栏用户菜单的三个跳转项、双语切换、退出登录；
③ 顶栏折叠按钮切换折叠态。补后函数覆盖率回到 **21.55%**，门禁转绿。

**过程中遇到并修掉的四处测试环境问题**（都不改断言内容，只改环境与等待窗口）：

1. **jsdom 缺 `Element.prototype.scrollTo`**：`App.tsx` 在每次路由切换时对内容区调用它做滚动复位，
   jsdom 未实现 → `TypeError` 且成为测试运行**之后**才落地的未处理异常。已按本文件既有 jsdom 补丁的
   同一手法加桩（`src/test/setup.ts`）。此前没被发现，是因为只有会发生路由跳转的用例才踩得到。
2. **G6 在 jsdom 里必然抛错**：顶栏「使用地图」会挂载 `UsageMapPage`，它在挂载后异步 `new Graph(...)`
   起 AntV G6；jsdom 无 canvas，`getContext` 返回 null，g-canvas 随后对 null 调 `clearRect`。
   症状很隐蔽——**79 个用例全绿、退出码却是 1**（vitest 把 2 个 unhandled rejection 计入失败）。
   处置与 `UsageMapPage.test.tsx` 同一手法：打桩 `@antv/g6`。本文件断言的是 Shell 跳转，不是图渲染。
3. **表情前缀让文本匹配失效**：`UsageMapPage` 的标题是 `🗺️ {t('pages.usageMap.pageTitle')}`，
   前缀与文案是两个文本节点，`textContent` ≠ 键名，精确匹配取不到 → 改用正则（`PersonalCenterPage.test.tsx`
   里已有同样处理，属既有惯例）。
4. **一处既有缺陷：`App.render.test.tsx` 的 mock 从来没生效**。三条 `vi.mock` 路径写成
   `'../../services/…'`，从 `src/` 出发指向的是仓库外的 `E:\code\crm\services\`（不存在）——
   mock 静默失效 → `fetchMe` 打真网络 → 用户恒为未登录态 → **整个 Shell 与 `App.tsx` 的菜单构建代码
   从未被执行过**，而测试照样绿。这是 084 顺带修掉的（详见本文 §"顺带修掉的一处既有缺陷"）。
   修好后该文件才真正渲染出登录态 Shell，**这也解释了 statements 34.92 → 46.86、branches 49.05 → 70.03
   的大幅上移**：不是新增用例堆出来的，是被"从未真正执行"的代码终于执行了。

**另外修掉的一处偶发超时**（不属本规格范围，但会让门禁不稳）：`PersonalCenterPage.test.tsx` 的
FR-005 用例（两次新密码不一致）的 `waitFor` 用默认 1 s，在**全量 + 覆盖率插桩**下偶发超时
（单独跑该文件必过）。已与套件内其余异步断言统一为 5 s，**不放宽断言内容**。

### T037 后端门禁：`spotless:check` 通过，`verify` 失败于 4 例范围外失败

**相位实序（本次实测，与 T037 的事前判断一致）**：
`jacoco:prepare-agent` → surefire → **jacoco:report** → **spotless:check** → **failsafe:verify ✗**
→ `jacoco:check` **未到达**。

| 项 | 结果 |
|---|---|
| `mvn -B -o spotless:check` | **BUILD SUCCESS**（724 文件，0 需改动，0 已干净——全部命中缓存） |
| surefire（`*Test`） | **548 run / 0F / 0E** ✅ 全绿 |
| failsafe（`*IT`） | **259 run / 4F / 0E** ❌ |
| `mvn -B verify` | **BUILD FAILURE**，中止于 `failsafe:verify` |

**surefire 全绿包含本规格新增/修好的三类守卫**：`MenuRouteAlignmentTest`、`MenuAccessGrantAlignmentTest`
（两者只解析源码与迁移，不起 Spring 上下文、不连库、不联网——全程 `-o` 离线跑绿即为证），
以及被本规格**修好的** `SchemaParityIT`（2 项）。

#### 4 例失败与本规格的关系：逐行号核对

| 失败用例 | 083 的记录（`spec.md:177`） |
|---|---|
| `IntegrationHubIT.integrationFlow:93` | 同一用例、同一行号 |
| `OpportunityIT.closeWithoutResultReturns422:175` | 同一用例、同一行号 |
| `UserIT.disableUserRevokesAccess:117` | 同一用例、同一行号 |
| `UserIT.userLifecycle:74` | 同一用例、同一行号 |

**四例的用例名与行号与 083 已记录的清单逐项一致**，说明就是同一批既有失败，本次既未新增也未修好。
083 的 `spec.md:177` 明确写这 4 例「**决策仍待作出，本规格范围之外**」；083 的 `tasks.md` 另有
**T068（CRITICAL）** 要求项目负责人二选一（① 作为显式偏差批准并记录批准人与日期／② 推动产品决策并修复）。

**因此 T037 不能判为通过。** 按章程治理节「任何偏差必须明确说明理由**并经批准**」，
"这 4 例是既有的"是理由，但没有批准，**我不具备代替项目负责人批准的地位**——
本次会话自始至终未收到任何用户批准（后台任务完成通知已明确标注为"NOT user input"，
不得当作同意）。**本规格不自行把这 4 例宣告为可接受偏差**，T037 在 `tasks.md` 中保持未勾选，
并把裁决点原样交给项目负责人。

**一处如实说明**：083 记录该批失败时是 `255 run / 4F`，本次为 `259 run / 4F`。
**运行数 +4、失败数不变**，新增的运行来自本规格新增的 `SchemaParityIT`（2 项）等；
失败集合逐项一致，故不影响上面的结论。

### 本规格自身的守卫确实起了作用：`SchemaParityIT` 抓住了本规格的遗漏

084 新增了 V85/V86 两条数据迁移，但**初版漏了把「85」「86」加入 `SchemaParityIT.MIRRORED_MIGRATIONS`
清单**。`mvn -B verify` 立刻以 `SchemaParityIT.everyMigrationIsMirroredInTestSchema` 失败报出该遗漏
（报错文案即指向"哪些迁移未镜像"），补入版本号后转绿。

这正是 FR-G07 设计意图的实现：**"新增迁移未同步镜像时构建失败"这一机制，第一次实际生效就是拦下了
本规格自己的疏漏**。它属于"护栏有反向验证"的一个真实案例——不是人为制造的不一致，而是自然发生的。

### SC-N07（既有 e2e 零失败）：实跑 **37 passed / 0 failed**，但适用范围须如实限定

`cd frontend && pnpm test:e2e` → **37 passed (49.3s)，退出码 0**（chromium；含 083 的
`module-page-auth.spec.ts` 模块页面鉴权 14 项与 `role-permissions.spec.ts` 角色权限各项）。

**适用范围必须说清楚，否则这条证据会被读大**：

| 侧 | 本次 e2e 实际验证的是 | 依据 |
|---|---|---|
| 前端 | **本规格的改动**（vite dev server 直接服务工作区当前源码，084 的菜单可见性代码就是被测对象） | `playwright.config.ts` 的 `webServer` 起官方 dev server |
| 后端 | **不是本规格的改动**——跑的是 **16:54 启动的既有实例**，而 084 的 `RoleConstants.class` 编译于 **20:27** | `Get-CimInstance Win32_Process` 得 java 进程 CreationDate 16:54（`spring-boot:run`，classpath `backend/target/classes`）；`ls -l target/classes/.../RoleConstants.class` 得 20:27 |

即：**SC-N07 在"084 前端 + 084 之前的后端"这个组合下为真**（37/37），
但 **084 的后端侧改动（V85/V86 数据迁移、`CurrencyRateController`/`CustomObjectController` 改按权限码放行）
未被这次 e2e 覆盖到**。运行中的后端进程是先于本规格启动的，且**可能属于并行会话**——
本工作区常有多会话共用（见项目记忆），故**本次不去重启它**。

**因此留一个显式的待办**：要让 SC-N07 覆盖 084 的后端侧，需在**包含 V85/V86 的后端实例**上重跑本套件。
该动作会重启他人的服务进程，须先确认归属，不在本次自动执行。
本规格的后端侧改动由 `MenuAccessGrantAlignmentTest`（surefire，离线、不起上下文）与
`SchemaParityIT`（已镜像进测试库）在构建内覆盖，但这两者是**静态断言**，不能替代活库端到端验证——
这一点如实记为**未完成项**，不混入上面的 37/37。

## T039–T040 收口：两处治理记录的补齐（2026-09-12）

`/speckit-converge` 在本次复核中报出两条 CRITICAL，都不是"代码没写"，而是**记录不合规格要求**。
两项均由项目负责人裁决后补齐，记录如下。

### T039：第二处端点授权语义变更的契约与署名

**缺口**：084 有两处同类的端点判定语义变更，但只有第一处（自定义对象）进了契约与批准记录。
第二处（多币种）**唯一留痕是 `V86` 的 SQL 注释，且那条注释只写「已批准」、无批准人姓名**——
而 FR-N24 的措辞是"必须在本规格内记录批准人与批准日期"，SQL 注释不是规格文档。

**处置**：新增**第二份**最小契约 `contracts/currency-endpoint-authorization.md`（§6 记批准人 **龙星**、
批准日期 **2026-09-12**），并把同一条款补进 `plan.md` 的「FR-N24 批准位」（放行能力由 1 处扩为 2 处）与
`spec.md` 的「批准记录（FR-N24）」；plan 的偏差 D2 行、`spec.md` 假设 2 的订正处各加一处交叉引用。

**为什么不改写契约一**：契约一的 §1 范围声明与 §6 批准记录是**已批准的在案文本**。把它改成"涵盖多币种"
需要修改一段已生效的批准记录，并让「批准了 A」与「批准了 A+B」在同一段落里混为一谈。两处变更分属不同
模块、不同权限码集合、不同受影响角色，各自独立可评审，故按模块各留一份。

**契约内逐条核对的事实依据**（均为本次实测，不是转述既有结论）：

| 断言 | 依据 |
|---|---|
| 变更前 5 个守卫是角色字面量 | `git show b44a2ca~1:backend/.../CurrencyRateController.java`：`:39`、`:70` 为 `hasAnyRole('ADMIN','SALES')`（列表、折算），`:47`、`:54`、`:62` 为 `hasRole('ADMIN')`（增、改、删） |
| `currency:manage` 全仓仅一处授予 | `grep -rn` 迁移目录 → 仅 `V75:228`（`FINANCE_MANAGER` 块内）；`schema-h2.sql:1711` 是同一句的镜像 |
| `currency:read` 已在字典中定义 | `common/RoleConstants.java:333`；`:332` 的注释即本次"读写分码"的判据，故不存在"授予了一个未定义的码" |
| `ADMIN` 的可访问性不依赖 `V86` | `security/PermissionAspect.checkPermission` 对 `ADMIN` 内建恒放行（`"ADMIN".equals(principal.role())` 即 return）；`V86` 仍把 ADMIN 列入授予，是为让角色页勾选状态与字典一致 |

**未改任何代码**：`V86` 与 Controller 的改动在 T020 已完成，本次只补记录与署名。

### T040：`mvn -B verify` 红的显式偏差批准

**缺口**：构建以 failsafe 4 例业务类失败告终，而章程原则四不可协商、治理节要求偏差"经批准"——
此前既未修复、也**未获批准**，主分支处于"已知违反不可协商原则且无批准"的状态。

**裁决**：取 083 `tasks.md` T068 的**选项①**——批准为**显式偏差**。批准记录写入
`specs/083-engineering-consolidation/spec.md` 的新增小节「### 偏差批准记录（T068…，2026-09-12）」，
批准人 **龙星**、批准日期 **2026-09-12**。083 的 T068 随之勾选。

**批准不是免责**——记录里写死了三条边界与三条失效条件（只覆盖那 4 例、出现第 5 例即越界；
不下调覆盖率阈值；不覆盖全绿的前端五道门禁），并明确**不主张 `verify` 已通过、`SC-G01` 判定口径不变**。
本规格的 T037 据此解除"未经批准不得勾选"这一条阻塞，但**并不因此**宣告后端门禁通过——两者的区别见下节。

### T037 收口：全量门禁的最终判定，已无遗留未知项

| 门禁 | 判定 | 依据 |
|---|---|---|
| 前端 `lint` / `typecheck` / `i18n:check` / `menu:check` / `test:coverage` | ✅ 全绿 | 见上「### T037 前端五项门禁：全过」（`test:coverage` 退出码 0，20 文件 / 79 用例） |
| 后端 `spotless:check` | ✅ 通过 | 本次复跑日志：`Spotless.Java is keeping 724 files clean - 0 needs changes to be clean` |
| 后端 `surefire` | ✅ 548 run / 0F / 0E | 见上「### T037 后端门禁」 |
| 后端 `failsafe` | ⚠️ **259 run / 4F / 0E** | 失败集合与 083 记录**逐项逐行号一致**（`IntegrationHubIT.integrationFlow:93`、`OpportunityIT.closeWithoutResultReturns422:175`、`UserIT.disableUserRevokesAccess:117`、`UserIT.userLifecycle:74`） |
| 后端 `jacoco:check`（覆盖率门槛） | ✅ 通过（**在 084 的树上复现**） | `mvn -B verify -Dmaven.test.failure.ignore=true` → 构建越过 `failsafe:verify`，该 check 报 `All coverage checks have been met.`；实测 `INSTRUCTION covered 44 080 / total 56 288 = 0.7831` ≥ 阈值 `0.73`（阈值未下调）。**该判定并非本次首次取得**——首次由 `5bcd4b1`（同日 19:07，早于 084 提交）完成并已记入 `pom.xml` 注释（`0.7818`，同分母）；本次新增的信息只是"084 改动之后它仍然通过" |
| e2e（SC-N07） | ✅ 37 passed / 0 failed | 适用范围限定见上节，不重复 |

**"覆盖率阈值不得下调"这一条此前只能证成一半**（配置确实没改，但门槛是否通过未被判定过——
`jacoco:check` 与 `failsafe:verify` 同处 `verify` 相位且声明在其后，构建在 failsafe 处即中止）。
那一半在 `5bcd4b1`（同日 19:07，早于 084 提交）就已补齐，本次是把该判定**在 084 的树上复现一次**，
确认它没有被 084 的后端改动打破。**不要把本次读成"首次取得判定"**。

**由此得到的收口结论**：`mvn -B verify` 的红**完全**由那 4 例业务类失败造成，与覆盖率门槛无关，
与前端门禁无关，与格式（spotless）无关。那 4 例已作为**显式偏差获批准**（T040，批准人 龙星 / 2026-09-12，
记录于 `specs/083-engineering-consolidation/spec.md`）。**判定已完全确定，无遗留未知项。**

**T037 勾选的含义必须写清，以免被读大**：勾选＝"全量门禁已执行、判定已作出、无未知项"，
**不**表示"后端构建为绿"。后端构建**仍然是红的**——那条红是一条有署名、有边界、有失效条件的
**已批准偏差**，登记在 083 而非本规格；它的对象是产品决策（投递时序、两层校验优先级、登录副作用），
本规格无权也不打算替它决定。

## 面包屑收口：第五处菜单定义作者（2026-09-12 报缺陷 → 2026-09-13 记录）

### 根因：084 的「四处」少算了一处

用户报告「商机阶段的面包屑显示不对」。根因不是漏登记一项，而是 `BreadcrumbNav.tsx` **自带两张手写表**：

- `GROUPED_ROUTES`：**7 个分组**（`customerManagement` / `salesManagement` / `dealManagement` / `marketingAndService` / `basicData` / `dataAnalysis` / `systemManagement`）
- `MENU_KEY_MAP`：**56 条**「路径 → 文案键」

它们是菜单结构的**第五份副本**（前四份＝`RoleConstants.MENU_TREE`、`App.tsx` 路由数组、`role_menu` 授权数据、i18n 文案表），
且**同时决定「有哪些项、属于哪组、叫什么名字」——正是 FR-N21 的三个维度**。

`plan.md` 的 Structure Decision 曾写「残留的、**刻意保留**的前端本地映射**只有三处**，均不属于 FR-N21 的三个维度」——
**该枚举在实施期即不成立**。已在原句后追加【范围订正，2026-09-13】注记，原文保留（订正不静默、原文留痕）。

三道护栏为何都没抓到它：

| 护栏 | 管辖 | 为何放行 |
|---|---|---|
| `pnpm menu:check` | 生成物是否陈旧 | 只比对「重新生成的结果 vs `menuManifest.ts`」，不涉及消费方 |
| `pnpm i18n:check` | 路由 ↔ 清单双射 + `menu.*` 键是否存在 | 输入是 `App.tsx` 的路由表与文案表，面包屑的表不在其内 |
| `MenuRouteAlignmentTest`（后端，7 例） | 清单 / 授权数据 / 权限码 三维一致 | 解析 `App.tsx` 与迁移种子、权限字典，不读 `BreadcrumbNav.tsx` |

即：**三道护栏都只覆盖「可由生成物或路由表推导的那部分」，而这两张表是自立的**，
所以它落后时不产生任何断言——这既是它成为第五份副本却长期未被发现的原因，也是本次要补的**管辖缺口**本身。

### 三个实测症状

| # | 症状 | 依据 |
|---|---|---|
| 1（报告项） | `/opportunity-stages`（商机阶段）在生成物与侧边栏里都在，**两张表里都没有** → 最长前缀匹配落空 → 落到兜底分支渲染成「首页 / 当前页面」 | 反向验证：把它从解析候选中去掉，`breadcrumbTrail.test.ts` 3 条变红，其一为 `expected [ '首页', 'breadcrumb.currentPage' ] to deeply equal [ '首页', '流程配置', '商机阶段' ]` |
| 2 | 组名自成分歧：那张表是 **7 组**词汇，清单是 **11 组**；配置类页面面包屑显示「系统管理」（`systemManagement`），侧边栏显示「流程配置」——而**「流程配置」在那张表的词汇表里根本不存在** | 收口后 `/workflows`、`/sla-policies`、`/custom-objects` 的组断言改判为「流程配置」（`config`） |
| 3 | `MENU_KEY_MAP:136` 的 `'/data-vision': '酷炫大屏'` 是**中文裸字面量**而非文案键 → 英文界面下仍显示中文；且「酷炫大屏」正是 084 已在其余两处统一的 **7 处名称漂移之一** | 收口后 `/data-vision` 断言为「数据分析 / 数据大屏」，键为 `dataVision` |

顺带收掉一处**不可达分支**：原判据为 `isDetail || isNestedDetail`，而 `DETAIL_SEGMENTS`
（`['customers','leads','tickets','orders','quotes','contracts']`）恒为前者的子集——凡 `path` 以 `/customers/` 开头者，
`/customers` 必已被匹配为某项，`isDetail` 已然成立。收成一个条件，并由两条断言钉住两侧
（`/customers/123` 有详情段、`/customers/at-risk` 没有）。

### 收口方式（提交 `226d55c`，10 文件，+630 / −240）

| 文件 | 变更 |
|---|---|
| `frontend/src/components/breadcrumbTrail.ts` | **新增**：纯函数「路径 → 分段」，分组/顺序/名称一律取自 `MENU_MANIFEST`，路径由 `pathOfMenuKey` 推出 |
| `frontend/src/components/breadcrumbTrail.test.ts` | **新增**：11 例（含虚构清单与往返一致性） |
| `frontend/src/components/BreadcrumbNav.tsx` | **重写**：删两张表（−244 行），只留一个 `switch` 渲染器 |
| `frontend/src/components/BreadcrumbNav.test.tsx` | **新增**：6 例渲染断言（文案与链接） |
| `frontend/src/i18n/labelOf.ts` | **新增**：缺键降级的**唯一实现** `menuLabel(t, i18nKey, title)` |
| `frontend/src/constants/menuKeys.ts` | 补 `pathOfMenuKey`（`menuKeyOf` 的逆方向），只登记唯一例外 `at-risk → /customers/at-risk`，与 `COARSE_ALIASES` 同文件同位（同一事实的两个方向） |
| `frontend/src/App.tsx` | 删本地 `labelOf`，三处调用改用共享的 `menuLabel`（两处各写一遍时「降级」会有两种行为） |
| `frontend/src/i18n/zh-CN.ts`、`en.ts` | 删 `pages.breadcrumbGroup.*`（各 7 键）——它们随本次收口失去引用，留着等于把一份与清单不一致的组名词汇表留在语言文件里 |
| `backend/src/test/java/com/crm/security/MenuRouteAlignmentTest.java` | 新增第 8 例 `derivedPathsAreRealRoutes` |

**不复制 `App.tsx` 身上的常量**：置顶分组与子页面锚点都在 `App.tsx` 里，而它 import 本组件（反向 import 成环）。
前者改由清单的**可观测形态**推出（单成员且成员与组同名，由既有 `noItemEchoesItsGroupName` 与新增单测双重钉住）；
后者的锚点直接取 `COARSE_ALIASES`（后端已断言它与 `App.tsx` 的 `SUB_PAGE_AFTER_MENU_KEY` 逐条一致），故锚点仍只有一个作者。

### 护栏与反向验证

| 护栏 | 断言 | 反向验证 |
|---|---|---|
| `breadcrumbTrail.test.ts`（11 例） | 遍历清单 **56 项**：每项都解析出「分组 + 项」，**0 项**落兜底段 | 去掉 `opportunity-stages` 候选 → **3 条红**（症状 1 的形态） |
| 同上·**虚构清单** 1 例 | 用一份虚构清单证明分组/顺序/名称是**派生**出来的，而不是恰好与一份抄来的表一致 | —— |
| `BreadcrumbNav.test.tsx`（6 例） | 渲染出的可见文案与链接：分组名跟侧边栏、置顶项不渲染指向自身的链接、详情段不是链接、未匹配路径落兜底段 | —— |
| `MenuRouteAlignmentTest.derivedPathsAreRealRoutes`（后端第 8 例） | 每个清单项的规范路径必须是**一条真实声明的菜单路由** | 清空 `CANONICAL_PATH_OVERRIDES` → 该例红，列出悬空路径 `at-risk → /at-risk` |

**这条后端断言为何必须放在后端**：前端测试跑在 jsdom 下，既无 `file:` 的 `import.meta.url` 也无 `@types/node`，
读不了文件；而该断言的输入横跨「前端推路径的规则」与「前端路由表」，只有后端测试能读前端源文件
（分工由既有 `MenuRouteAlignmentTest` 确立，本次沿用而未新开入口）。

### 门禁实测（2026-09-13 复跑，均为本表写作时新取得）

| 门禁 | 结果 |
|---|---|
| `pnpm typecheck` | 通过（`tsc --noEmit` 无输出） |
| `pnpm lint` | 0 problems |
| `pnpm test` | **22 文件 / 96 用例**全通过 |
| `pnpm test:coverage` | 退出码 0；statements **47.02** / branches **71.35–71.49** / functions **22.29–22.43** / lines 47.02。阈值未下调（最紧仍为 functions 21.4） |
| `pnpm i18n:check` | ✓ zh-CN **2883** / en **2883**；路由 58 / 清单 56 / 粗粒度别名 3 |
| `pnpm menu:check` | ✓ 清单是最新的（56 项） |
| 后端三例（surefire） | `MenuRouteAlignmentTest` **8/8**、`MenuAccessGrantAlignmentTest` **6/6**、`FrontendPermissionCodeAlignmentTest` **2/2**（16 run / 0F / 0E，BUILD SUCCESS） |

**关于上表的两点限定，必须与数字一同读取**：

1. **branches 与 functions 记的是区间，不是单点**。三次复跑里一次报 `71.49 / 22.29`、两次报 `71.35 / 22.43`
   （statements 与 lines 稳定在 47.02）。**差的成因未查明**——候选有运行间非确定性，
   也有「本工作区常有多会话并行」这一可能：本次复跑前后确有**另一个会话**在改
   `frontend/src/pages/data-retention/**` 与 `frontend/src/pages/exports/**` 并新增两个用例文件
   （其文件 mtime 为 `00:03:13`–`00:03:48`，可查）。**两种成因都与本次收口无关**，且区间两端都过阈值，
   故判定不变；但**不应把该区间当作「已测得运行间浮动率」引用**。
2. 上表的 `22 文件 / 96 用例` 是**收口提交 `226d55c` 的状态**下的实测。此后并行会话新增的用例文件不在其中，
   故**现在重跑会得到不同的文件数与用例数**——那不是本节的回归。
   `vite.config.ts` 的覆盖率注释已按「保留旧快照 + 追加新实测」的惯例刷新（阈值未改）。

### 如实限定：这次收口没有覆盖到什么

1. **「清单新增项必有面包屑」这条护栏有部分是近似恒真的**——删掉清单项也同时删掉一次遍历。
   这正是另外三条证据存在的原因：虚构清单（证明是派生）、组件渲染用例（端到端看文案）、
   后端路径真实性断言（路径不是凭空拼出来的）。**不要把第一条单独当作充分证据。**
2. **不能替代 T021 的手工验收**：本节的证据全是构建内断言与 jsdom 渲染，而 T021 要求的是
   在**含 V85/V86 的后端实例**上按 quickstart D1/D2/D3 人工核对。T021 仍为未勾选（084 的 41 条任务中唯一一条），
   阻塞原因见上文「SC-N07」节——那个 8081 实例可能属于并行会话，本次不重启它。
3. **未新增规格、未新增迁移、未改任何端点**：本次是 084 域内收口后的缺陷修复，落在既有 FR-N20/N21 的管辖下，
   故只在本节与 `plan.md` 的订正注记、`tasks.md` 的 Convergence 相位留痕，不动 `spec.md` 的需求文本
   （FR-N20/N21 的文字本身没有错，错的是 plan 那句对**残留映射数量**的枚举）。

