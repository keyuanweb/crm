# Phase 1 Data Model: 菜单信息架构与授权可见性收口

**Date**: 2026-09-12 | **Plan**: [plan.md](./plan.md) | **Research**: [research.md](./research.md)

本规格**不改任何表结构**（无 DDL）。因此本文件描述的不是建表脚本，而是三样东西：
① 菜单领域的概念模型与其权威归属；② 唯一一处**数据变更**（新读码的授予与死授权清理）；
③ 必须由护栏断言的不变量。审查重点应放在 ③ —— 不变量才是本次交付的实质。

---

## 一、概念模型

### 1. 菜单项（Menu Item）—— 权威定义在 `RoleConstants.MENU_TREE`

| 属性 | 说明 | 权威来源 |
|---|---|---|
| `menuKey` | 稳定标识（如 `custom-objects`、`stats/leaderboard`），是 `role_menu.menu_key` 的唯一取值域 | `MENU_TREE` |
| `groupKey` | 所属分组（如 `data`、`audit`） | `MENU_TREE`（本次按 FR-N13–N18 调整若干项） |
| `title` | **功能名称**（中文），两侧显示名的唯一作者 | `MENU_TREE` |
| `order` | 组内显示顺序 = `MENU_TREE` 中的书写顺序 | `MENU_TREE` |
| `path` | 页面地址；默认约定 `path == "/" + menuKey` | 前端生成物 |
| `i18nKey` | `menu.*` 文案键（中文由权威处对齐，英文由 `en.ts` 提供） | 前端生成物（键名派生规则含少量例外，见下） |
| `icon` | antd 图标元素 | **前端本地**（JSX，刻意不进生成物） |
| 是否需要读码 | 「打得开」所需的权限码集合，或显式 `OPEN`（登录即可） | 测试侧对照表（`MenuReadPermissionTestSupport`） |

**例外与边界（护栏会钉住，不允许增长）**：

- 三个路径别名：`/customers/at-risk → at-risk`、`/marketing/roi → marketing`、`/workflows/logs → workflows`（`COARSE_ALIASES`，既有）
- 两个「借分组显示」的子页面：`/marketing/roi`、`/workflows/logs`（不是菜单项，不参与授权，靠别名随所属分组出现）
- 首页项 `stats` 在前端被置顶为独立项（不进分组渲染），但它在权威处仍属「首页」分组

### 2. 菜单分组（Menu Group）—— 权威定义在 `RoleConstants.MENU_TREE`

| 属性 | 说明 |
|---|---|
| `groupKey` | 稳定标识（`home`/`customer`/`sales`/`deal`/`marketing`/`service`/`workbench`/`data`/`admin`/`config`/`audit`） |
| `title` | 分组名称，两侧显示名的唯一作者（本次统一 4 处组名，FR-N09） |
| 成员集合 | 由各菜单项的 `groupKey` 决定（FR-N10：组内成员集合两侧相同） |
| 空分组 | **不渲染**（既有行为，须保持） |

### 3. 角色菜单授权（`role_menu`）—— 既有表，本次不改结构

| 列 | 说明 |
|---|---|
| `role_id` | 角色 |
| `menu_key` | 菜单项标识 |

**关键性质**：**不含分组**。因此归属搬移（FR-N13–N18）与名称统一（FR-N08/N09）**零数据迁移**，FR-N19「归属调整不改变任何角色的可见集合」由结构本身保证——授权集合是按 `menu_key` 存的，搬分组不触及它。

### 4. 权限码（`role_permission` + `RoleConstants.PERMISSION_DEFS`）—— 既有

- `PERMISSION_DEFS` 是码的**字典**（角色配置页据此渲染勾选项）；`role_permission` 是**授予**。
- 与 `role_menu` **相互独立**：前者决定「打得开」，后者决定「看得见」。
- 本次唯一改动：字典中**新增** `custom_object:read`（此前只有 create/update/delete，见 plan 的 D1）。

### 5. 读码对照表（测试输入，新增）

`backend/src/test/java/com/crm/support/MenuReadPermissionTestSupport.java`，形如：

```text
菜单键                 所需读码集合                    备注
stats                  OPEN                            登录即可
customers              {customer:read}
custom-objects         {custom_object:read}            本次新增的码
...
```

`OPEN` 是显式声明而非缺省：它表示「这个页面不需要任何码」，必须逐项写出来（决策 ③ 的 C3 保证没有第三个状态「忘了写」）。

### 6. 生成物（`frontend/src/constants/menuManifest.ts`，新增）

从权威处生成的只读清单，字段与 §1/§2 的子集一致：有序分组 → `{groupKey, groupI18nKey}`，分组内有序菜单项 → `{menuKey, path, i18nKey}`。**不含**图标与路由元素（前端关注点）。文件头标注「由 `scripts/gen-menu.mjs` 生成，请勿手改」并注明重生成命令。

---

## 二、数据变更（本次唯一一处）

**新增 `V85__menu_ia_and_custom_object_read.sql`**，内容仅两类：

1. **授予**：`custom_object:read` → ADMIN、ANALYST（形态与 V84 §3 的 `custom_field:read` 完全一致，含同样的「为什么是这些角色」的注释）
2. **清理**：删除 `role_menu` 中指向不存在菜单项的死授权（FR-N07）。**当前是否仍有存量死授权尚未逐一枚举**——由决策 ③ 的 C4 断言检出后按检出结果清理；若检出为空，本迁移不含 DELETE 语句，并在注释里记明「C4 断言当时为零」。

镜像：`backend/src/test/resources/schema-h2.sql` 只补本迁移的授予（既有规则「新增迁移必镜像」）。**不**顺手补 V70–V77 的存量缺口——那是 083 的 B 块/T072（见 plan 的 D3）。

> 说明：`role_menu`/`role_permission` 是**数据**，本规格的「无数据库结构变更」成立；但按规格假设 1 的例外条款，新增迁移这件事必须显式说明——已记于 plan 的「与规格假设的偏差」D1，待批准。

---

## 三、不变量（本次交付的实质）

| # | 不变量 | 断言处 | 对应需求 |
|---|---|---|---|
| **I1** | 可见集合 = 授权集合（对全部预置角色，不多不少） | 前端不再有角色名硬门 + 决策 ③ 的 C1 | FR-N01/N02/N03、SC-N01/N02 |
| **I2** | 内置管理员的可见集合不受本改动影响（兜底仍在，且不与新规则冲突） | 保留 ADMIN 走「全量」分支的既有行为，且该分支**不再是**整组硬门的替代品 | FR-N04、规格边界情况「内置管理员的兜底」 |
| **I3** | 名称单一作者：配置侧标题 = 使用侧中文名（逐字） | 扩展后的 `MenuRouteAlignmentTest` | FR-N08/N09、SC-N03 |
| **I4** | 分组归属单一作者：生成物中的分组与成员集合 = 权威处 | 同上（分组维度）+ `menu:check`（陈旧性） | FR-N10、SC-N03 |
| **I5** | 已授 ⇒ 有码（对任意角色），且对照表里的码都真的被端点校验 | 决策 ③ 的 C1/C2/C3 | FR-N06、SC-N04、SC-N08 |
| **I6** | 授权数据中无指向不存在菜单项的死授权 | 决策 ③ 的 C4 | FR-N07 |
| **I7** | 单一真相源：新增一个菜单项只改权威处一处，两侧同时正确 | `gen-menu.mjs` + `menu:check` + quickstart 的实验 | FR-N20、SC-N06 |
| **I8** | 服务端授权不被削弱：菜单可见性只影响导航 | 本规格**不修改**任何服务端校验的强度；撤掉的是 UI 侧硬门，撤门处都有权限码闸门把守（I5 证明） | FR-N05、原则三 |

---

## 四、生命周期：新增一个菜单项要走几步（I7 的操作形态）

```text
1. 在 RoleConstants.MENU_TREE 的对应分组里加一行            ← 唯一的手改处
2. 在 App.tsx 补路由（path/icon/element）与 MENU_I18N_KEYS 的文案键   ← 页面实现本身，非「菜单定义」
3. 在前端跑 pnpm menu:check（生成物陈旧 → 报红 → 按提示重生成）
4. 在测试侧对照表里为该菜单键填读码或 OPEN（漏填 → C3 报红）
5. （若要给角色）在迁移里授予 role_menu，检查 C1 是否要求同时补码
```

第 2 步不是「菜单定义的第二个真相源」：它描述的是**页面与路由的存在性**，而非菜单项的分组/名称/顺序——后三者只在第 1 步。这个区分是 I7 能成立的关键，也是评审时应重点确认的一点。
