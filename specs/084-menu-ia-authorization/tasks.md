---
description: "Task list for 084 菜单信息架构与授权可见性收口"
---

# Tasks: 菜单信息架构与授权可见性收口

**Input**: Design documents from `/specs/084-menu-ia-authorization/`

**Prerequisites**: [plan.md](./plan.md)、[spec.md](./spec.md)、[research.md](./research.md)、[data-model.md](./data-model.md)、[contracts/authorization-semantics.md](./contracts/authorization-semantics.md)、[quickstart.md](./quickstart.md)

**Tests**: 本规格**要求**测试先行（原则四为不可协商，且 FR-N21/N22/N23 本身就是「护栏」的需求）。凡标注「先红后绿」的任务，必须先证明它失败，再实施修复。

**Organization**: 按用户故事分组，使每个故事可独立实施与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无依赖）
- **[Story]**: 所属用户故事（US1–US4）
- 描述中一律给出确切文件路径

## 路径约定

- 后端：`backend/src/main/java/com/crm/`、`backend/src/test/java/com/crm/`、`backend/src/main/resources/db/migration/`
- 前端：`frontend/src/`、`frontend/scripts/`
- 护栏需要 `frontend/` 与 `backend/` 同处一个检出（`MenuRouteAlignmentTest` 的既有前提）

---

## Phase 1: Setup

**Purpose**: 批准前置与判据基准。本阶段不写任何业务代码。

- [x] T001 **CRITICAL** 取得并记录 FR-N24 的批准：把批准人与批准日期填入 `specs/084-menu-ia-authorization/contracts/authorization-semantics.md` §6、`plan.md` 的「FR-N24 批准位」与 `spec.md` 的「批准记录（FR-N24）」。**未记录前，T016–T019 不得实施，本分支不得交付**（治理节：任何偏差必须明确说明理由并经批准）→ 批准人 **龙星**、批准日期 **2026-09-12**，三处均已落笔
- [x] T002 捕获判据基准：按现有代码（`App.tsx` 的 `filterByMenus` + `isAdmin` 硬门）推出并记录「每个预置角色的当前可见集合」——角色 → 授权项数 → 当前可见项数 → 差集，记入 `specs/084-menu-ia-authorization/` 的验收记录。这是 FR-N03「不得额外放大**也不得缩小**」的比对基准，必须在任何行为变更之前抓取 → 已记入 `verification.md`：13 个角色的授权/可见项数逐行；**被硬门隐藏共 16 处（7 个去重键 × 5 个角色）**，落在 `ANALYST`/`FINANCE_MANAGER`/`MARKETING_MANAGER`/`SALES_MANAGER`/`SUPPORT_MANAGER`；**死授权 0 条**（故 C4 预期零红项）

**Checkpoint**: T001 拿到签字前，本规格的实质改动一律不动手。

---

## Phase 2: Foundational（阻塞后续所有用户故事）

**Purpose**: ① 把权威处定稿（生成器必须从一个已定稿的权威处生成）；② 建立派生机制，并完成一次**行为保持**的结构性重构。

> **CRITICAL**: 本阶段未完成前，任何用户故事都不开始。
>
> 关于 FR-N13–N18（归属调整）为何落在本阶段：生成物是从 `MENU_TREE` 冻结出来的，权威处的归属若在生成之后再改，就会先在界面上呈现一次「数据保留回到数据分析」的回退、再在 US3 里搬回去（两次可见抖动）。因此权威归属必须**先定稿**。US3 的阶段（Phase 5）负责该结果的验证与收口。

- [x] T003 权威处定稿（**已完成**，落地结果：客户管理 6 项 += `tags`；销售管理 7 项 += `quotas`；工作台 5 项 += `approvals`；数据分析 5 项 += `data-vision`；流程配置 10 项 += `field-permissions`；审计维护 3 项 += `data-retention`。三项校验：**56 项 / 11 组不变**、无 title 改动、`spotless:check` 通过；可见集合逐角色与 T002 基准**完全一致**，即 FR-N19 已被实测确认）
- [x] T004 [P] 新增 `frontend/scripts/gen-menu.mjs`（**已完成**；实测校验：生成的 56 个 `i18nKey` 与改造前 `MENU_I18N_KEYS` 的值**逐一相等**，9 处不符里 7 处进例外表、2 处是别名子页面留在前端。**与任务原文的一处偏差**：生成物**不含 `path` 字段**——路由拼写已是 `menuKeys.ts` 的职责，写进生成物就是第二份路由真相，见 plan 的 P3。缺映射/键冲突/解析过少三种情形均实测报错退出）：解析 `RoleConstants.java` 的 `MENU_TREE`，输出 `frontend/src/constants/menuManifest.ts`。规则见 research.md 决策 ④——菜单项文案键默认取 `camelCase(menuKey 最后一段)`，7 个项目走显式例外表（`stats→home`、`customer-merge→merge`、`contract-renewal→renewal`、`marketing→marketingActivity`、`marketing/email→emailMarketing`、`email-unsubscribes→unsubscribe`、`exports/scheduled→scheduledExports`）；11 个分组标题→组文案键显式映射，**缺映射即报错退出**（不得静默兜底）。生成物每项含 `menuKey / path / i18nKey / title`，分组含 `title / i18nKey` 与有序成员。文件头写明「本文件由 scripts/gen-menu.mjs 生成，请勿手改」与重生成命令
- [x] T005 [P] 新增 `frontend/scripts/check-menu.mjs`（**已完成**，`package.json` 已加 `menu:gen`/`menu:check`）：重新生成并与磁盘上的 `menuManifest.ts` 逐字节比对，不一致即非零退出并打印差异行；同时在 `frontend/package.json` 增加 `menu:gen` 与 `menu:check` 两个脚本项
- [x] T006 运行生成器产出 `frontend/src/constants/menuManifest.ts` 并提交（**已完成**：56 项 / 11 组，prettier `--check` 通过，`tsc --noEmit` 通过）（首次生成；若生成结果与预期不符，先修 T004 的解析或例外表，不要在生成物上手工修补）
- [x] T007 重构 `frontend/src/App.tsx` 使分组/顺序/名称派生自生成物：删除 58 条 `MENU_I18N_KEYS` 手写表与 `groupedMenuItems` 里手写的分组字面量，改为遍历 `menuManifest`；**本次保留** `isAdmin` 硬门（行为变更是 US1 的事）与既有可见性行为；保留分组→图标映射（antd 图标是 JSX，不进生成物）与两个「借分组显示」的子页面路由（`/marketing/roi`、`/workflows/logs`）（**已完成**。生成物为唯一的分组/顺序/名称来源，前端只剩三样东西：路由表、`GROUP_ICONS`、两个别名子页面——都写了「为何不是第二个作者」的注释。**实施中发现并处理的一个坑**：`/marketing/roi` 与 `/marketing` 同归 `marketing` 键，查表必须让「精确同名路径优先」（`r.path === '/' + key`），否则别名会把正式项的 path 与图标挤掉。`ADMIN_ONLY_GROUP_I18N_KEYS` 是**待删除的开关**（T013 删），故显式标注而非隐式散落。门禁：`tsc --noEmit` / `eslint src/App.tsx` / `pnpm test`（19 文件 67 用例）/ 新增代码块与 prettier 输出逐字相同；行为保持证据见 T010）
- [x] T008 改造 `frontend/scripts/check-i18n.mjs`：其「路由覆盖」检查原以 `MENU_I18N_KEYS` 为输入，改为消费 `menuManifest.ts`（键集合的来源随之收敛到生成物）；双语键对齐检查的既有行为不变（**已完成**。输入换成生成物，`COARSE_ALIASES` 从 `menuKeys.ts` 读而不在此复刻。**检查由单向扩为双向**：清单驱动渲染后，「路由有、清单无」会让整项从侧边栏消失，「清单有、路由无」正是 084 要根除的断链，两个方向各给一句修法提示。实测两向差集皆空——56 个清单键 ↔ 56 个路由键构成**双射**。三处人为篡改（加幽灵路由 / 删路由声明 / 改 `i18nKey`）逐一变红、退出码 1，还原后复跑转绿；证据见 `verification.md` §T008）
- [x] T009 在 `.github/workflows/ci.yml` 的前端作业中，于既有 `i18n key parity` 步骤旁增加 `pnpm menu:check` 步骤（FR-N23 的前端一半）（**已完成**。位置在 `i18n key parity` 之后、`test:coverage` 之前，前后各一段注释写明分工：`i18n:check` 管「路由与清单双向对齐」、`menu:check` 管「生成物与权威处一致」，两道护栏互不替代。用仓库内的 `js-yaml` 实测解析通过，frontend 作业 12 个步骤顺序无误）
- [x] T010 验证行为保持：逐角色比对侧边栏可见集合与 T002 的基准，**唯一允许的差异是 FR-N13–N18 的归属变化（分组位置变、所属项集合不变）**，不得出现可见项增减。差异记入验收记录（**已完成**，逐角色比对表见 `verification.md` §T010）：**13 个角色的可见集合与改造前完全一致、无增无减**（`ADMIN` 58、`ANALYST` 5、`SALES_MANAGER` 24 …）。6 个角色存在顺序变化，**100% 由 6 处有意搬移解释**——摘掉 `tags`/`approvals`/`data-vision`/`quotas`/`data-retention`/`field-permissions` 后，13 个角色的顺序逐项相同。口径核对：本表按「侧边栏项」计、T002 按「菜单键」计，差额恰为借分组子页面数（`ADMIN` 56+2、授了 `marketing` 的 4 个角色 +1），两套独立推导互证）

**Checkpoint**: 权威处定稿、生成物与陈旧性校验就位、渲染改为派生——此时可见集合仍与改造前一致（除归属位置）。

---

## Phase 3: User Story 1 - 已授予的菜单必须真的出现在侧边栏（Priority: P0）🎯 MVP

**Goal**: 侧边栏可见集合**只**由菜单授权决定（FR-N01–N07）；每个「已授」项都「打得开」。

**Independent Test**: 以 `ANALYST` 登录——「自定义对象」出现在数据分析组下，点开正常加载（非 403）；对未被授予的项直接输 URL 仍 403。全部 10 个预置角色的可见集合 == 授权集合。

- [x] T011 [US1] 抽取可见性判定的纯函数到 `frontend/src/constants/menuVisibility.ts`（输入：角色、`user.menus`、生成物；输出：可见菜单键集合）。**不得**出现按角色名整组开关的分支（FR-N01）（**已完成**：导出 `allMenuKeys()` 与 `resolveVisibleMenuKeys(role, grantedMenus, manifest = 生成物)`。**唯一的角色名分支是 ADMIN 全量兜底**（FR-N04，且注释写明「否则授权表残缺时管理员会把自己锁在系统外面」）；另有一条边界：授权里出现、清单里没有的键**不**变可见（FR-N02 的 `授权 ⊆ 可渲染`）。清单可注入，正是为了单测能构造小清单）
- [x] T012 [P] [US1] 新增 `frontend/src/constants/menuVisibility.test.ts`（vitest，纯函数、不读文件——读文件的事归后端护栏）：断言 FR-N01（任意非 ADMIN 角色的可见集合 == 其授权集合，且无角色名硬门）、FR-N02（授权集合 ⊆ 可渲染集合）、FR-N03（⊇ T002 基准的已授权项，不放大也不缩小）、FR-N04（ADMIN 仍为全量）（**已完成**：**8 个用例全绿**。FR-N01 用两个角度钉：①`SALES_MANAGER` 被授 `users`/`roles` 时确实可见（这正是 V75 的真实形态）；②**虚构角色 `AUDITOR_V2`** 同样按授权放行——有人加回 `role === 'X'` 分支即变红。FR-N02 用「清单里没有的键」直接断言不可见；FR-N03 用 ANALYST 的基准授权作锚；FR-N04 断言 ADMIN 在授权为空/缺失两种情况下都是全量，并附「真实清单 56 项 / 11 组」的规模断言，防止清单退化成空时上述断言静默通过）
- [x] T013 [US1] 删除 `frontend/src/App.tsx:494` 的 `isAdmin ? adminRoutes... : []` 整组硬门，改由 T011 的纯函数决定可见性（保留 ADMIN 的全量兜底分支，FR-N04）（**已完成**：`menuRoutes` 无条件含 `adminRoutes`、删掉 `ADMIN_ONLY_GROUP_I18N_KEYS` 常量与 `isAdmin` 局部变量，可见性统一走 `resolveVisibleMenuKeys`。**效果已逐项实测**：13 个角色新出现的项**合计 16 处**，与 T002 记录的明细逐角色逐键一致，且**可路由 == 授权项数**对全部 13 个角色成立（SC-N02），无一个角色丢失可见项；见 `verification.md` §T013。门禁：`tsc` / `eslint .`（全仓 0 problem）/ `pnpm test` 20 文件 75 用例 / `i18n:check` / `menu:check` 全绿）
- [x] T014 [US1] 新增 `backend/src/test/java/com/crm/support/MenuReadPermissionTestSupport.java`：① 菜单项 → 所需读码对照表（**全部菜单项**逐项填写，不需码的显式写 `OPEN`）；② 预置角色授予的解析器，从 `backend/src/main/resources/db/migration/` 的迁移文件读 `role_menu` / `role_permission` 的插入——**白名单式**，遇无法识别的 `INSERT INTO role_menu|role_permission` 形态立即失败并打印该语句，并断言「解析到的语句数 == 文件中出现的语句数」。**`role_menu` 的语句形态已实测为 6 种（含 `UPDATE`/`DELETE`/`WHERE r.code <> 'ADMIN'` 三种非平凡形态），清单与出处见 `verification.md` 的「供 T014 解析器参考」一节；防呆计数的分子分母必须把 `V46` 的 `CREATE TABLE role_menu` 计入或排除，否则会误报**（**已完成**：对照表 `MENU_REQUIREMENTS` 逐项填满 56 项、三种形态 CODE/OPEN/ROLE 各带可复核的依据文本（C3 断言 `hasSize(56)` 且与 `MENU_TREE` 逐键相等）；解析器从 83 个迁移文件推演，**实测 75 条授权语句、75 次出现**（防呆断言相等，见 C5）。**首跑即暴露一处真实缺陷**：`keywordOf` 只取首词，`INSERT INTO` / `CREATE TABLE role_menu` 全部落进 `unrecognized` 分支抛错——这正是白名单式设计要的结果（宁可变红也不静默跳过），修好后重跑通过。**补做 T014 规格里未落地的一条**：「语句数 == 出现次数」原先只是两个 getter、无人断言，现补成 C5 的真实断言，并加三条**结果级**断言（改键重放、DELETE 重放、`<> 'ADMIN'` 取补集解成「除 ADMIN 外全部 12 个角色」），因为计数相等在今天的实现里是构造性的、单独不足以证伪）
- [x] T015 [US1] 新增 `backend/src/test/java/com/crm/security/MenuAccessGrantAlignmentTest.java`，实现 research.md 决策 ③ 的四条断言：C1（任意角色：已授菜单 ⇒ 持有所需读码）、C2（对照表里的每个码都必须被某个端点的 `@RequirePermission`/`@PreAuthorize` 校验）、C3（每个菜单项填了码或 `OPEN`）、C4（不存在指向不存在菜单项的死授权）。**先运行、先变红，把红项逐条记录**——红项就是待修的缺口清单（**已完成**：C1~C5 五个断言落地，**先运行、先变红**——首跑 `Tests run: 5, Failures: 2`，红项恰好两条，详见 `verification.md` §T015。红项就是 T020 的输入清单：**只有两条真实缺口**，比规划时的预判少得多（`users`/`roles`/`departments`/`sla-policies`/`playbook`/`mail-sync`/`open-platform`/`workflows` 全部已被 V80~V84 接好线，无需再动）：①`ANALYST × custom-objects`（C1+C2 各报一次）；②`FINANCE_MANAGER × currencies`（C1））
- [x] T016 [US1] 在 `backend/src/main/java/com/crm/common/RoleConstants.java` 的 `PERMISSION_DEFS` 中新增读码 `custom_object:read`（契约 §3；先例＝V84 §3 为 `custom_field` 新增读码）。**依赖 T001 的批准**（**已完成**：`PERMISSION_DEFS` 的「自定义对象」组新增 `custom_object:read`「查看自定义对象」，注释写明「配置面读码」与「`/{id}/records*` 不设码」两个边界。门禁：`RequirePermissionCatalogTest` 绿（注解 ⊆ 字典））
- [x] T017 [US1] 新增迁移 `backend/src/main/resources/db/migration/V85__menu_ia_and_custom_object_read.sql`：把 `custom_object:read` 授予 `ADMIN` 与 `ANALYST`（形态与 V84 §3 一致，含「为什么是这些角色」的注释）；并按 T015 的 C4 结果清理死授权（若 C4 报零，则不含 DELETE 语句并在注释中记明）。**依赖 T001 的批准**（**已完成**：`V85__menu_ia_and_custom_object_read.sql`：`custom_object:read` → `ADMIN`+`ANALYST`，形态与 V84 §3 一致（含已发布矩阵的兑现说明与 FR-N24 批准记录：批准人 龙星、2026-09-12）。**C4 实测零红项**，故按约定不含 `DELETE`，并在 §2 注释里记明依据）
- [x] T018 [US1] 在 `backend/src/test/resources/schema-h2.sql` 中镜像 V85 的授予（既有规则「新增迁移必镜像」）。**只镜像本迁移**，不扩到 V70–V77 的存量缺口（那是 083 的 B 块/T072）（**已完成**：`schema-h2.sql` 末尾镜像 V85 的授予；**未**触碰 V70–V77 的存量缺口（083 B 块/T072））
- [x] T019 [US1] 改造 `backend/src/main/java/com/crm/controller/CustomObjectController.java`：5 个**对象定义**端点的类级/方法级 `hasRole('ADMIN')` 改为 `@RequirePermission`——`GET`→`custom_object:read`、`POST`→`custom_object:create`、`PUT`→`custom_object:update`、`{id}/toggle`→`custom_object:update`（实施时确认）、`DELETE`→`custom_object:delete`。`/{id}/records*` 五个端点**不动**（契约 §1）。**依赖 T001 的批准**（**已完成**：5 个定义端点 `hasRole('ADMIN')` → `@RequirePermission`（read/create/update/update[toggle]/delete），`/{id}/records*` 五个端点**逐字未动**；类注释写明批准依据与「定义面 vs 记录面」的分界。`git diff --stat` 实测该控制器 +26/-… 行，**只改守卫**）
- [x] T020 [US1] 按 T015 报出的 C1 红项逐项修复（补齐权限码或收回菜单授权，两者择一），覆盖**全部 10 个预置角色**而非点名的 5 个（SC-N08）。若为 0 项，把「逐角色零缺口」的断言输出留作证据——不得以「看起来没问题」作结（FR-N06）（**已完成**：C1 两条红项逐项修复，覆盖全部 13 个角色（10 预置 + 3 内建）后**零缺口**。①`custom-objects`：T016–T019 接线；②`currencies`：**用户裁决「接线到权限码」**（2026-09-12）——新增读码 `currency:read`（`V86__currency_read_and_gate.sql`，授 ADMIN/SALES/FINANCE_MANAGER，读写分码），`CurrencyRateController` 5 个端点按码放行；SALES 补授只为保持其改造前已有的读/折算能力，写码不扩授。这是 084 的第二处实质扩权，已在迁移抬头写明批准与范围。**注意**：T021 的「diff 只触及 CustomObjectController」因此改为**两个控制器**，属已批准的偏差）
- [ ] T021 [US1] 跑通 `mvn -B -o test -Dtest='MenuAccessGrantAlignmentTest'` 至全绿，并按 quickstart.md 的 D1（ANALYST 主场景）、D2（10 个预置角色逐一 + `ADMIN`/`SALES` 两个回归项）、D3（内置管理员兜底）做手工验收并留痕。**另**核对本分支的 diff **只触及 `CustomObjectController` 的守卫**、未改动其他 Controller 的鉴权注解，**【范围订正，T041，2026-09-12】原文这半句写于「接线到权限码」裁决之前，现已与事实不符**：本分支另改了 `CurrencyRateController` 的 **5 个守卫**（`hasAnyRole('ADMIN','SALES')`／`hasRole('ADMIN')` → `@RequirePermission("currency:read")`／`("currency:manage")`，配套 V86），那是 T015 报出的第二条 C1 红项按裁决修复的结果。**执行 T021 时按此口径核对**：允许出现的改造是「`CustomObjectController` + `CurrencyRateController` 两个守卫」，后者须能对上 V86 与 FR-N24；**「未改动其他 Controller」这半句仍然有效**——多出来的第三个 Controller 才是越界。订正理由与出处见 Phase 8 的 T041。且既有安全类 IT 全绿（如钉着「SUPPORT 建字段→403」的那条）——这是 FR-N05「本规格不得削弱任何既有的服务端校验」的落地证据，也是 US1 验收场景 5 的机械形态（**机械部分已完成，浏览器部分待人工**：①门禁 `mvn -B -o test -Dtest='MenuAccessGrantAlignmentTest'` → **5 用例 0 失败**，`RequirePermissionCatalogTest` 1/1。②行为层新增 `PermissionEnforcementIT#menuIaBatchGatesByCode`（真登录、打真接口），并用**两次变异**证明它不是空转：把守卫改回角色字面量 → `Status expected:<200> but was:<403>`（ANALYST 读定义面）；撤掉读端点注解 → `expected:<403> but was:<200>`（VIEWER 不再被挡）。两次均先还原再复跑绿。③既有安全类 IT 显式跑过：`PermissionEnforcementIT`/`CustomObjectIT`/`MultiCurrencyIT`/`PermissionMatrixIT`/`SystemEnhancementIT` 共 **22 用例全绿**——注意 failsafe 尚未接入（083 的 A 块），这些是靠 `-Dtest` 手动触发的，接入后才会随 `verify` 自动跑。④diff 范围核对：`git diff --stat -- controller/` = **只有两个控制器**（`CustomObjectController`、`CurrencyRateController`），后者是 T020 里用户已批准的偏差；其余 60 余个 Controller 的鉴权注解逐字未动。**未做**：quickstart D1/D2/D3 的浏览器步骤（本会话无浏览器）。其判据的机械替代关系已在 `verification.md` §T021 写明，其中「侧边栏实际渲染（分组标题、组内空则不渲染整组）」一层仍需人工过一遍）（**补记，2026-09-12**：机械一半已实跑全绿——`mvn -B -o test -Dtest='MenuAccessGrantAlignmentTest'` → **6 run / 0F / 0E，BUILD SUCCESS**（2.262 s）。其中 **C1 正对 T015 报出的最后一条红项 `FINANCE_MANAGER × currencies`**，故该红项的「菜单已授 ⇒ 持有对应权限码」已由断言证明，并已随 T039 补入契约二 §7。**剩余只有手工一半**：quickstart D1/D2/D3 需在**含 V85/V86 的后端实例**上做（在旧实例上，ANALYST 打开「自定义对象」必然 403——那正是被测行为本身），而 8081 上的实例启动于 16:54、早于本次编译（20:27）且可能属并行会话 `crm-gap-remediation`，故本次未重启。该限制与 SC-N07 的适用范围限制**同源同因**，如实留为未完成项）

**Checkpoint**: SC-N01（已授但永不出现 = 0）、SC-N02（可见 == 授权）、SC-N04（已授但 403 = 0）均达成。

---

## Phase 4: User Story 2 - 配置侧与使用侧的菜单名称、分组一致（Priority: P0）

**Goal**: 同一菜单项/分组在两侧的名称逐字相同，且以配置侧为准（FR-N08–N12）。

**Independent Test**: 角色页勾选树与侧边栏逐项比对——7 项名称、4 个组名、全部项的分组归属均一致。

> **口径说明（评审时请注意）**：按 FR-N09「以配置侧为准」，统一方向是**前端向 `MENU_TREE` 对齐**，因此侧边栏的「服务协作 → 客户服务」「营销中心 → 营销管理」「流程与配置 → 流程配置」「审计与维护 → 审计维护」。`spec.md` 的 FR-N13–N18 散文里出现的「审计与维护域」「客户域」等说法是对同一批分组的非正式指称，**显示名一律以权威处为准**；这处措辞不一致在 T036 一并订正。

- [x] T022 [US2] 扩展 `backend/src/test/java/com/crm/security/MenuRouteAlignmentTest.java`：在既有的「项」维度（路由→菜单键）之上补「分组」与「名称」两个维度——断言生成物中每个菜单项的分组与成员集合 == 权威处；`frontend/src/i18n/zh-CN.ts` 的 `menu.<i18nKey>` 值 == 权威处 title（逐字）、每个生成物的 `i18nKey` 在 `zh-CN` 与 `en` 中都存在。**先运行、先变红**（**已完成**：本类由 2 条用例扩为 5 条，全绿。新增的正是任务要求的三条：① 分组维度 `manifestGroupsMatchTheAuthority`——从 `MENU_TREE` 直接读「分组→成员」（新增 `PermissionDictionaryTestSupport.menuGroups()`，与既有展平逻辑同处，遵循「解析权威处只留一处」），与生成物逐组比对组名/顺序/成员/项名，并断言 11 组且两侧同规模；② 名称维度 `menuTitlesMatchTheAuthorityVerbally`——zh-CN 的 `menu.<i18nKey>` 值与权威名**逐字**相等（项与组都查）；③ 键存在维度 `everyManifestI18nKeyExistsInBothLocales`——66 个键（56 项 + 11 组、「首页」组项共用 `menu.home`）在 zh-CN 与 en 都存在。**先红后绿已实测**：首跑为红，列出 **13 处**不一致，与另用一次性 Node 脚本独立比对得出的清单**逐条相同**（两套独立实现互证，避免「测试照抄待修清单」这种自证）。**与任务原文的两处偏差**：ⓐ 原文说「7 处菜单项名称」，实测是 **9 处**——多出 `tasks`（任务→任务管理）与 `approvalFlows`（审批流配置→审批流），规划期的人工审计漏了这两项；判据是「两侧逐字相同」这条需求本身而非那个数字，故以实测为准（明细见 `verification.md` §T022）。ⓑ **en 侧不比 value**：权威名是中文，英文界面无法与之逐字相等，en 的约束只有「键存在」——这条口径写进了断言注释，否则该断言会写成一句无意义的话。**首跑暴露的两处实现缺陷**（都是「静默通过」形态，被防呆断言拦下）：Ⓐ `LOCALE_ENTRY` 缺 `MULTILINE` 时一条都取不到（`find()` 下 `^` 只匹配整段开头），被「至少 66 条」的下限断言拦下;Ⓑ 生成物解析原先按 `\n` 切分，CRLF 检出下整行 `matches()` 因行尾多一个回车而全部落空，现象是「菜单项出现在任何分组之前」——报错信息与真实原因（换行符）相去甚远，已改为按可选回车切分并写明理由）
- [x] T023 [P] [US2] 按权威处改写 `frontend/src/i18n/zh-CN.ts` 与 `frontend/src/i18n/en.ts` 的 7 处菜单项名称（方向＝侧边栏向配置侧对齐，`en` 同步改以保证 FR-N11）：酷炫大屏→**数据大屏**、工单管理→**客户服务**、部门→**部门管理**、续约管理→**合同续约**、数据保留→**数据保留策略**、SLA 日历→**SLA日历**、销售Playbook→**销售 Playbook**（一律以权威处 `title` 逐字为准，`en` 侧给对应的英文译名而非中文）（**已完成**，但 **en 侧实际改动为 0 条**，此处记录偏差与理由）：zh-CN 的 **9 处**（非原文的 7 处，见 T022 偏差 ⓐ）已按权威处逐字改写。en 侧实测**每一条都已是英文且与权威名同义**——`Tickets`=工单管理、`Departments`=部门管理、`Data Vision`=数据大屏、`Renewals`=合同续约、`Data Retention`=数据保留策略、`Tasks`=任务管理、`Approval Flows`=审批流、`Sales Playbook`=销售 Playbook、`SLA Calendar`=SLA 日历；英文页面文案侧用的也是同一批词（`pages.ticket.list.title = Tickets` 等），故改英文名只会制造用户可见的抖动而无一致性收益。任务原文的「`en` 同步改以保证 FR-N11」应读作**条件式**（改中文名时不要往 en 贴中文），该条件在此不触发；FR-N11 的实体是**键对齐**，它不受只改 value 的影响，已由 `i18n:check` 与 T022 的第③条断言各自把住。**方向的一处旁证**（说明「向配置侧对齐」确实改对了）：改 `tickets` 为「工单管理」之后，侧边栏与工单各页（`pages.ticket.list.title=服务工单`、`pages.ticket.detail.ticketInfo=工单信息`…）的用词才一致；改之前侧边栏把一个「工单」页面叫成「客户服务」，还与它所在的「客户服务」组同名）
- [x] T024 [US2] 同上两个文件：4 处分组名对齐（服务协作→客户服务、营销中心→营销管理、流程与配置→流程配置、审计与维护→审计维护）（**已完成**：zh-CN 的 4 组按权威处改写——营销中心→**营销管理**、服务协作→**客户服务**、流程与配置→**流程配置**、审计与维护→**审计维护**；其余 7 组的值实测已与权威名相同，未动。en 侧 4 组同样 0 改动，理由同 T023：`Marketing` / `Service & Support` / `Configuration` / `Audit & Maintenance` 均已是英文且语义与权威名对应，且权威名是中文、无从「逐字对齐」）
- [x] T025 [US2] 清除指向已不存在菜单项的文案键（FR-N12；至少含 `menu.board`——`grep` 确认全仓无引用后再删，两种语言同步删以保持键对齐）（**已完成，但实际只删了 2 个键、比预期少**——`grep` 之后才知道为什么）：全仓核查发现，「生成物不引用」的 6 个键里有 **4 个仍在用**：`BreadcrumbNav.tsx` 用 `menu.customerMerge` / `menu.channelRoi` / `menu.emailUnsubscribes` / `menu.workflowLogs` 给「借分组显示」的子页面（`/customer-merge`、`/marketing/roi`、`/email-unsubscribes`、`/workflows/logs`）做面包屑标签，删掉会让面包屑渲染出 `menu.xxx` 字面量——正是本 spec 要根除的故障形态，等于「按 FR-N12 删键」反而造出一批 FR-N12 的违规。真正无引用的只有 `menu.board`（值「数据大屏」，V80 改键前 `board` 菜单项的遗留）与 `menu.planned`（值「（规划中）」），两种语言同步删除，键数 2892 → 2890，`i18n:check` 通过。**另有动态取值面已一并核查**：`RecycleBinPage.tsx` 用 `t(`menu.${m.label}`)` 拼键（`TYPE_META` 的 `customers`/`leads`/`contacts`/`opportunities` 四值），不涉及上述两个被删的键。**一处遗留观察（不在 T025 范围内，不擅自扩权）**：面包屑用的 `menu.customerMerge`/`menu.emailUnsubscribes` 与清单的 `menu.merge`/`menu.unsubscribe` 值相同且指向同一页面，今天不产生可见差异，但两条键并存是漂移面，记入 `verification.md` §T025 的遗留观察）
- [x] T026 [US2] 落地边界情况「缺文案降级」：`frontend/src/App.tsx` 的降级显示改用生成物中的权威 `title`，**不得**渲染出 `menu.xxx` 键名（删掉 `MENU_I18N_KEYS` 后原有的降级分支需重写）（**已完成**——实施时点早于本阶段，本阶段核查确认现状即满足）：`App.tsx` 的 `labelOf(i18nKey, title)` 在 `t()` 返回键名本身时降级为生成物里的权威 `title`，项（`groupItems`）、组（`groupedMenuItems`）、置顶项（`topLevelItems`）三处渲染全部经它取值，**不存在**渲染出 `menu.xxx` 的路径；`MENU_I18N_KEYS` 已从代码中删除（`grep -rn "MENU_I18N_KEYS" src/ scripts/` 仅命中 3 处**注释里的历史说明**，无代码引用）。边界情况「缺文案降级」因此落地为「渲染权威中文名」而非键名，且该名字与角色配置页显示的一致（FR-N09））
- [x] T027 [US2] 跑通 T022 的断言至全绿，并跑 `cd frontend && pnpm i18n:check` 确认双语键对齐零失败（FR-N11）（**已完成**，全绿）：`mvn -B -o test -Dtest=MenuRouteAlignmentTest` → 5 用例通过；`pnpm i18n:check` → `✓ 语言资源一致：zh-CN 2890 键 / en 2890 键；菜单路由与清单双向对齐（路由 58 条 / 清单 56 项，粗粒度别名 3 条）`；`pnpm menu:check` → 生成物最新（56 项）；`npx tsc --noEmit` → 0；`pnpm test` → 20 文件 75 用例全绿；`mvn spotless:check` → BUILD SUCCESS。**反向验证（本题的机械核心，三处人为篡改各自变红、逐字节复原后复跑全绿）**：① 把 `menu.tickets` 改回旧名 → `menuTitlesMatchTheAuthorityVerbally` 失败；② 把组名 `menu.audit` 改回旧名 → 同上失败；③ 把 `data-retention` 从「审计维护」挪回「数据分析」组 → `manifestGroupsMatchTheAuthority` 失败（分组归属维度）。明细与失败文本见 `verification.md` §T022）

**Checkpoint**: 角色页与侧边栏逐项一致（SC-N03 的名称与组名维度 = 0）。

---

## Phase 5: User Story 3 - 菜单归属按业务域归位（Priority: P1）

**Goal**: 归属调整的**结果被验证**，且确认未改变任何角色的可见集合（FR-N19），未改变两个借分组子页面的范围（边界情况）。

> 机制（`MENU_TREE` 的归属改动）已在 Phase 2 的 T003 落地——它必须早于生成器。本阶段是验证与收口，不是实施。

- [x] T028 [US3] 断言并留痕 FR-N19：归属调整**不改变任何角色的可见集合**——`role_menu` 只存 `menu_key`、不含分组（data-model.md §3），故可见性不受归属影响；用一次实测比对（T002 基准 vs 现状，逐角色）证明并记入验收记录（**已完成**：新增 `MenuAccessGrantAlignmentTest` 的 **C6** `regroupingDidNotChangeAnyRoleGrantSet`——13 个角色逐个与 T002 基准（改造前由另一次一次性脚本推导出的数字）比对授权项数，实测**逐一相等**：ADMIN 28 / ANALYST 7 / FINANCE_ACCOUNTANT 8 / FINANCE_MANAGER 13 / MARKETING_MANAGER 15 / MARKETING_SPECIALIST 11 / SALES 22 / SALES_MANAGER 26 / SALES_REP 21 / SUPPORT 8 / SUPPORT_AGENT 10 / SUPPORT_MANAGER 16 / VIEWER 12。**结构性依据**（为什么「授权集合不变」就足以支持 FR-N19）：可见集合 ＝ 授权集合 ∩ 可渲染集合；`role_menu` 只有 `menu_key` 一列、**没有分组列**（data-model.md §3），归属根本进不了可见性的计算，而「可渲染集合」也不因搬动而变（C3 已钉住生成物与 MENU_TREE 逐键相等，56 项）。另把 T002 记录的 16 处「已授权却被 `isAdmin` 硬门隐藏」（5 角色 × 7 键）固化为 containment 断言，把两件事分开归因：**授权侧一直是齐的，缺的是渲染**（渲染侧的守卫在 `menuVisibility.test.ts` 的 FR-N01 用例）。**反向验证**：把基准里 SUPPORT 的 8 改成 9 → C6 单独变红，打印 `SUPPORT：改造前 9 项，现在 8 项 → [approvals, contacts, customers, exports, knowledge, satisfaction, stats, tickets]`（指名到角色 + 两侧计数 + 实际解析到的集合）；逐字节复原后复跑全绿。注：这处反向验证打在基准数字上，**没有**往 `db/migration/` 放探针文件——本工作区多会话并行，临时迁移文件会进别人的构建与 Flyway 扫描；改错基准同样能变红，且失败信息里已打印真实解析结果，足以证明断言读的是真实数据（理由记入 `verification.md` §T028））
- [x] T029 [US3] 校验 SC-N03 的归属维度 = 0：`MENU_TREE`（权威）与 `menuManifest.ts`（派生）中每一项的分组一致，且无「角色页勾了、侧边栏换组」的项（`data-retention` 是改造前的唯一一例，应已消除）（**已完成**：T022 新增的 `manifestGroupsMatchTheAuthority` 即本题的机械形态——从 `MENU_TREE` 直接读「分组→成员（有序）」与生成物逐组比对组名/顺序/成员/项名，两侧各 11 组（并断言同规模）。现状核对：`data-retention` 在 `MENU_TREE` 属「审计维护」、生成物同组，`pnpm menu:check` 报「最新（56 个菜单项）」，归属维度不一致数 = **0**，改造前那一例已消除。**反向验证**：把生成物里的 `data-retention` 从「审计维护」挪回「数据分析」→ 该断言变红（`Tests run: 5, Failures: 1`，见 `verification.md` §T022 篡改 ③），复原后复跑全绿。**「角色页勾了、侧边栏换组」无残留**：角色页渲染 `MENU_TREE`、侧边栏渲染生成物，两者分组逐项相等即无此形态；`pnpm i18n:check` 的「路由 58 条 / 清单 56 项双向对齐」另证明没有找不到归属的项）
- [x] T030 [US3] 边界情况确认（quickstart.md D4）：① 两个「借分组显示」的子页面（`/marketing/roi`、`/workflows/logs`）可见范围与改造前一致，且被护栏钉成恰好这两条；② 空分组不渲染；③ 归位后不出现「组内唯一项与组名同名」的重复标签（**已完成**：三个边界各自钉住并反向验证——① **借分组子页面恰好两条**：新增 `subPageBorrowersStayExactlyTwo`，断言 `App.tsx` 的 `SUB_PAGE_AFTER_MENU_KEY` 恰好 `/marketing/roi`→`marketing`、`/workflows/logs`→`workflows`，且每条与 `menuKeys.ts` 的 `COARSE_ALIASES` 指向**同一个**菜单项（可见性取自别名表、渲染位置取自本表，两处不一致会让页面被「甲的可见性」控制却显示在乙的分组下）、锚点在 `MENU_TREE` 里存在、两条路径都有路由。**反向验证**：把 `COARSE_ALIASES` 的 `/marketing/roi` 改成 `customers` → 变红并打印 `["/marketing/roi 的锚点不一致：SUB_PAGE_AFTER_MENU_KEY='marketing' COARSE_ALIASES='customers'"]`。② **空分组不渲染**：`App.render.test.tsx` 新增用例，用 ANALYST 的 7 项真实授权渲染整个 App，断言有可见项的三组（工作台/数据分析/流程配置）与置顶「首页」出现、7 个空组（客户管理/销售管理/交易管理/营销管理/客户服务/系统管理/审计维护）不出现，并带正向对照。③ **组内唯一项与组名同名**：新增 `noItemEchoesItsGroupName`，断言非置顶的 55 项中没有任何一项与所在组同名或同文案键（改名前 `tickets` 与「客户服务」组同名正是此形态）；置顶组按 `App.tsx` 的 `TOP_LEVEL_GROUP_I18N_KEYS` 排除，且该集合被断言**恰好** `{home}`——否则排除规则会变成万能逃生口。**反向验证**：把生成物里 `tickets` 的标题改成「客户服务」→ 变红并打印 `["项 tickets（客户服务）与它所在的分组同名"]`。**顺带修掉一处既有缺陷**（不在 084 任何需求内，但正落在本项与 T021 的渲染层要用的那种测试上）：`App.render.test.tsx` 与 `pages/leads/LeadListPage.render.test.tsx` 的 `vi.mock` 路径指向**不存在的目录**（前者指到仓库外的 `E:\code\crm\services\`），mock 静默失效、测试照样绿；已改对路径并各加一条「mock 真的被调用过」的正向对照断言，反向验证见 `verification.md` §T030。**T021 的浏览器层验收（quickstart D1/D2/D3）仍待人工**——自动化覆盖的是渲染层，不是真实浏览器观感）

**Checkpoint**: 归属维度一致（SC-N03）、可见集合未因归位而变（FR-N19）。

---

## Phase 6: User Story 4 - 菜单定义单一真相源与防复发校验（Priority: P1）

**Goal**: 权威处唯一、使用侧派生（FR-N20），且护栏经过反向验证并纳入常规入口（FR-N21–N23）。

**Independent Test**: 新增一个菜单项只改权威处一处，两侧同时正确；人为制造一处不一致则构建变红。

- [x] T031 [US4] 反向验证（FR-N22，quickstart.md 的 B1/B2/B3）：在名称、授权、生成物陈旧三处各人为制造一次不一致，确认三道断言分别变红且**报错指名到具体项**；每次验证后只还原自己动过的那个文件（**禁止** `git checkout -- .`／`git restore .`，本工作区常有多会话并行），结果留痕（**已完成，三处各做一次，全部逐字节复原**：① **名称**——把 `frontend/src/i18n/zh-CN.ts` 的 `menu.tickets` 改成「工单管理x」→ `MenuRouteAlignmentTest` `Tests run: 7, Failures: 1`，报 `["项 tickets（menu.tickets）：侧边栏='工单管理x' 权威='工单管理'"]`；② **授权**——把 `V85` 的授权对象从 `('ADMIN', 'ANALYST')` 收成 `('ADMIN')`（去掉 ANALYST 的 `custom_object:read`）→ `MenuAccessGrantAlignmentTest` `Tests run: 6, Failures: 1`，报 `ANALYST 的 custom-objects：需要 custom_object:read（CustomObjectController#page…）；持有码=[…]`；③ **生成物陈旧**——把 `quotas` 从销售管理挪到交易管理且不重生成 → `pnpm menu:check` 报「陈旧…有 7 行不一致」并逐行打印「磁盘上 / 重生成」两侧内容与修复命令。**复原后**：`pnpm menu:check`「✓ 最新（56 个菜单项）」、`pnpm i18n:check`「✓ 路由 58 / 清单 56」、两个测试类 13 项全绿；三个文件 `cmp` 与备份逐字节一致。**未使用** `git checkout -- .` / `git restore .`（本工作区常有多会话并行）。**一处订正**：quickstart 的 B2 原写「删掉 V75 里 ANALYST 的一条 role_permission（如 custom_object:update）」——实测删它**不会**变红：菜单 `custom-objects` 要求的是 `custom_object:read`，V75 给 ANALYST 的是 create/update/delete，读码在 V85。已把 quickstart 的 B2 改准（见 `verification.md` §T031 的踩坑记录，含 CRLF 字面量匹配失败一次、未落盘））
- [x] T032 [US4] SC-N06 实验：在 `MENU_TREE` 新增一个菜单项（或在组间移动一项）→ 确认 `pnpm menu:check` 报陈旧 → 重生成 → 两侧同时正确；把实验步骤与结论回填 `spec.md` 的成功标准区（规格要求「以一次实验证明，而非以设计上应当代替」）（**已完成，三种情形各做一遍，结论已回填 `spec.md`**：**C1 移动**（`quotas` 销售管理→交易管理）：未重生成 → `pnpm menu:check` 报陈旧（7 行差异）；`pnpm menu:gen` → 通过（56 项）；生成物里 `quotas` 已落在 `交易管理` 组（第 66 行 vs 组头第 63 行）；后端 7 项断言全绿——**改一处 + 重生成即两侧一致**。**C2 新增**（`probe-experiment`「实验项」）：未重生成 → 报陈旧（20 行差异）；重生成 → `menu:check` 通过（57 项）；但 `pnpm i18n:check` 报红并**指名**新项（「清单引用了不存在的 menu.* 键（1）：menu.probeExperiment」与「清单里的路由在 App.tsx 里不存在：probe-experiment」）；后端四道计数断言同时变红并各自打出 `probe-experiment`（对照表 56→57、文案键 66→67、`noItemEchoesItsGroupName` 的 examined 55→56）。**C3 改名**（销售配额→销售配额（自测改名））：`menu:check` 报红、重生成后 `menu:check` 与 `i18n:check` 都通过，但 `menuTitlesMatchTheAuthorityVerbally` 报红并指名 `quotas`。**回填内容**：`spec.md` 成功标准区新增「SC-N06 实验」小节（三情形结论表 + 「为什么改名是两处」），并把 SC-N06 原文「改名**或**移动…只改权威定义处一处」订正为「移动一处、改名两处、新增四处」；另在 FR-N20 下加「口径补充」。**订正不是静默的**：原文与实测的差异、原因、被哪道断言拦下都写在 `verification.md` §T032。全部篡改已复原（`cmp` 逐字节一致），复原后两道前端检查与 13 项后端断言全绿）
- [x] T033 [US4] FR-N23 最终确认：两道护栏都在常规构建入口——后端随 `mvn -B verify` 的 surefire 运行（无需网络与数据库）；前端 `pnpm menu:check` 与 `pnpm i18n:check` 在 CI 的前端作业中。核对 `.github/workflows/ci.yml` 并记录证据（**已核对并留证**：**后端**——`MenuRouteAlignmentTest`(7 项) 与 `MenuAccessGrantAlignmentTest`(6 项) 都是 `*Test` 命名，随 `mvn -B verify` 的 surefire 运行（`.github/workflows/ci.yml` 的 backend 作业跑 `mvn -B verify`）；两者只解析源码与迁移文件，不起 Spring 上下文、不连数据库、不联网——本会话全程以 `mvn -B -o`（离线）跑绿即为证。**前端**——同一个 `ci.yml` 的 frontend 作业有 `run: pnpm run i18n:check` 与 `run: pnpm run menu:check` 两步（各自带注释说明分工：前者管「路由与清单双向对齐」，后者管「生成物与权威处一致」，并标注 FR-N23），两步都排在 `pnpm run build` 之前；`package.json` 中两个脚本分别指向 `scripts/check-i18n.mjs` 与 `scripts/check-menu.mjs`。故两道护栏都不只是"本地可用"）
- [x] T034 [US4] 核验 FR-N21 的失败信息质量：三条断言在失败时都必须**指出具体项**（哪个菜单键/哪个角色/缺哪个码/差哪一行），而非只给计数——逐条查看一次失败输出并留痕（**已逐条查看失败输出**，四类断言全部**指名到具体项**，无一条只给计数：① `menuTitlesMatchTheAuthorityVerbally` → `项 tickets（menu.tickets）：侧边栏='工单管理x' 权威='工单管理'`；② C1 `grantedMenusAreActuallyOpenable` → `ANALYST 的 custom-objects：需要 custom_object:read（出处…）；持有码=[…]`；③ `pnpm menu:check` → 「第 N 行 / 磁盘上 / 重生成」逐行两侧 + 修复命令；④ 新增情形下四条断言各自打出新项的键（`probe-experiment` / `menu.probeExperiment` / examined 实际值）。**两处如实记为"审读而非实跑"**：C2（对照表里的码必须被端点校验）与 C4（死授权）在正常态是空跑，其报错信息只有缺口出现时才可见，而这二类缺口不在 quickstart 的 B1–B3 里，本次未制造；这两条只做了代码审读——C2 打出「菜单键 → 码（出处）」、C4 打出「角色 → 死键」并附原因与修法（见 `verification.md` §T034））

**Checkpoint**: 单一真相源成立且有反向验证过的护栏；SC-N05、SC-N06 达成。

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T035 [P] 清理 `planned` 占位机制的死代码（`grep "planned: true"` 零命中，已在 research.md 决策 ⑤ 显式登记）：删除 `App.tsx` 中的 `'planned' in r && r.planned` 分支与相关类型（**已完成**：`grep -rn "planned" frontend/src/App.tsx` 零命中（`App.tsx` 已被 T013 重写，重写时未带上该分支）；全仓库 `grep -rn "planned: true" frontend/src/` 亦零命中，占位机制无残留。类型侧随重写一并消失——`menuManifest.ts` 的项类型不含 `planned` 字段）
- [x] T036 [P] 文档订正与登记：① 回改 `spec.md` 的假设 1/2（按 D1/D2 的批准结果）与 FR-N13–N18 里的非正式域指称；② 回填 FR-N24 的批准人与日期；③ 按 082 的 7 处先例登记 `specs/README.md` 与 `specs/roadmap.md`（**已完成三处**：① `spec.md` 假设区的两条各带 ⚠️ 段，写明"在规划阶段被证伪"、登记为 plan 偏差 D1/D2 并按 FR-N24 批准后撤销原文后半句；FR-N13 起一律改称**分组**并附术语订正说明（`grep "审计与维护域\|客户域"` 零命中）；② `spec.md:145` 记录**批准人 龙星 / 批准日期 2026-09-12**，并写明批准范围＝ANALYST 对自定义对象定义的读写（含 V85 迁移与 D2 的端点语义变更），同条覆盖 plan 的 D1 与 D2；③ `specs/README.md` 模块表新增 084 行（契约列 = `authorization-semantics`）、版本行改「V1~V86 迁移，85 张表」、编号说明补 084 与 083 的差别、迁移对照表新增 V85/V86 两行；`specs/roadmap.md` 的「最后更新」「整体覆盖度」与 `## 当前进度` 三处均已更新）
- [x] T037 全量门禁（quickstart.md §E）：`cd backend && mvn -B -o spotless:check && mvn -B verify`（**先 spotless**，它在 verify 相位里排在 failsafe 与 jacoco 之前，格式违规会让判定点前移）；`cd frontend && pnpm lint && pnpm typecheck && pnpm i18n:check && pnpm menu:check && pnpm test:coverage`。覆盖率阈值**不得下调**；既有 e2e 零失败（SC-N07）（**未完成——前端全绿、后端红且红因在本规格范围之外，须项目负责人裁决，不得自行批准**。**前端五项全过**：`pnpm lint` 0 problem；`pnpm typecheck` 0 error；`pnpm i18n:check`「✓ 2890/2890 键，路由 58 / 清单 56」；`pnpm menu:check`「✓ 最新（56 项）」；`pnpm test:coverage` **20 文件 / 79 用例全通过、退出码 0**，statements 46.86 / branches 70.03 / **functions 21.55**（阈值 33.6 / 47.2 / 21.4，**三项阈值均未下调**）。**后端 `spotless:check` 通过**（724 文件、0 需改动）。**`mvn -B verify` 失败**：surefire **548 run / 0F / 0E** 全绿（含本规格新增的 `MenuRouteAlignmentTest`、`MenuAccessGrantAlignmentTest`、以及被本规格修好的 `SchemaParityIT` 2 项），failsafe **259 run / 4F / 0E**，构建在 `failsafe:verify` 中止（相位实序：jacoco:report → spotless:check → failsafe:verify ✗ → jacoco:check 未到达）。**4 例失败与本规格无关，且与 083 已记录的 4 例逐项、逐行号一致**：`IntegrationHubIT.integrationFlow:93`、`OpportunityIT.closeWithoutResultReturns422:175`、`UserIT.disableUserRevokesAccess:117`、`UserIT.userLifecycle:74`。依据 **083 `spec.md:177`** 与 **083 `tasks.md` 的 T068（CRITICAL）**：这 4 例属产品决策、明确写在 083 范围之外，且 **T068 要求的"作为显式偏差批准"至今未见批准**。按治理节「任何偏差必须明确说明理由**并经批准**」，本任务**不得**以"既有失败"为由自判通过；需项目负责人二选一（批准为显式偏差 / 推动产品决策修复）。**SC-N07（既有 e2e 零失败）：实跑 `pnpm test:e2e` → 37 passed / 0 failed、退出码 0**，但适用范围须限定——dev server 服务的是工作区当前源码，故**084 的前端改动被端到端覆盖，084 的后端改动未被覆盖**（跑的是 16:54 启动的既有后端实例，而 084 的 `RoleConstants.class` 编译于 20:27；该进程可能属并行会话，本次未重启）。后端侧要覆盖需在含 V85/V86 的实例上重跑，已记为显式未完成项。前后端本次的完整判定链与原始输出见 `verification.md` §T035–T037）（**收口，2026-09-12**：阻塞已由 T040 解除——用户裁决把该 4 例**批准为显式偏差**（批准人 龙星 / 2026-09-12，记录于 `specs/083-engineering-consolidation/spec.md`）。同时补测复现了一直缺失的那半条判定：`mvn -B verify -Dmaven.test.failure.ignore=true` 使构建越过 `failsafe:verify`，`jacoco:check` 报 `All coverage checks have been met.`（实测 INSTRUCTION `44 080 / 56 288 = 0.7831` ≥ 阈值 `0.73`，**阈值未下调**；**该判定首次取得于 `5bcd4b1`、实测 0.7818 且已记入 `pom.xml`，本次只是"在 084 的树上复现"，不是首次**）。故结论是：红的**完全**由那 4 例造成，与覆盖率门槛、前端门禁、格式都无关。**勾选＝"门禁已执行、判定已作出、无未知项"，不表示"后端构建为绿"**——后端构建仍红，那条红是登记在 083 的已批准偏差，其对象是产品决策，不在本规格范围。判定链见 `verification.md` §「T037 收口」。**未完成项仍有一处**：在含 V85/V86 的后端实例上重跑 e2e，以覆盖 084 的后端侧改动）
- [x] T038 提交：先 `ListAgents` 确认无并行会话正在本工作区改动，再**用显式路径** `git add`（**禁止** `git add -A` / `git commit -a`），提交信息按 Conventional Commits 并在正文写入 FR-N24 的批准人与日期（**已完成，2026-09-12**：主体改动提交于 `b44a2ca`（正文含 FR-N24 批准人 龙星 / 批准日期 2026-09-12）；本次收敛收口提交于 `06cf1ac`（同一批准信息，并补齐 T037/T039/T040/T021 的记录）。两次提交前均先跑 `ListAgents` 确认无并行会话在改动，且**只用显式路径** `git add`（未用 `git add -A` / `git commit -a`）。）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖；T001 是**全规格的交付闸门**
- **Foundational (Phase 2)**: 依赖 Setup；**阻塞全部用户故事**
- **US1 (Phase 3) / US2 (Phase 4)**: 均依赖 Foundational；彼此独立（US1 改可见性与授权，US2 改文案）
- **US3 (Phase 5)**: 依赖 Foundational 的 T003；与 US1/US2 无耦合
- **US4 (Phase 6)**: 依赖 US1 与 US2 的护栏全部就位（T015、T022）
- **Polish (Phase 7)**: 依赖全部用户故事

### 关键依赖链

- T001 → T016 → T017 → T019（**批准是扩权改动的硬前置**）
- T003 → T004 → T006 → T007（权威定稿才能生成，生成才能派生）
- T007 → T013 → T021（派生重构在前，行为变更在后，两者分离使 US1 的改动可独立验证）
- T014 → T015 → T020（对照表与解析器 → 断言 → 按红项修复）
- T022 → T023/T024 → T027

### Parallel Opportunities

- T004 与 T005（生成器 / 陈旧性校验脚本，不同文件）
- T011 与 T014（前端纯函数 / 后端测试支撑，不同语言不同文件）
- T023 与 T024 各自内部可拆，但两者改同一批语言文件，**不要并行**
- T035、T036（清理与登记，不同文件）

### 不可并行（同文件）

`App.tsx`：T007 → T013 → T026（顺序）
`RoleConstants.java`：T003 → T016（顺序）
`zh-CN.ts` / `en.ts`：T023 → T024 → T025（顺序）

---

## Implementation Strategy

### MVP First

Phase 1 → Phase 2 → **Phase 3（US1）** → 停下独立验收（SC-N01/N02/N04）→ 即可交付。US1 单独交付就能让 5 个预置角色被挡在门外的 7 个菜单项真正可用，这是本规格价值最高的一段。

### Incremental Delivery

1. Setup + Foundational → 机制就位、行为未变（可安全合并）
2. US1 → 可见性修复（MVP）
3. US2 → 名称与分组一致
4. US3 → 归属验证收口
5. US4 → 护栏反向验证与入口确认
6. Polish → 登记、门禁、提交

### 红→绿的纪律

T015、T022 **必须先运行并记录红项**。若在实施对应修复前它们就是绿的，说明护栏没在起作用（例如对照表被写成恒真、或解析器零命中），按缺陷处理——见 T034。

---

## Notes

- [P] = 不同文件、无依赖
- 每个用户故事可独立完成与验证；US1 是 MVP
- 涉及扩权的 T016–T019 在 T001 完成前一律不得开始
- 后端 Java 改动后、提交前跑一次 `mvn -B -o spotless:check`；要修只针对自己改的文件跑 `mvn -B spotless:apply -DspotlessFiles=".*(FileName)\.java"`
- 本工作区常有多会话并行：**禁止** `git add -A`、`git commit -a`、`git checkout -- .`、`git restore .`，也**不要**跑不带过滤的全仓 `spotless:apply`
- 不要顺手修复 `schema-h2.sql` 的 V70–V77 存量缺口（083 的 B 块/T072）

---

## Phase 8: Convergence

> 本相位由 `/speckit-converge`（2026-09-12）追加，**只追加、不改写**上文任何既有任务。
> 每条都追溯到具体来源，`(gap-type)` 为 missing／partial／contradicts／unrequested。

- [x] T039 **CRITICAL** 为**第二处端点授权语义变更**补契约与批准记录：`CurrencyRateController` 的 5 个守卫已由 `hasAnyRole('ADMIN','SALES')`／`hasRole('ADMIN')` 改为 `@RequirePermission("currency:read")`／`@RequirePermission("currency:manage")`（读：`GET /currencies`、`POST /currencies/convert`；写：`POST`／`PUT`／`DELETE`），并新增 `V86` 读码、授予 ADMIN/SALES/FINANCE_MANAGER。它与 plan 的 **D2 属同类**（端点判定语义变更），却既不在 `contracts/authorization-semantics.md`（其 §1 明确只覆盖自定义对象端点）、也不在 `plan.md` 的 D1/D2 条目、更不在三处批准记录（contracts §6、plan「FR-N24 批准位」、`spec.md:145`）之内——**唯一记录是 `V86` 的 SQL 注释**，且只写「（FR-N24，需批准的偏差，已批准）」**无批准人姓名**。按 **Constitution 一（契约不得被静默修改，不可协商）**、**治理节**与 **FR-N24**（"必须在本规格内记录批准人与批准日期后方可实施"）：需把契约范围扩到覆盖多币种端点（或新增一份同类最小契约），并把**批准人＋批准日期**填入上述三处指定位置。**注意这不是"要不要改"的问题**（用户已于 2026-09-12 裁决「接线到权限码」），而是"记录位置不合 FR-N24 要求" (missing)（**已完成，2026-09-12**：落成**第二份**最小契约 `contracts/currency-endpoint-authorization.md`，§6 记批准人**龙星**、批准日期 **2026-09-12**；同一条款已补入 `plan.md` 的「FR-N24 批准位」（放行能力由 1 处扩为 2 处）、`spec.md` 的「批准记录（FR-N24）」，并在 plan 的偏差 D2 行与 `spec.md` 假设 2 的订正处各加一处交叉引用。**未改写契约一**——其 §1 范围声明与 §6 批准记录是已批准的在案文本，改写它会把"批准了 A"与"批准了 A+B"混为一谈，故按模块各留一份。契约内逐条事实依据：5 个端点的变更前守卫取 `git show b44a2ca~1:…/CurrencyRateController.java` 实测（`:39`/`:70` 为 `hasAnyRole('ADMIN','SALES')`，`:47`/`:54`/`:62` 为 `hasRole('ADMIN')`）；`currency:manage` 全仓仅 `V75:228` 一处授予（`FINANCE_MANAGER`）；`currency:read` 已在字典 `RoleConstants.java:333` 定义并已镜像进 `schema-h2.sql`；`ADMIN` 走 `PermissionAspect` 内建直通（故其可访问性不依赖 V86 的授予）。**未改任何代码**——V86 与 Controller 的改动在 T020 已完成，本次只补记录与署名。）
- [x] T040 **CRITICAL** 取得并记录「`mvn -B verify` 红」的显式偏差批准：现状为 failsafe **259 run / 4F / 0E**，构建中止于 `failsafe:verify`（相位实序 jacoco:report → spotless:check → failsafe:verify ✗ → jacoco:check 未到达），4 例＝`IntegrationHubIT.integrationFlow:93`、`OpportunityIT.closeWithoutResultReturns422:175`、`UserIT.disableUserRevokesAccess:117`、`UserIT.userLifecycle:74`，与本规格无关且与 083 的记录逐项逐行号一致。**Constitution 四（不可协商）**要求"每次合并必须通过构建、单元测试、集成测试、Lint、类型检查以及已配置的覆盖率门槛"，**治理节**要求"任何偏差必须明确说明理由**并经批准**"；083 的 `tasks.md` T068（CRITICAL）早已要求负责人二选一（① 作为显式偏差批准并记录批准人与日期／② 推动产品决策修复），**至今未见批准**。本规格**不得自行认定该偏差可接受**，故 T037 在具备批准前不能勾选 (contradicts)（**已完成，2026-09-12**：用户裁决取 T068 的**选项①**——批准为**显式偏差**。批准记录写入 `specs/083-engineering-consolidation/spec.md` 新增小节「### 偏差批准记录（T068：`mvn -B verify` 不通过的显式偏差，2026-09-12）」，记批准人**龙星**、批准日期 **2026-09-12**，并写明三条边界（只覆盖该 4 例，出现第 5 例即越界／不下调覆盖率阈值／不覆盖全绿的前端五道门禁）与三条失效条件（任一例被修复、出现第 5 例、门禁相位被改动）。**该记录不主张 `verify` 已通过**，`SC-G01` 的判定口径未作修改——批准改变的是这条红的治理状态，不是它的存在。）
- [x] T041 订正 T021 的范围声明：T021 要求核对"本分支的 diff **只触及** `CustomObjectController` 的守卫、未改动其他 Controller 的鉴权注解"，而 `CurrencyRateController` 的 5 个守卫已按已批准的结果一并改为权限码（V86）。该声明写于"接线到权限码"裁决之前，现已与事实不符——按原文执行会把**已批准的改动判成越界**，甚至可能被"修正"回去。需在 T021 中把它改写为"只触及 `CustomObjectController` 与 `CurrencyRateController` 两个守卫，且后者已由 FR-N24 覆盖"，并保留「未改动其他 Controller」这一仍有意义的半句 (contradicts)（**已完成**：未覆盖原文，而是在 T021 的该句后追加「【范围订正，T041，2026-09-12】」注记，写明原句写于裁决之前、允许的改造集合与仍有效的半句——与仓库既有做法一致：订正不静默、原文留痕。**该订正只改任务口径、不改任何代码**）
> 【2026-09-13 追加，只增不改】以下 T042–T043 与前四条同属本相位，但由**后续一次**复核追加
> （前四条为 2026-09-12 那次）。两条都指向**同一处**收口后缺陷：提交 `226d55c` 已修复，此处补登记以留可追溯。
> 详细验收记录见 `verification.md` 的「面包屑收口：第五处菜单定义作者」节。
- [x] T042 消除**第五处菜单定义作者**：`BreadcrumbNav.tsx` 自带 `GROUPED_ROUTES`（7 组）与 `MENU_KEY_MAP`（56 个路径 → 文案键）两张手写表，**同时决定项、分组、名称——正是 FR-N21 的三个维度**，与 **FR-N20**「使用侧的分组与名称必须由权威处派生，不得手写第二份清单」直接冲突；而 `plan.md` 的 Structure Decision 反向声明「残留的、刻意保留的前端本地映射**只有三处**，均不属于这三个维度」，该枚举在实施期即不成立。实测后果三门：`/opportunity-stages`（商机阶段）两张表里都没有 → 落兜底段渲染成「首页 / 当前页面」；配置类页面面包屑写「系统管理」而侧边栏写「流程配置」（后者在那张表的词汇表里根本不存在）；`MENU_KEY_MAP:136` 的 `'/data-vision': '酷炫大屏'` 是中文裸字面量，英文界面仍显示中文。**已完成，2026-09-12**（提交 `226d55c`，10 文件 +630/−240）：删除两张表，改由 `components/breadcrumbTrail.ts` 纯函数解析（分组/顺序/名称取自 `MENU_MANIFEST`，路径取自 `menuKeys.ts` 新增的 `pathOfMenuKey`，置顶组的判定改取清单的可观测形态以免与 `App.tsx` 成环引用），`BreadcrumbNav.tsx` 退化为渲染器；`App.tsx` 与面包屑共用 `i18n/labelOf.ts` 的 `menuLabel`（缺键降级的唯一实现）；删除随收口失去引用的 `pages.breadcrumbGroup.*`（两语言文件各 7 键）；顺带收掉不可达分支 `isNestedDetail`。`plan.md` 原句后已追加【范围订正，2026-09-13】注记，原文留痕。详细验收见 `verification.md` 的「面包屑收口」节 (contradicts)
- [x] T043 补上 **FR-N21 的管辖缺口**：三道既有护栏都只覆盖「可由生成物或路由表推导的那部分」——`menu:check` 只比对生成器产出、`i18n:check` 只断言路由↔清单双射与 `menu.*` 键存在、`MenuRouteAlignmentTest` 解析 `App.tsx` 与授权数据——因此**一张自立的消费侧表可以长期落后而不产生任何断言**，这正是第五份副本得以存在的条件；按 FR-N21「必须存在一道机械校验，断言配置侧勾选项与使用侧可渲染项在**项、分组、名称**三个维度上互相包含」，其覆盖面实为**部分未满足**。**已完成，2026-09-12**（同一提交 `226d55c`）：补两道前端护栏（`components/breadcrumbTrail.test.ts` 遍历清单 **56 项**断言每项都解析出分组与项、**0 项**落兜底段，并用一份**虚构清单**证明分组/顺序/名称是派生而非与抄来的表恰好一致；`components/BreadcrumbNav.test.tsx` 断言渲染出的文案与链接——分组名跟侧边栏、置顶项不渲染指向自身的链接、详情段不是链接）与一道后端护栏（`MenuRouteAlignmentTest.derivedPathsAreRealRoutes`，断言每个清单项的规范路径是一条**真实声明的菜单路由**；该断言横跨「前端推路径的规则」与「前端路由表」，而前端测试跑在 jsdom 下读不了文件，故只能放后端）。**两个方向都已反向验证**：去掉 `opportunity-stages` 候选 → 前端 3 条变红；清空 `CANONICAL_PATH_OVERRIDES` → 后端变红并列出悬空路径 `at-risk → /at-risk`。**已知限度，不掩盖**：遍历式护栏对「删项」有近似恒真性，故另有虚构清单与组件渲染两条独立证据；本项**不替代** T021 的手工验收（仍阻塞于含 V85/V86 的后端实例）。**未新增规格、未新增迁移、未改端点**——它落在既有 FR-N20/N21 的管辖下，故不动 `spec.md` 的需求文本 (partial)
