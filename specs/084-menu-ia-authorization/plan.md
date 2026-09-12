# Implementation Plan: 菜单信息架构与授权可见性收口

**Branch**: `084-menu-ia-authorization` | **Date**: 2026-09-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/084-menu-ia-authorization/spec.md`

## Summary

把菜单的「定义」从四处收敛为一处，并让「看得见」与「打得开」都能被机械校验。

**权威处 = 后端 `RoleConstants.MENU_TREE`**（它已经是角色配置页勾选树的来源，也已经是 `user.menus` 的产出源）。前端的分组、顺序、名称**改为构建期从该处的生成物派生**，不再手写第二份清单（FR-N20）。围绕这条权威线补两道护栏：

1. **静态护栏（三维互含）**：扩展既有的 `MenuRouteAlignmentTest`，把「项」的对齐扩到**项 / 分组 / 名称**三个维度，并加一条生成物陈旧性校验与一条反向验证（FR-N21/N22）。
2. **授权护栏（已授 ⇒ 打得开）**：新增一道只读源文件的测试，以「菜单项 → 该模块读接口所需权限码」对照表为输入，断言**任意角色**的每条菜单授权都伴随所需读码（FR-N06 的可重复形态），并断言对照表里的每个码都真的被某个端点校验（顺带消灭「只在字典里、无人引用」的死码）。

两处实质修复随之落地：删掉 `App.tsx` 里按角色名整组硬门掉 19 项的 `isAdmin` 开关（FR-N01/N02）；把分组归属、7 处名称、4 处组名按配置侧对齐（FR-N08–N19）。

## Technical Context

**Language/Version**: 后端 Java 17 + Spring Boot 3.2.0（Maven）；前端 TypeScript 5.3 + React 18 + Vite 5 + antd 5.22（pnpm 11.7.0）

**Primary Dependencies**: 后端 Spring Security / MyBatis-Plus / Flyway；前端 react-i18next / react-router。**本规格不新增任何运行时依赖**——生成器与校验脚本用 Node 内置 `node:fs`（沿用 `scripts/check-i18n.mjs` 的既有做法）

**Storage**: 关系型数据库（Flyway 版本化迁移）。菜单授权存于 `role_menu`（`role_id` + `menu_key`，**不含分组**）；权限码存于 `role_permission`。本规格**无 DDL 变更**；有一处**数据迁移**（见「与规格假设的偏差」）

**Testing**: 后端 JUnit 5 + surefire（`*Test`，纯文件解析，不启 Spring 上下文、不连库）；前端 vitest / `scripts/*.mjs` 静态检查。**护栏必须可在无网络、无数据库的条件下运行**，否则无法成为常规构建入口（FR-N23）

**Target Platform**: 服务端（Linux / 容器）+ 浏览器。护栏需要 `frontend/` 与 `backend/` 同处一个检出——`MenuRouteAlignmentTest` 已确立并会在找不到仓库根时显式失败

**Project Type**: Web application（`backend/` + `frontend/` 双工程，单仓库）

**Performance Goals**: 无运行时性能目标。本规格不改变任何请求路径的服务端行为（FR-N05），护栏只在构建期执行，代价为毫秒级文件解析

**Constraints**:
- 前端检查**不能**写进 vitest：前端测试跑在 jsdom 下、无 `file:` 协议的 `import.meta.url`、未装 `@types/node`（`menuKeys.test.ts` 的既有结论）。因此「读源文件」的校验一律落在 Node 脚本或后端测试
- 生成器读的是 `backend/` 下的 Java 源文件，故 `frontend` 的构建产物依赖同一检出内的后端源码。这与既有护栏的假设一致，不引入新耦合
- 前端既有两条护栏口径必须保持：`scripts/check-i18n.mjs`（双语键对齐 + 路由覆盖）与 `MenuRouteAlignmentTest`（同一套正则解析 `App.tsx`）

**Scale/Scope**: 56 个菜单项、11 个分组、10 个预置角色（另加 `ADMIN`/`SALES` 两个既有角色作回归项）、4 处定义点 → 收敛为 1 处权威定义 + 1 份生成物 + 2 道护栏。改动面：后端 7 个文件（权威常量 + 1 条迁移 + 1 个 Controller + 1 处测试库镜像 + 3 个测试/测试支撑）、前端 10 个文件（2 个脚本 + 1 份生成物 + 可见性纯函数与其单测 + `App.tsx` + 双语表 + `check-i18n.mjs` + `package.json`）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 原则 | 判定 | 依据 |
|---|---|---|
| **一、契约优先（不可协商）** | ⚠️ 通过，**附最小契约** | 本规格不新增端点、不改请求/响应结构。但自定义对象的 5 个定义端点由 `hasRole('ADMIN')` 改为权限码放行，**改变了既有端点的授权判定**——按 V80–V84 已确立的「无端点变更则无契约、有端点语义变更则有契约」，产出 `contracts/authorization-semantics.md` 记录判定语义的变化。这正是原则一「契约不得被**静默**修改」要求的形态：记录而非静默 |
| **二、分层架构** | ✅ 通过 | 改动落点是 Controller 的注解、常量定义、前端表现层与构建期生成物。生成物只承载**结构数据**（分组、顺序、文案键），不含业务规则；侧边栏可见性过滤本就属表现层，且 FR-N05 明确服务端校验不削弱 |
| **三、安全与校验（不可协商）** | ⚠️ 通过，**需 FR-N24 批准** | 本规格**放宽 UI 可见性**，故必须证明这不是「靠隐藏 UI 做访问控制」的反面——而是反过来：把此前恒 403 的入口还给「菜单已授且持有权限码」的角色，且**每个放行项都由服务端权限码闸门把守**（由授权护栏机械证明）。`CustomObjectController` 由「仅内置管理员」改为「按显式权限码」是**把隐式角色门换成可审计的码**，非放开。实质扩权部分须按 FR-N24 记录批准人与日期 |
| **四、测试优先与质量门禁（不可协商）** | ✅ 通过 | 护栏先行：先造出会红的机械校验（FR-N22 反向验证）再改定义与渲染，符合红→绿。覆盖率门槛不得下调。护栏纳入常规入口（FR-N23：后端随 `mvn verify`，前端与 `i18n:check` 并列进 CI） |
| **五、简洁、可维护** | ✅ 通过 | 「单一真相源」取**构建期生成**而非运行时下发（见 research.md 决策 ①）：不新增端点、不新增运行时依赖、不改安全上下文。复用既有 Node 脚本形态与既有后端解析测试，YAGNI |
| **治理节：偏差须说明理由并经批准** | ⚠️ 见下节 | 本计划发现三处与规格假设不符的事实，单列「与规格假设的偏差」一节请求批准；FR-N24 的记录位亦在此 |

**Phase 1 设计后复审（结论：门禁不新增违规）**：Phase 1 落定的三份产物没有改变上表结论，只把其中两处 ⚠️ 的处置具体化——
① `contracts/authorization-semantics.md` 把「有端点语义变更则有契约」落成了一份可评审的最小契约（原则一由此从「记录在注释里」升级为「记录在契约里」）；
② 授权护栏（决策 ③）把原则三的要求「授权必须在服务端强制执行」变成**机械可证**的断言（已授 ⇒ 有码、码必须被端点校验），并顺带检出「字典里有、无人引用」的死码；
③ `data-model.md` 的不变量 I8 显式声明本规格不削弱任何服务端校验的强度。
新增的两处偏差（D1 的数据迁移、D2 的契约）已在上表登记，均属治理节「明确说明理由并经批准」的范畴，**不构成章程违规**，故 Complexity Tracking 不登记违规项。

## 与规格假设的偏差（需批准）

Phase 0 侦察发现三处事实与 `spec.md` 的「假设」不符。**不静默改写规格**，在此显式登记，理由与影响如下；批准后回改 `spec.md` 的假设 1/2 并在本文件留痕。

| # | 规格假设 | 侦察到的事实 | 影响与理由 |
|---|---|---|---|
| **D1** | 假设 1「预期不新增版本化迁移」（无数据库结构变更） | 无 DDL 变更仍成立，但**必须新增一条数据迁移**：`PERMISSION_DEFS` 里**没有 `custom_object:read`**（只有 create/update/delete 三个码），而「自定义对象」菜单页首屏是对象定义列表（`GET /custom-objects`）。要让 ANALYST「打得开」，必须新增该读码并授予 ADMIN/ANALYST → 新增 `V85__*.sql`，并把授予镜像进 `schema-h2.sql` | 有先例且形态一致：V84 §3 就是「本迁移新增读码 `custom_field:read`」同一做法。规格自身的例外条款（「若实施中发现某处必须改数据，需在本规格内显式说明并补齐镜像」）已授权这条路径，此处按该条款显式说明 |
| **D2** | 假设 2「无接口契约变更…FR-N06 的处置只调整谁能通过闸门，不改变闸门的判定语义」 | 前半句成立（无结构变更、无新端点），后半句**不成立**：自定义对象的 5 个定义端点从「角色名判定」换成「权限码判定」，判定语义确实变了（原先持有 `custom_object:*` 的 ANALYST 恒 403，改后按码放行）。<br>**【实施期追加，T039，2026-09-12】同类实例**：多币种端点的 5 个守卫也在实施期（T020）由角色字面量改为权限码，同属「判定语义变更」；该实例由 T020 报告、属本行所述偏差的**第二处**实例，故不另立 D 行 | 按原则一产出最小契约 `contracts/authorization-semantics.md`（自定义对象）。这也与 V80–V84 批量「撤门接线」的历史口径一致：那些改动同样只被迁移文件的注释记录，本次因涉及**扩权**而升级为契约文档。<br>**【实施期追加，T039】多币种**另有契约二 `contracts/currency-endpoint-authorization.md`——分列两份是因为契约一的范围声明与批准记录是**已批准的在案文本**，改写它会把「批准了 A」与「批准了 A+B」混为一谈 |
| **D3** | 假设 1「不改测试库镜像」 | 与 D1 连带：`V85` 的授予需镜像进 `schema-h2.sql`（既有规则「新增迁移必镜像」）。反之，**本规格不修复** `schema-h2.sql` 缺失 V70–V77 的存量问题——那是 083 的 B 块（T072） | 边界必须写清，否则实施时容易顺手去补 V70–V77 而把两个规格的范围搅在一起 |

**FR-N24 批准位**（实施前必须填，否则该分支不得交付）：

- 放行的能力（**共两处**，均为「菜单/矩阵已授、端点却恒 403」的断链接线）：
  1. 数据分析师（ANALYST）获得**自定义对象定义**的读、写能力（读：新增的 `custom_object:read`；写：其已持有的 `custom_object:create/update/delete`，此前因类级 `hasRole('ADMIN')` 恒 403）
  2. **【实施期追加，T039，2026-09-12】** 财务经理（FINANCE_MANAGER）获得**多币种**的读、写能力（读：新增的 `currency:read`；写：其 `V75` 早已持有的 `currency:manage`，此前因角色字面量恒 403）；`SALES` 补授读码以**维持现状**，非扩权。详见契约二 `contracts/currency-endpoint-authorization.md` §4
- 批准人：**龙星**
- 批准日期：**2026-09-12**
- 记录位置：本节 + `spec.md` 的 FR-N24 + 提交信息正文；两处能力各自的契约见 `contracts/authorization-semantics.md` §6 与 `contracts/currency-endpoint-authorization.md` §6

## 规划期决策记录（规格留有空白、由规划阶段拍板）

`/speckit-analyze` 复核发现两处规格正文没有给出可实施答案的点。它们不是「信息缺失」而是「必须有人拍板的选择」，故在此记录决定与理由，并同步回改了 `spec.md`（改的是措辞与判据，不是需求方向）。

| # | 空白 | 决定 | 理由与被否决的其他方向 |
|---|---|---|---|
| **P1** | FR-N18 只说「字段权限与自定义字段**同域**」，未说归到哪一组。现状 `field-permissions` 在「系统管理」、`settings/custom-fields` 在「流程配置」——两个方向都合理，且机械校验对任一方向都会放行（选错了不会有任何断言变红，这是最危险的一类空白） | 移 `field-permissions` → **「流程配置」** | 「流程配置」已经装着 `settings/custom-fields` 与 `custom-objects`，把字段权限并入即成完整的「数据模型配置面」；反向移动（把 `custom-fields` 挪进「系统管理」）会拆散刚聚合好的配置面，且「系统管理」的定位是账号与组织（用户/角色/部门/币种），字段级权限不属于它 |
| **P3** | plan 的 Structure Decision 原文把生成物字段写成「含 menuKey、path、i18nKey」，未说明 `path` 从何而来 | 生成物**不含 `path`**，字段为 `menuKey / i18nKey / title`（分组为 `title / i18nKey / items`） | 路由 path 是前端路由表的事，且「path → menuKey」的翻译规则已在 `menuKeys.ts` 里成文（含 3 条别名）。把 path 也生成一份，等于给路由拼写开第二个作者——正是本规格要消灭的形态。渲染侧改为「遍历生成物的分组与有序项 → 用 `menuKeyOf(path)` 把路由表里的项认领进来」，图标与懒加载组件仍由路由表提供 |
| **P2** | SC-N06 原文「新增一个菜单项**只改权威定义处一处**」对新菜单项**字面上不成立**——新项还需补路由/页面与界面文案；照字面判定会得到一个永远无法通过的验收标准 | 把实验形态改为**改名或组间移动**（真正只需改一处），并在 SC-N06 中显式写明「新增项另有路由与文案这两步，它们描述的是页面是否存在、不是菜单定义的第二个作者」 | 不接受「就按新增项做实验、把路由与文案也算作一处」——那会让「单一真相源」的判据退化成可以自圆其说的说法；也不接受悄悄放宽而不记录，因为这条成功标准是 US4 的核心验收面 |

## Project Structure

### Documentation (this feature)

```text
specs/084-menu-ia-authorization/
├── plan.md                                    # 本文件
├── research.md                                # Phase 0：三项设计决策与取舍
├── data-model.md                              # Phase 1：定义模型、对照表与不变量（无表结构）
├── contracts/
│   └── authorization-semantics.md             # Phase 1：自定义对象端点的授权语义契约（见 D2）
├── quickstart.md                              # Phase 1：可运行的验证场景（含反向验证与单一真相源实验）
├── checklists/requirements.md                 # 已完成，16/16
└── tasks.md                                   # Phase 2 产物（/speckit-tasks）
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── common/RoleConstants.java              # 权威处：分组归属按 FR-N13–N18 调整；PERMISSION_DEFS 增 custom_object:read
└── controller/CustomObjectController.java # 5 个定义端点：hasRole('ADMIN') → @RequirePermission

backend/src/main/resources/db/migration/
└── V85__menu_ia_and_custom_object_read.sql # 新增读码的授予 + 死授权清理（FR-N07）

backend/src/test/
├── java/com/crm/security/
│   ├── MenuRouteAlignmentTest.java         # 扩展：项/分组/名称三维互含 + 生成物陈旧性 + 反向验证
│   └── MenuAccessGrantAlignmentTest.java   # 新增：已授 ⇒ 打得开（对全部预置角色）
├── java/com/crm/support/
│   └── MenuReadPermissionTestSupport.java  # 新增：菜单项 → 读码对照表 + 预置角色授予解析
└── resources/schema-h2.sql                 # 镜像 V85 的授予（仅此，不扩到 V70–V77）

frontend/
├── scripts/
│   ├── gen-menu.mjs                        # 新增：从 RoleConstants.java 生成 menuManifest.ts；文案键派生规则与例外表的唯一实现
│   └── check-menu.mjs                      # 新增：重新生成 → 比对 → 陈旧即失败
└── src/
    ├── constants/menuManifest.ts           # 生成物（勿手改）：分组标题 / 组文案键 / 有序菜单项（含 menuKey、i18nKey、title；不含 path，见 P3）
    ├── constants/menuKeys.ts               # path → key（既有，仅在三条例外别名有变时改）
    ├── constants/menuVisibility.ts         # 新增：可见性判定的纯函数（供前端单测，见 §Structure Decision 4）
    ├── constants/menuVisibility.test.ts    # 新增：vitest 单测（FR-N01–N04 的前端一侧）
    ├── App.tsx                             # 分组渲染改由生成物派生；删除 MENU_I18N_KEYS 手写表与 isAdmin 硬门
    └── i18n/{zh-CN,en}.ts                  # 名称对齐 + 死键清除（FR-N08/N09/N12）

.github/workflows/ci.yml                    # 前端作业新增 menu:check 步骤（与 i18n:check 并列）
```

**Structure Decision**: 采用仓库既有的 Web application 布局（`backend/` + `frontend/` 同仓库）。关键结构决策有三条，均由「让护栏能跑、且不引入新耦合」推出：

1. **权威定义留在后端**（`RoleConstants.MENU_TREE`），不迁到前端或独立配置文件——它已经是角色配置页与 `user.menus` 的产出源，迁走会新增一处需要同步的真相源。
2. **派生方式是构建期生成**：`frontend/scripts/gen-menu.mjs` 读 `RoleConstants.java` 写 `frontend/src/constants/menuManifest.ts`，`App.tsx` 消费生成物。生成物进版本库（可见、可评审、可 diff），并由 `check-menu.mjs` 做「重新生成 → 比对 → 不一致即失败」的陈旧性校验（沿用本仓库 spotless 的判据形态）。
3. **两道护栏分居两处，各管一半**（沿用 `menuKeys.test.ts` 已确立的分工）：**语义**维度（项/分组/名称三维互含、授权 ⇒ 读码）在后端测试里做——它本来就要读前端源文件；**陈旧性**维度（生成物是否最新）在前端 Node 脚本里做——生成器就在那里，放进后端会写出第二份生成逻辑。

4. **文案键的派生规则只在 JS 侧实现一次**，例外表（7 条项例外 + 11 条组映射）留在 `gen-menu.mjs`，解析结果写进生成物；后端护栏读生成物的 `i18nKey` 字段而不复刻规则。理由与决策 ② 否决「后端复刻生成逻辑」相同：同一个派生规则两份实现必然漂移。之所以不让 `MENU_TREE` 自己携带文案键，是因为 `MENU_TREE` **就是** `/api/v1/roles/menu-tree` 的响应体，给它加字段等于改该端点的响应结构（详见 research.md 决策 ④）。另外，把可见性判定抽成 `menuVisibility.ts` 里的纯函数，是为了让 FR-N01–N04 能有**前端单测**——交互逻辑不抽出来就只能靠手工点验，而这正是本规格要消灭的形态（与「后端护栏读源文件」分工，各管一半）。

残留的、**刻意保留**的前端本地映射只有三处，均不属于 FR-N21 的三个维度（项/分组/名称），故不违反单一真相源：文案键**例外表**（它不决定有哪些项、属于哪组、叫什么名字，权威处改了组名而它没跟会直接报错）；分组 → 图标（antd 图标是 JSX，无法进入生成物）；两个「借分组显示」的子页面（`/marketing/roi`、`/workflows/logs`）。最后一项会被断言钉成恰好这两条，不允许增长（规格边界情况已要求该集合不因归位而扩大）。

## Complexity Tracking

> 本规格无章程违规项，故本节不登记违规。以下登记的是**规格假设的偏差**（见上节），按治理节「任何偏差必须明确说明理由并经批准」的要求在此汇总。

| 偏差 | 为何需要 | 被否决的更简方案 |
|---|---|---|
| 新增数据迁移 `V85` 与一个读码 `custom_object:read`（D1） | 「自定义对象」菜单页首屏必须能读对象定义列表；字典里没有读码，就只能给写码，而给写码意味着「只读地打开这个页面」需要创建权限——那是错的口径 | ①复用 `custom_object:create` 当读码：语义错误，且会让「能看」等价于「能改」。②收回 ANALYST 的「自定义对象」菜单授权：会缩小可见集合，与 FR-N03「不得额外放大**也不得缩小**」冲突，且把已做好的功能收回而非交付 |
| 产出最小契约 `contracts/authorization-semantics.md`（D2） | 5 个端点的授权判定语义确实改变；原则一要求契约变更非静默 | 按「加固类不产契约」先例不写：该先例的前提是「无端点语义变更」（003 即如此），本例不满足该前提 |
