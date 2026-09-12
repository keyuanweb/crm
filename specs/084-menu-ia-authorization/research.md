# Phase 0 Research: 菜单信息架构与授权可见性收口

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

本阶段需要为规格的三处开放设计落定实现形态（plan 的输入 ①②③），外加三处由侦察发现的连带决策。
每条给出**决策 / 理由 / 被否决的替代方案**，理由一律以仓库内可核实的证据为准。

---

## 侦察结论（决策的共同前提）

| 事实 | 证据 |
|---|---|
| 菜单定义目前散在四处 | ① `backend/src/main/java/com/crm/common/RoleConstants.java` 的 `MENU_TREE`；② `frontend/src/App.tsx` 的路由数组 + `groupedMenuItems` + `MENU_I18N_KEYS`；③ `V46/V75/V80/V84` 的 `role_menu` 种子；④ `frontend/src/i18n/{zh-CN,en}.ts` 的 `menu.*` |
| `role_menu` **只存菜单项标识、不存分组** | 迁移里 `INSERT INTO role_menu (role_id, menu_key)`，无分组列 → 归属调整与改名**零数据迁移**（FR-N19 由此成立） |
| 配置页的菜单树就是 `MENU_TREE` 本身 | `RoleService.menuTree()` 直接返回 `RoleConstants.MENU_TREE`；端点 `/api/v1/roles/menu-tree` 受 `@RequirePermission("role:manage")` |
| 使用侧的可见性当前是**两重门** | `App.tsx:529` 按 `user.menus` 过滤（正确），但 `:494` 另有 `isAdmin ? adminRoutes : []` 的整组硬门，把「系统管理/流程与配置/审计与维护」19 项对非 ADMIN 全部切断 |
| 19 项里有 7 项**已授而不可见** | V75 给 SALES_MANAGER / SUPPORT_MANAGER / MARKETING_MANAGER / FINANCE_MANAGER / ANALYST 授了 `users`/`departments`/`roles`/`sla-policies`/`currencies`/`settings/custom-fields`/`custom-objects`；对应端点已是 `@RequirePermission` 闸门（如 `UserController:51` 用 `user:manage`） |
| `PERMISSION_DEFS` 里**没有** `custom_object:read` | `RoleConstants.java:340-342` 只有 create/update/delete；`CustomObjectController` 的 5 个定义端点全是 `@PreAuthorize("hasRole('ADMIN')")` |
| 迁移的授予语句形态高度规整 | 统一样式 `INSERT INTO \`role_(permission\|menu)\` ... SELECT r.id, '<值>' FROM \`role\` r WHERE r.code IN (...)`（V84 全文），另有一处 `JOIN (SELECT ... UNION SELECT ...) p` 的多码形态 |
| 后端已有「读前端源文件」的护栏与成熟做法 | `backend/src/test/java/com/crm/security/MenuRouteAlignmentTest.java`：自带仓库根解析、正则口径与 `check-i18n.mjs` 一致，且**先断言输入非空**（`assertThat(menuKeys).hasSizeGreaterThan(50)`）以避免「零命中通过」的虚假安心 |
| 前端测试读不了文件 | `frontend/src/constants/menuKeys.test.ts` 的 javadoc：jsdom 下无 `file:` 协议的 `import.meta.url`，且未装 `@types/node`；「为了读一个文件引入 Node 类型依赖，不值得」 |
| 前端已有 Node 脚本做跨文件静态检查 | `frontend/scripts/check-i18n.mjs`（`pnpm i18n:check`，已进 CI），且明确不在 CI 里引入 tsx/vite-node |
| 死代码 | `grep "planned: true"` 零命中（`planned` 占位机制已无实际使用者）；`menu.board` 文案键无人引用 |

---

## 决策 ①：单一真相源的落地形态 = **构建期生成**

**决策**：以后端 `RoleConstants.MENU_TREE` 为唯一权威定义处；前端由 `frontend/scripts/gen-menu.mjs` 生成 `frontend/src/constants/menuManifest.ts`（内容：有序的分组列表，每组含分组 id、组文案键、以及该组有序菜单项 `{menuKey, path, i18nKey}`），`App.tsx` 的分组渲染、顺序与文案键**全部派生自该生成物**。生成物进版本库，并由 `check-menu.mjs` 做陈旧性校验（重新生成 → 逐字节比对 → 不一致即失败，报出差异项）。

**理由**：

1. 权威处**不能动**：`MENU_TREE` 已经是角色配置页勾选树的来源，也已经是 `user.menus` 的产出源。把权威迁到前端或第三个文件，等于新造一处需要与两者同步的真相源——与 FR-N20 的目的相反。
2. 运行时下发（前端启动时向后端要菜单树）**被接口契约否决**：`/api/v1/roles/menu-tree` 的闸门是 `@RequirePermission("role:manage")`。用它做渲染要么放宽该端点（把角色配置的读取权放给所有登录用户），要么新增端点——前者是**安全回退**，后者是**新增契约**。规格假设 2 与原则一都不允许，且总收益（省掉一次代码生成）远小于代价。
3. 构建期生成是**本仓库已经用熟的判据形态**：spotless 的「重新格式化 → 比对 → 不一致即失败」就是同一机制，开发者对「生成物陈旧 ⇒ 构建红 ⇒ 跑一次生成命令」的反馈回路无需重新学习。
4. 生成物**进版本库**而非构建时即席生成：代码评审能看到「这次改了哪些分组与名称」，diff 本身就是 FR-N08/N09/N10 的证据；即席生成会让这类改动潜行。

**被否决的替代方案**：

| 方案 | 否决理由 |
|---|---|
| 运行时由后端下发菜单树 | 见理由 2：需放宽 `role:manage` 端点或新增端点，属安全回退 / 契约变更。另需处理加载期空菜单的闪烁与失败降级，复杂度更高 |
| 保留两份手写清单，只加机械校验 | 直接违反 FR-N20（「不得手写第二份清单」）；且校验只能事后发现不一致，改一个菜单项仍需改两处——SC-N06 的「只改一处」无法满足 |
| 把菜单定义抽到一个中立的 JSON/YAML，两侧都读它 | 权威处从「后端常量」变成「第三个文件」，后端反而要读前端工程的文件（或反向）；且 `MENU_TREE` 是 Java 常量、被 `PermissionDictionaryTestSupport` 等测试依赖，迁移面大而收益仅为「更对称」 |
| 生成物不进版本库、构建时生成 | 分组/名称的改动不再有可评审的 diff；CI 与本地构建都要先跑生成器，失败模式从「陈旧」变成「顺序依赖」 |

---

## 决策 ②：机械校验挂载点 = **两处，各管一半**

**决策**：

- **语义维度**（项、分组、名称三维互含；授权 ⇒ 读码）→ **后端 surefire 测试**：
  - 扩展 `backend/src/test/java/com/crm/security/MenuRouteAlignmentTest.java`（既有文件，既有的仓库根解析与「输入非空」防呆）
  - 新增 `backend/src/test/java/com/crm/security/MenuAccessGrantAlignmentTest.java`
- **陈旧性维度**（生成物是否与权威处一致）→ **前端 Node 脚本** `frontend/scripts/check-menu.mjs`，以 `pnpm menu:check` 暴露，并在 `.github/workflows/ci.yml` 的前端作业里与 `i18n:check` 并列成一步。

**理由**：

1. 语义校验**必须**在后端做：它要读 `RoleConstants.java`（Java 源）与 `App.tsx`（前端源）。既有 `MenuRouteAlignmentTest` 正是为此存在，其 javadoc 已写明理由——前端做不了（决策前提表最后两行），而「前端是否与权威一致」在后端校验最自然。
2. 陈旧性校验**必须**在前端做：生成器 `gen-menu.mjs` 就在前端工程里。若在后端再实现一遍「按同样的规则解析 Java 常量并预期同样的产物」，就会出现两份生成逻辑——正是本规格要消灭的重复形态。让后端去 spawn node 更糟（构建不再自包含）。
3. 这个分工**沿用仓库已有的成文约定**：`menuKeys.test.ts` 的 javadoc 明确写了「两个测试各管一半、且都必须存在」；`check-i18n.mjs` 与 `MenuRouteAlignmentTest` 用同一套正则解析 `App.tsx` 也是同一条约定。新护栏不发明新惯例。
4. 两道护栏都**不需要网络与数据库**：前者是文件解析，后者是字符串比对。这才满足 FR-N23「纳入常规构建入口」——需要起 MySQL/Redis 的校验没法成为提交前门禁。

**被否决的替代方案**：

| 方案 | 否决理由 |
|---|---|
| 全部塞进一个后端测试（含生成物陈旧性） | 后端要复刻 JS 生成逻辑，两份生成规则必然漂移；或 spawn node，破坏构建自包含 |
| 全部塞进一个前端 Node 脚本 | 要复刻 `MENU_TREE` 的 Java 解析与 `PermissionDictionaryTestSupport` 的展平逻辑，且放弃既有的后端护栏与「输入非空」防呆 |
| 写成 vitest 测试 | 前端测试跑在 jsdom 下、无 `@types/node`，读文件需新增依赖（既有结论明确否决） |
| 写成 Playwright e2e 断言 | e2e 作业需真实后端与数据库，属「极少量端到端测试」（原则四的测试金字塔）；且 e2e 只能覆盖被点名的 5 个角色，无法覆盖全部 10 个 |

---

## 决策 ③：「已授 ⇒ 打得开」的可重复形态 = **读源码的静态断言，驱动修复**

**决策**：新增 `MenuAccessGrantAlignmentTest`，输入三份，断言两组：

输入：
- **A. 权威菜单树**：`PermissionDictionaryTestSupport.menuKeys()`（既有）+ 新的分组/名称解析
- **B. 菜单项 → 读码对照表**：新增 `MenuReadPermissionTestSupport`，形式为 `菜单键 → 所需读码集合`（例：`custom-objects → {custom_object:read}`；对「登录即可、无码」的项显式标记为 `OPEN`）
- **C. 预置角色授权**：从 `V46/V75/V80/V81/V82/V83/V84` 等迁移文件中解析 `role_menu` / `role_permission` 的授予

断言：
- **C1（对全部角色，不只点名的 5 个）**：对任意角色 R、任意 `(R, key) ∈ role_menu`，若 B 中 `key` 需要读码，则 `role_permission` 必须含 `(R, 该码)`。违例即失败并列出「角色 / 菜单项 / 缺失的码 / 所在迁移」——这正是 FR-N06 的通用形态，覆盖 SC-N02、SC-N04、SC-N08。
- **C2（对照表自身的体检）**：B 中每一项的码都必须**真的**被某个端点的 `@RequirePermission` / `@PreAuthorize` 校验（扫 Controller 注解）。这同时做两件事：防止 B 指向一个不存在的码（如当前缺失的 `custom_object:read`）；检出「字典里有、无人引用」的死码。
- **C3（完整性）**：A 中每个菜单键要么在 B 中有条目、要么被显式标记 `OPEN`——新增菜单项时被迫做出决定，不能被默默漏掉。
- **C4（死授权 = FR-N07）**：`role_menu` 里不得存在不在 A 中的 `menu_key`。这条断言的修复动作就是删除死授权，且删除**不改变任何角色的可见集合**——死授权本就翻译不出可渲染项（FR-N07 的后半句由此自动成立，无需额外证明）。

**理由**：

1. **它把「逐项人工核对」变成「一道会自己变红的断言」**。FR-N06 要求「不得以看起来没问题作结」——静态断言恰好不能容忍这一点：漏掉任一项，C3 变红；授了菜单没给码，C1 变红；码不存在或无人校验，C2 变红。
2. **它直接驱动 FR-N24 的修复**：一旦引入，`custom-objects` 项会立刻因「`custom_object:read` 不存在 / ANALYST 不持有」而变红，逼迫实施者补齐读码 + 授予 + 撤掉 `CustomObjectController` 的类级 ADMIN 门。规格要的「补齐权限码」由此从人工判断变成构建结果。
3. **不需要数据库**：授权从迁移 SQL 读，而迁移是授权的**权威来源**（预置角色的授权只存在于迁移里）。避免依赖 H2 测试库补齐 V75 镜像（那是 083 的范围，见 plan 的 D3）。
4. **解析须「遇到不认识的形态就报错」，而不是跳过**。迁移的授予语句高度规整（统一样式 + 一处 `JOIN ... UNION SELECT` 多码形态），但为了不出现「漏解析一部分 → 断言静默通过」，解析器必须**白名单式**工作：命中已知形态才计入，遇到无法识别的 `INSERT INTO role_menu/role_permission` 形态立即失败并打印该语句。这沿用了 `MenuRouteAlignmentTest` 的「先证明输入非空」防呆精神（那里是断言「>50 个菜单键」，这里是断言「解析到的授予语句数与文件里出现的语句数相等」）。

**被否决的替代方案**：

| 方案 | 否决理由 |
|---|---|
| 起 Spring 上下文、逐角色登录后真实调用各模块读接口断言非 403 | 最真实，但需要预置角色存在于测试库（依赖 083 补齐 V75 的 H2 镜像），且 10 角色 × 数十菜单的 HTTP 调用为「极少量端到端测试」原则所不容；留给 quickstart 的手工验收场景做一次 |
| 只人工核对一次并写进验收记录 | 直接违反 FR-N06 的最后一句与 FR-N23；下次加菜单项时不会有人想起这张表 |
| 断言写死「7 项断链修好即可」 | 只能覆盖已发现的缺陷，无法覆盖 SC-N08（全部 10 个角色逐一核对），也无法防止回归 |
| 让权限码校验由运行时的 `PermissionAspect` 兜底 | 运行时不报错、只返回 403——正是本规格要消除的形态（菜单指向拒绝页而没有构建期信号） |

---

## 决策 ④（连带）：名称权威的落地 = **权威处给中文名，文案键走「派生规则 + 例外表」**

**决策**：
- `MENU_TREE` 的 `title` 是中文名的**唯一作者**；`zh-CN.ts` 的 `menu.*` 文案**必须与之逐字相等**（由扩展后的 `MenuRouteAlignmentTest` 断言），`en.ts` 继续由 `check-i18n.mjs` 保证键对齐。
- **文案键的解析规则只有一份实现**，放在 `frontend/scripts/gen-menu.mjs` 里，并写进生成物：
  - 菜单项：默认 `camelCase(menuKey 的最后一段)`（实测绝大多数既有键已符合，如 `/sla-policies → slaPolicies`、`/stats/leaderboard → leaderboard`、`/settings/custom-fields → customFields`），**7 个项目走显式例外**：`stats→home`、`customer-merge→merge`、`contract-renewal→renewal`、`marketing→marketingActivity`、`marketing/email→emailMarketing`、`email-unsubscribes→unsubscribe`、`exports/scheduled→scheduledExports`
  - 分组：11 条显式映射（分组标题是中文、键是英文，无从派生），以**分组标题**为键；权威处改了组名而例外表没跟 → 护栏报「未找到分组 X 的文案键」而非静默用错
- `App.tsx` 里那张 58 条的 `MENU_I18N_KEYS` 手写表**删除**——它正是「手写第二份清单」的形态（键是菜单路径，等于第二份菜单项集合）。留下的只有 7 条项例外 + 11 条组映射，净减约 40 条手工条目，且例外表**不是**菜单定义（它不决定有哪些项、属于哪组、叫什么名字）。
- 后端护栏**不重新实现**该派生规则：它读生成物里的 `i18nKey` 字段。规则的唯一实现留在 JS 侧，其正确性由 `menu:check`（陈旧性）+ 后端护栏（`i18nKey` 必须在两种语言里都存在、且 `zh-CN` 的值 == 权威处 `title`）双向钉住。这避免了「同一个派生规则两份实现」——与决策 ② 里否决「后端复刻生成逻辑」是同一条理由。

**理由**：FR-N08/N09 要求「以配置侧功能名称为准」且两侧逐字相同。若把中文名直接烧进生成物，`zh-CN` 的 `menu.*` 键会变成死键、`en` 的译文失去锚点（双语表是同一命名空间）；保留文案键则既满足「名称只有一个作者」，又让 `en` 有地方放译名。规格边界情况里「缺键时必须降级显示权威名称」也随之有据可依（降级目标就是权威 title 本身）。

**被否决的方案**：

| 方案 | 否决理由 |
|---|---|
| 生成物直接内嵌中文名 | `zh-CN` 的菜单键全变死键、`en` 译文失去锚点；双语表是同一命名空间，拆掉一半会让 `check-i18n.mjs` 的双向对齐失去意义 |
| **让 `MENU_TREE` 的 `item()` 直接携带 i18n 键**（一度倾向此方案） | **决定性理由**：`MENU_TREE` 就是 `/api/v1/roles/menu-tree` 的**响应体**（`RoleService.menuTree()` 直接返回它）。给每个 item 加字段 = 改变该端点的响应结构，会引入**第三处**与规格假设 2 的偏差并触发一次契约变更——为省掉 18 条手工例外不值得。次要理由：58 行 `item(...)` 全都要动，改动面远大于例外表 |
| 让 i18n 键改名为「完全由 menuKey 派生」，消灭全部例外 | 要改动约 9 组既有键（含 `menu.home` 这类被其他页面引用的键），是一次与规格目标无关的大范围改名；而例外表只有 7 条且有护栏兜底 |
| 保留 `App.tsx` 里的 58 条 `MENU_I18N_KEYS` 表原样 | 它的键就是菜单路径，等价于一份手写的菜单项集合，正面违反 FR-N20；且新增菜单项时它必须被再次手改（SC-N06 的「只改一处」无法满足） |


---

## 决策 ⑤（连带）：`App.tsx` 里刻意保留的本地映射，以及随之清理的死代码

**决策**：
- 保留两处前端本地映射：**分组 id → antd 图标**（图标是 JSX，无法进入生成物；且不属于 FR-N21 的项/分组/名称三维度）；**两个「借分组显示」的子页面** `/marketing/roi`、`/workflows/logs`（它们本就不是菜单项，靠 `COARSE_ALIASES` 归属到 `marketing`/`workflows`）。后者由护栏断言**恰好只有这两条**，不允许增长（对应规格边界情况）。
- 清理 `planned` 占位机制（`grep "planned: true"` 零命中，已是死代码）与死文案键 `menu.board`。**`menu.board` 的清除属 FR-N12 的直接要求；`planned` 的清除是为了不让重写后的路由构造里留着一条永不成立的分支**——两者都在此显式登记，以免被当作范围外的多余改动。

**理由**：重写 `menuRoutes` / `groupedMenuItems` 的构造是本次不可避免的改动；留着 `'planned' in r && r.planned` 这类分支会让「菜单项集合」多出一条隐身来源。显式登记而非顺手删，是为了让范围可被评审。

---

## 遗留与交接

- **D1/D2/D3（见 plan.md「与规格假设的偏差」）需批准**：新增读码与 `V85` 数据迁移、最小契约文档、以及「不修复 `schema-h2.sql` 的 V70–V77 存量缺口（属 083）」这条边界。FR-N24 的批准人/日期须在实施前填入 `plan.md` 与 `spec.md`。
- **SC-N06（单一真相源实验）**：以「新增一个菜单项只改权威处一处」的实验证明，而非以设计论证代替。实验步骤写进 `quickstart.md`，结论须回填规格。
- **10 个预置角色的逐一核对**：由决策 ③ 的 C1 覆盖（通用断言），不再是人工清单；quickstart 里保留一次针对 ANALYST 的手工端到端确认（真实点开页面不 403），作为对静态断言的独立校验。
- **不涉及**：权限矩阵重审（规格已排除）、`schema-h2.sql` 的 V70–V77 补齐（083 T072）、行级数据权限。
