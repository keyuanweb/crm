# 实施计划：接线码的渲染层用例补课（087）

## 摘要

为 086 盘点出的 **18 个「已接线但零本页渲染用例」的权限码**补齐双向渲染用例（≥36 条），
分布在校验它们的 **11 个页面**上。**零生产代码改动**——只新增 `*.perm.test.tsx`（其中
`ContractDetailPage.perm.test.tsx` 与 `LeadListPage.perm.test.tsx` 已存在，本规格向它们**追加**）。

> 原定 15 个码 / 8 个页面；收尾时按正确口径重扫全仓，另有 4 个站点 / 3 个码被 086 的旧口径误判为已覆盖，
> 按同类扩入（详见下文「调研结论」的**第二遍**）。

## 技术上下文

- **测试栈**：vitest + @testing-library/react（既有，086 已用 39 个文件验证过形制）。
- **渲染辅助**：`src/test/renderWithProviders.tsx`——`ConfigProvider(zhCN)` + antd `App` +
  `QueryClient({retry:false})` + `MemoryRouter(initialEntries)`。
  **`useParams` 页面必须**包在 `<Routes><Route path="/x/:id" element={...}/></Routes>` 里，
  仅靠 `MemoryRouter` 的 `initialEntries` **不提供 params**（086 踩过）。
- **i18n**：`src/test/setup.ts` mock 了 `react-i18next`（`t(key) => key`），
  **缺键时抛错**——所以按钮名在断言里就是 i18n **键字符串**，用 `/pages\.x\.btnY/` 之类正则查询。
- **权限语义**（`hooks/usePermission.ts`）：`hasPerm(code, user)` 在 `user.role === 'ADMIN'` 时
  **短路为 true**；否则等价于 `(user.permissions ?? []).includes(code)`。
  ⇒ **负向用例只能用非 ADMIN 用户**，否则永远拿不到"看不见"的结论。
  ⇒ 但 ADMIN 直通语义**必须保留**（`CustomerListPage.test.tsx` 依赖它找「新建客户」）。

## 章程检查

- **原则一（不得静默）**：本规格不改变任何对外行为，只增加证据。
- **零生产代码改动**是硬约束（FR-005）——它同时是"这确实是纯补课"的可机器校验的证明
  （`git diff --stat` 里出现任何 `pages/**/*.tsx` 非测试文件即违规）。
- 不产 `contracts/`：无端点语义变更。

## 调研结论

**范围是机械推导出来的，不是估的，而且推了两遍。**

**第一遍（规格起草时，口径借自 086 T103）**：对 15 个码逐个 grep `PERMS.<key>`（排除 `permissions.ts`
与测试文件）得到接线点；再 grep 测试文件，得到用例数——**15 个码的用例数全部为 0**。

**一处口径订正（已写入 `086/tasks.md` T103，此处复述以免复发）**：
"码字面量出现在测试里" **≠** "有正向用例"。`campaign:delete` 的字面量确实出现在
`LandingPageListPage.perm.test.tsx:104`，但那是**负向夹具**（断言某角色不该因此获得能力），
它真正的判据落点 `CampaignListPage.tsx:200` 从未被执行。若按"字面量出现"计数，本规格会漏掉它。

**第二遍（执行收尾时，改用正确口径重扫全仓）**：口径改为
「该码的判据落点所在页面，是否有用例**引用该码**（`PERMS.<key>` 或码字符串）」，对**所有**含 `PERMS.` 的
页面 × 码全部筛一遍。结果：

```
累及 (页面,码) 判据点总数: 68   其中无本页用例的: 4
  A 类（本页有 perm.test.tsx 但漏了该码）：CampaignListPage ← campaign:delete
  B 类（整页无 perm.test.tsx）        ：EmailUnsubscribePage ← email:manage
                                        EmailCampaignPage    ← email:manage
                                        ScheduledExportExecutionHistoryPage ← export:scheduled
```

⇒ **`campaign:delete` 不是孤例，是"字面量坐在别页负向夹具里"这一类的一个样本**；086 T103 只点出了它一个，
没把这一类扫完。**范围由 15 个码订正为 18 个码**，4 个新站点如下表末 4 行。

> 这条订正本身值得留在文档里：**一个口径只在自己被用来"排除"时才会暴露缺陷**。
> 086 用它在 63 个码里划出 15 个缺口（划出来的那 15 个是真的），**没被划出来的是假的**——
> 而"没被划出来"这件事，只有当有人按正确口径重扫一遍时才会现形。

## 实现策略

单阶段、按页面并行（页面之间零依赖）。每个码两条用例，同一页面内多码时**必须互相隔离**。

### 判据落点映射（T001 逐页核对所得，行号为 2026-09-13 实测）

| 码 | 页面 | 控件 | 判据行 | 非显然点 |
|---|---|---|---|---|
| `customer:create` | `customers/CustomerListPage` | 工具栏「新建」 | `:389` | |
| `customer:delete` | 同上 | 行内「删除」 | `:279` | |
| `customer:import` | 同上 | 工具栏「导入」**+「下载模板」** | `:352` | **一码管两个控件**，须同进同出 |
| `customer:claim` | 同上 | 行内「认领」 | `:269` | ⚠️ **仅池视图**（`view === 'pool'`） |
| `customer:pool_manage` | 同上 | **行选择 + 「池扫描」+「批量转移」** | `:346` `:365` `:374` | ⚠️ **一码管三处** |
| `contract:approve` | `contracts/ContractDetailPage` | 「审批」「驳回」 | `:266` | ∧ `status === 'PENDING_APPROVAL'` |
| `quote:approve` | `quotes/QuoteDetailPage` | 审批动作 | `:148` | ∧ `status === 'PENDING_APPROVAL'` |
| `lead:assign` | `leads/LeadListPage` | 行内「分配给我」 | `:264` | ∧ `ownerId != null && ownerId !== user.id` |
| `order:delete` | `orders/OrderListPage` | 行内「删除」 | `:211` | |
| `product:create` | `products/ProductListPage` | 工具栏「新建」 | `:232` | |
| `product:update` | 同上 | 行内「编辑」 | `:191` | |
| `product:delete` | 同上 | 行内「删除」 | `:196` | |
| `campaign:create` | `marketing/CampaignListPage` | 工具栏「新建」 | `:239` | |
| `campaign:update` | 同上 | 行内「开始」/「结束」/「编辑」 | `:185` `:190` `:195` | ⚠️ **∧ 状态**：开始仅 `PLANNING`、结束仅 `RUNNING`、编辑非 `ENDED` |
| `role:manage` | `roles/RoleListPage` | 行内「编辑/删除」**+ 工具栏「新建」** | `:199` `:263` | **一码管两处** |
| `campaign:delete` | `marketing/CampaignListPage` | 行内「删除」 | `:200` | 范围订正扩入（原被误判为已覆盖） |
| `email:manage` | `email/EmailUnsubscribePage` | 「恢复」 | `:41` | 范围订正扩入；**同码在 `EmailTemplatePage` 另有落点，已在册** |
| `email:manage` | `marketing/EmailCampaignPage` | 「测试发送」 | `:183` | 同上；**与上一行是两个独立站点，不可互推** |
| `export:scheduled` | `exports/ScheduledExportExecutionHistoryPage` | 「立即执行」 | `:104` | 范围订正扩入；**同码在 `ScheduledExportListPage` 的落点已覆盖，勿重复** |

> ⚠️ 标记者是本规格最容易写错的地方：**把状态判据的差异误读成权限判据的差异**
> （例：`campaign:update` 下「开始」在 PLANNING 行有、在 RUNNING 行没有——那是状态不是权限）。
> 用例必须**固定一个姿态的行**，只让权限变量动。

### 测试写法（照 086 的既有形制）

1. **双向成对**：同一条用例文件内，用 `role: 'SALES'`、`permissions: [目标码]` 与 `permissions: []`
   两个用户渲染同一页面，断言同一控件的在/不在。
2. **锚点防真空**：断言里必须含一个**恒存在**的元素（页面标题/工具栏容器），
   否则"渲染失败"会被误判成"按钮正确地不在"。
3. **互不串门**：同页多码时，授 A 的用例里必须同时断言 B 的控件**不在**
   （`ProductListPage` 的 create/update/delete、`CampaignListPage` 的 create/update 是重点）。
4. `getByText` 在**多元素匹配时抛错**——用 `queryAllByText(...).length`、`within(row)` 或 `closest('tr')`。
5. **变异自验**（FR-003，逐条留痕）：把判据改成恒真 ⇒ 负向用例**必红**；还原用**唯一探针标记行**替换，
   **禁止 `replace_all`** 作用于短常见字面量。

## 风险与缓解

| 风险 | 缓解 |
|---|---|
| **假绿：用例写了但什么都没证明**（本规格首要风险） | 双向成对（SC-001）+ 逐条变异自验（SC-002）+ 锚点防真空（FR-004） |
| **用 ADMIN 跑负向用例 ⇒ 断言恒真** | FR-002 明令；086 的既有用例作为反例模板 |
| **把状态判据误当权限判据**（campaign/contract/quote 三处） | 映射表已标注；用例固定行姿态，只动权限变量 |
| **同页多码串门** | FR-004 第 3 条：授 A 时必须断言 B 不在 |
| **`replace_all` 还原探针误伤无关代码** | FR-003 明令禁止；2026-09-13 有前例（6 处无关代码被改，类型检查沉默） |
| **并行写测试时的瞬时 TS 报错被误判为缺陷** | 端口/构建干扰与瞬时 `TS6133` 属工序噪声；先看 mtime 再采信（见 086 T100） |

## 验证

```bash
cd frontend
npx vitest run $(本规格涉及的 *.perm.test.tsx 列表)      # 定向：≥36 条新用例全绿
pnpm typecheck && pnpm lint && pnpm i18n:check && pnpm menu:check && pnpm perms:check && pnpm test:coverage
grep -rn "MUTATION-PROBE" src/                          # 必须为空
git diff --stat -- 'frontend/src/**/*.tsx' | grep -v "\.perm\.test\.tsx"   # 必须为空（零生产代码改动）
```

后端 `FrontendPermissionCodeAlignmentTest` 预期自然保持绿（本规格不动 `permissions.ts`）。

## 不覆盖的范围

- **不补 086 已覆盖的 46 个码**（那半边有 146 条用例）。
- **不新增渲染层用例之外的测试类型**（不做 e2e、不做后端 IT）——
  e2e 只自起前端，证明不了权限行为；后端 IT 全用管理员令牌，而管理员在切面直通。
- **不改任何生产代码**，包括不"顺手"修本规格途中看到的问题——发现的问题记入 `tasks.md` 的
  「附带发现」，单独立项。
