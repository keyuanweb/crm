# 任务清单：接线码的渲染层用例补课（087）

**输入**: `spec.md`（FR-001~007、SC-001~005）、`plan.md`（判据落点映射表）
**前置**: 086 已闭合（`b90b79f`）。本规格**不修改** 086 的任何产物。

## 格式：`[ID] [P?] [Story] 说明`

- `[P]` = 可并行（与同批次其他任务无文件交集）
- 路径均相对 `frontend/src/`
- 每个测试任务必须自带：双向用例 + 变异自验 + 探针残留自检

## 路径约定

- **测试文件**：与页面同目录、`<PageName>.perm.test.tsx`
- **唯一例外**：`pages/contracts/ContractDetailPage.perm.test.tsx` **已存在**（086 为 `contract:update` 而写），
  本规格**追加** `contract:approve` 的用例，**不新建文件**

---

## 准备

- [x] **T001** 逐页核对 15 个码的判据落点与行号，产出映射表（见 `plan.md`）
- [x] **T002** 核对 15 个码的现有用例数（**结论：全部为 0**）
- [x] **T003** 确立计数口径为「有**正向**用例」而非「码字面量出现在测试里」（见 `plan.md`）
- [x] **T004** 按正确口径重扫全仓，订正范围为 15 → 18 个码（见 `plan.md` §调研结论「第二遍」）
      —— 新增 `campaign:delete` / `email:manage` / `export:scheduled`，共 4 个判据点

## 测试（按页面并行，11 个页面 / 18 个码）

> 每个任务两条硬要求：**双向成对**（有码 ⇒ 在；无码 ⇒ 不在）+ **变异自验**（判据恒真后负向用例必红）。

- [x] **[P] T010** `CustomerListPage.perm.test.tsx` —— **5 个码**（本规格最重的一个）· 交付 **13 条 / 9 个变异探针**
      `customer:create`（工具栏新建）、`customer:delete`（行内删除）、
      `customer:import`（工具栏导入 **+ 下载模板**，一码两控件）、
      `customer:claim`（行内认领，⚠️ **仅池视图**）、
      `customer:pool_manage`（⚠️ **行选择 + 池扫描 + 批量转移，一码三处**）
      **额外要求**：非池视图下「认领」不存在的断言必须**与权限无关**地成立——
      即"授了 `customer:claim` 但不在池视图"时它仍不出现，用这条把视图判据与权限判据区分开。

- [x] **[P] T011** `ProductListPage.perm.test.tsx` —— **3 个码** · 交付 **7 条 / 3 个变异探针**
      `product:create`（工具栏新建）、`product:update`（行内编辑）、`product:delete`（行内删除）
      **额外要求**：三码互不串门——只授 `product:update` 时必须断言「新建」「删除」**均不在**
      （授予范围本就不同：create/delete 仅 ADMIN+MARKETING_MANAGER，update 另有销售角色）。

- [x] **[P] T012** `CampaignListPage.perm.test.tsx` —— **2 个码** · 交付 **5 条 / 3 个变异探针**
      ⚠️ 同文件另有 T018（`campaign:delete`）追加，两者**同一文件，须串行**，不可并行
      `campaign:create`（工具栏新建）、`campaign:update`（行内开始/结束/编辑）
      **额外要求**：`campaign:update` 的三个动作各带**状态判据**（开始仅 `PLANNING`、结束仅 `RUNNING`、
      编辑非 `ENDED`）——用例必须**固定行姿态**，只让权限变量动，否则会把状态差异误读成权限差异。

- [x] **[P] T013** `QuoteDetailPage.perm.test.tsx` —— **1 个码** · 交付 **4 条 / 2 个变异探针**
      `quote:approve`（∧ `status === 'PENDING_APPROVAL'`）
      **额外要求**：需覆盖"有码但状态不对 ⇒ 仍不可见"，把权限判据与状态判据分开证明。

- [x] **[P] T014** `ContractDetailPage.perm.test.tsx` —— **1 个码，追加进既有文件** · 交付 **+4 条 / 1 个变异探针**，
      既有 4 条**逐字未动**（`git diff --numstat` = 81 insertions / 0 deletions）
      `contract:approve`（∧ `status === 'PENDING_APPROVAL'`）
      **额外要求**：该文件已有 `contract:update` 的 4 条用例（086 T081），
      **不得改动它们**；新用例与既有用例共用 helper 时须先读清现有的 `renderPage` 形制再动手。

- [x] **[P] T015** `LeadListPage.perm.test.tsx` —— **1 个码，追加进既有文件** · 交付 **+5 条 / 2 个变异探针**，
      既有 3 条逐字未动
      **口径订正**：本文件与 `ContractDetailPage.perm.test.tsx` 同属**已存在**（086 为 `lead:delete`/`contract:update` 而写），
      本规格**追加**而非新建——原任务书只写了文件名、"新建"是遗漏的默认值
      `lead:assign`（行内「分配给我」）
      **额外要求**：该控件另受**归属判据**约束（`ownerId != null && ownerId !== user.id`）——
      与权限判据是 ∧。用例须造一行 `ownerId` 不等于当前用户的数据，否则控件本就不可见，测不出权限。

- [x] **[P] T016** `OrderListPage.perm.test.tsx` —— **1 个码** · 交付 **3 条 / 1 个变异探针**
      `order:delete`（行内删除）

- [x] **[P] T017** `RoleListPage.perm.test.tsx` —— **1 个码** · 交付 **4 条 / 2 个变异探针**

## 测试（范围订正扩入的 4 个判据点，3 个码）

- [ ] **T018** `CampaignListPage.perm.test.tsx` —— `campaign:delete`（行内删除，落点 `:200`）
      **追加**进 T012 的同一文件（故与 T012 串行）；头部 docstring 里「campaign:delete 不在本文件范围内」
      一句须同步订正（否则文档立刻失真）
- [ ] **T019** `email/EmailUnsubscribePage.perm.test.tsx` + `marketing/EmailCampaignPage.perm.test.tsx` ——
      `email:manage`（「恢复」`:41` / 「测试发送」`:183`）
      **同码两个独立站点，必须各自双向成对，不得互推**
- [ ] **T020** `exports/ScheduledExportExecutionHistoryPage.perm.test.tsx` —— `export:scheduled`
      （「立即执行」`:104`）。⚠️ 同码在 `ScheduledExportListPage` 的落点**已覆盖**，勿重复
      `role:manage`（⚠️ **行内编辑/删除 + 工具栏新建，一码两处**）

## 收尾

- [x] **T090** 定向跑本规格全部 `*.perm.test.tsx`，记录文件数与用例数（须 ≥36 条新用例）与退出码
- [x] **T091** 跑前端六道门禁，**逐条单独取退出码**（不得取管道尾部）
- [x] **T092** 自检：`grep -rn "MUTATION-PROBE" frontend/src/` 为空
- [x] **T093** 自检零生产代码改动：`git diff --stat` 中除 `*.perm.test.tsx` 与 `specs/087-*/` 外无文件
- [x] **T094** 产出「码 → 用例文件 → 用例名 → 正/负向」对照表（FR-006，可复核）
- [x] **T095** 记 `specs/README.md`：版本行加 087、模块加一行、编号说明加 087 段
- [x] **T096** 记「附带发现」（若途中发现任何生产代码问题，**只记录不修改**）

## 执行记录

### 一、T090 定向跑（2026-09-13 18:19:21）

```
npx vitest run <11 个 *.perm.test.tsx>      # exit=0
Test Files  11 passed (11)
     Tests  69 passed (69)      # 62 条新增 + 086 既有 7 条（Contract 4 / Lead 3）
```

62 条新增 > FR-001 要求的 ≥36 条。达标不代表达标即止——**用例数不是本规格的证据**，
证据是「每一条负向用例都被观察到在判据恒真后转红」，逐条留痕见各 T010~T020 的交付说明。

### 二、T091 六道门禁（冻结版本，逐条单独取退出码）

| 门禁 | 退出码 | 关键输出 |
|---|---|---|
| `typecheck` | **0** | `tsc --noEmit` 无输出 |
| `lint` | **0** | `eslint .` 无输出 |
| `i18n:check` | **0** | zh-CN 2884 键 / en 2884 键，双边一致 |
| `menu:check` | **0** | 56 个菜单项，与 `RoleConstants.MENU_TREE` 一致 |
| `perms:check` | **0** | 63 个权限码；8 个文件含已登记的 ADMIN 判断共 9 处 |
| `test:coverage` | **0** | 72 文件 / 308 用例全过；statements 67.15 / branches 72.6 / functions 33.94 / lines 67.15 |
| 后端 `FrontendPermissionCodeAlignmentTest`（SC-003） | **0** | `Tests run: 2, Failures: 0, Errors: 0`（surefire 报告 mtime 18:28:41，非陈旧产物） |

> 末行单独说明：`mvn -B -q test -Dtest=…` 的 `-q` **只回退出码**，而 `-DfailIfNoTests=false` 会让
> 「过滤器没匹配到任何测试」也报成功——两者叠加正好构成一个"看起来有门禁"。
> 故本行不采信退出码，改采信 **surefire 报告正文 + mtime**（`Tests run: 2`，且时间落在本次运行窗口内）。

**T091 第一次并不绿——过程必须留在记录里：**

1. 首次 `test:coverage`（18:20:16）**exit=1**：`CustomerListPage.perm.test.tsx` 的
   `持 customer:claim 且切到公海视图的 SALES 看得见行内「领取」（正向）` **超时 20s**。
2. 单跑该文件 **13/13 全绿（exit=0，19.5s）** ⇒ 不是判据错，是**全量并行下的 CPU 争用**。
3. 复跑 `test:coverage` 第二次，**同一用例再次超时** ⇒ 不是抖动，是稳定复现的余量不足。
4. 取逐文件耗时（第二次运行日志）：**本规格的文件恰好是全仓最重的几个**——

   | 文件 | 全量耗时 | 例数 | 每例 |
   |---|---|---|---|
   | `customers/CustomerListPage.perm.test.tsx` | 102.1s | 13 | **7.85s** |
   | `contracts/ContractDetailPage.perm.test.tsx` | 74.1s | 8 | **9.27s** |
   | `quotes/QuoteDetailPage.perm.test.tsx` | 23.6s | 4 | 5.90s |
   | `email/EmailUnsubscribePage.perm.test.tsx` | 21.8s | 5 | 4.35s |
   | `marketing/CampaignListPage.perm.test.tsx` | 23.0s | 7 | 3.29s |
   | `leads/LeadListPage.perm.test.tsx` | 24.2s | 8 | 3.02s |

   对照：单跑 CustomerList 为 15.4s/13 例 = **1.19s/例**，即全量并行下**慢约 6.6 倍**；
   「切公海视图」那几条要连续渲染两轮，于是越过 `vite.config.ts` 的全局 `testTimeout: 20000`。
5. 处置：给**每例耗时最紧的两个文件**（CustomerList 7.85s/例、Contract 9.27s/例）加
   `vi.setConfig({ testTimeout: 60_000 })`，**只放宽超时上限，断言一条不放松**——判据写错仍是红的。
   `vi.setConfig` 在模块作用域是否生效**未照文档假设**：写了一条 1.5s 的探针用例 + `testTimeout: 500`，
   观察到它以 500ms 转红，确认生效后才落笔（探针已删）。
6. 改后 `test:coverage` **exit=0**（72 文件 / 308 用例），其余五道在冻结版本上复跑亦全 0。

> 口径边界：`testTimeout` 是**机器负载**的判据，不是产品行为的判据。放宽它不影响本规格的任何结论；
> 但它也不该被当成"这个文件没问题"的证据——那些用例证明的是判据，靠的是逐条变异自验。

### 三、T092 / T093 自检

- **T092**：`grep -rn "MUTATION-PROBE" frontend/src/` → **空**（exit=1）。
  该检查的时点很关键：四个批次的 agent 与三个追补 agent 的**变异自验期间**，全仓 grep 会命中
  `CustomerListPage:268/:346/:352/:365`、`LeadListPage:84`、`CampaignListPage:185/:195`、
  `EmailCampaignPage:183`、`OrderListPage:65`、`ContractDetailPage:119` 等**在飞探针**——
  它们秒级出现又消失。**中途一次 grep 为空或非空都不能单独作数**，本记录取的是**全体 agent 停手后**的一次。
- **T093**：`git status --porcelain` 中与前端有关的只有 9 个新 `*.perm.test.tsx` + 2 个已存在的
  `*.perm.test.tsx`（` M`）；**无任何生产代码文件**。原先被探针临时改动的
  `CustomerListPage.tsx` / `LeadListPage.tsx` 已逐字还原、不再出现在工作区清单里。
  （工作区里另有 `ci.yml` / `Dockerfile` / `backend/pom.xml` 三件属对等会话，及 `specs/086/tasks.md`
  的 T101 记录——后者是本会话写于 086 提交之后的补记，非本规格产物。）

### 四、T094 码 → 文件 → 用例 → 向 对照表（FR-006）

> 「向」：**正** = 持码 ⇒ 控件在；**负** = 不持码 ⇒ 控件不在；**正交** = 判据里权限之外的那一半；
> **行为** = 点击后真的打到那个端点；**ADMIN** = 仅作正向/直通锚点，不承担否定效力。

| 码 | 文件 | 用例 | 向 |
|---|---|---|---|
| `customer:create` | `customers/CustomerListPage.perm.test.tsx` | 1 / 2 / 12 | 正 / 负 / 负 |
| `customer:delete` | 同上 | 3 / 4 | 正 / 负 |
| `customer:import` | 同上 | 5 / 6 | 正（一码两控件） / 负 |
| `customer:claim` | 同上 | 7 / 8 / 9 | 正 / 负 / **正交**（视图） |
| `customer:pool_manage` | 同上 | 10 / 11 | 正（一码三处） / 负 |
| —— 全页 | 同上 | 13 | ADMIN 直通语义 |
| `product:create` | `products/ProductListPage.perm.test.tsx` | 2 / 3 | 正 / 负 |
| `product:update` | 同上 | 4 / 5 | 正 / 负 |
| `product:delete` | 同上 | 6 / 7 | 正 / 负 |
| —— 全页 | 同上 | 1 | ADMIN（三码直通） |
| `campaign:create` | `marketing/CampaignListPage.perm.test.tsx` | 2 / 3 | 正 / 负 |
| `campaign:update` | 同上 | 4 / 5 | 正（状态分姿态） / 负 |
| `campaign:delete` | 同上 | 6 / 7 | 正（无状态判据，ENDED 行也在） / 负 |
| —— 全页 | 同上 | 1 | ADMIN |
| `quote:approve` | `quotes/QuoteDetailPage.perm.test.tsx` | 2 / 3 / 4 | 正 / 负 / **正交**（状态） |
| —— 全页 | 同上 | 1 | ADMIN |
| `contract:approve` | `contracts/ContractDetailPage.perm.test.tsx` | 5 / 6 / 7 / 8 | 正 / 负 / **正交**（状态） / 负（不串门） |
| `lead:assign` | `leads/LeadListPage.perm.test.tsx` | 6 / 5 / 7 / 8 | 正 / 负 / **正交**（归属） / 负（不串门） |
| —— 全页 | 同上 | 4 | ADMIN |
| `order:delete` | `orders/OrderListPage.perm.test.tsx` | 3 / 2 | 正 / 负 |
| —— 全页 | 同上 | 1 | ADMIN |
| `role:manage` | `roles/RoleListPage.perm.test.tsx` | 3 / 2 / 4 | 正（一码两处同进同出） / 负 / **正交**（builtIn） |
| —— 全页 | 同上 | 1 | ADMIN |
| `email:manage` | `email/EmailUnsubscribePage.perm.test.tsx` | 1 / 2 / 3 / 5 | 正 / 负 / 负（异码不放行） / 行为 |
| `email:manage` | `marketing/EmailCampaignPage.perm.test.tsx` | 1 / 2 / 3 / 5 | 正 / 负 / 负（异码不放行） / 行为 |
| `export:scheduled` | `exports/ScheduledExportExecutionHistoryPage.perm.test.tsx` | 3 / 2 / 4 / 5 | 正 / 负 / 负（同族不串门） / 行为 |

**同文件内 086 既有、本规格未改动的用例**（它们是"不得改动既有断言"这条约束的物证）：
`contract:update` 4 条（Contract 文件 1~4）、`lead:delete` 3 条（Lead 文件 1~3）。

### 五、T096 附带发现（**只记录，未修改任何生产代码**）

按可靠性排序——**前两条是真正的缺口，后几条是记录**：

1. **`customer:pool_manage` 不授给任何真实角色**（`V87__permission_matrix_alignment_batch3.sql` 只授了
   `customer:claim`；设计说明见 `CustomerPoolController:30-31` 与 `RoleConstants:122-126`）。
   后果：本规格对它的两条用例（10/11）正向主体只能是**合成用户**。这不是假绿（"码一旦授出就得立刻生效"
   本就该这样验），但它意味着**该码的接线在真实角色上永远走不到**——若这是设计意图，应在某处写明；
   若不是，就是漏授。
2. **`EmailCampaignPage` 工具栏的「测试邮箱」输入框未收口**（`:224-231`）：它无判据、对所有人渲染，
   而它服务的唯一去向（「测试」链接 `:183`）需要 `email:manage`。无码用户会看到一个**按钮没了、输入框还在**
   的孤立控件。不属破坏性动作，086/087 的口径均未覆盖（本规格的用例反而把它当恒在锚点用）。
3. **同一码在列表端点上也被校验** ⇒ 收口在真实角色上不可观测：`GET /email/unsubscribes`（`EmailController:127`）与
   `GET /email-campaigns`（`:101`）挂的都是 `email:manage`，非持有者连列表都拉不到（403），数据行根本不会出现。
   与 086 已记录的 `department:manage` / `open_platform:manage`「判据当下冗余」同型。本规格的负向用例
  锁的是**判据正确性**（收口写法对得上码），不是生产可观测差异——已写进两个文件的类注释。
4. **`campaign:delete` 的真实拒绝条件是归因数据而非状态**：`MarketingController:106-112` 只标码，
   `MarketingCampaignService.delete()`（`:132-143`）在 `leadCount/customerCount > 0` 时以
   `CAMPAIGN_HAS_ATTRIBUTION` 拒绝。**但前端对该两列 > 0 的行照样渲染「删除」**——点下去必然 4xx。
   可仿照别处 `linkCount` 的写法按行加条件。（本规格的 fixture 三行归因均为 0，故该分支在测试里不可见。）
5. **「上传附件」无判据**（`ContractDetailPage.tsx:381-390`）：后端 `POST /contracts/{id}/attachments`
   （`ContractAttachmentController:55-58`）标的是 `contract:update`，而前端该 `<Button>` 无任何判据；
   同卡片内**同一个后端码**的「删除附件」却受 `canUpdate` 管（`:171`）。**核过后定性为"符合既定裁决"而非漏接线**：
   「上传」属 086 决策 1 明说**不收口**的「新建类」，只是它与相邻的「删除附件」并列时容易被读成自相矛盾。
   若将来决定收口新建类，这里是清单上的一处。
6. **登记表的码数是 63，不是 086 `plan.md` 写的 62**（`perms:check` 输出「63 个权限码」）。
   086 plan 的算式是"原 17 + 新 45 = 62"，与登记表实际条目数差 1。**不影响任何门禁**
   （`FrontendPermissionCodeAlignmentTest` 的下界是 `hasSizeGreaterThanOrEqualTo(16)`，
   `perms:check` 查的是值不重复）——纯文档算术误差，记在此处备查。
7. **`ScheduledExportExecutionHistoryPage` 的两处小瑕疵**（`:77` 浮空 Promise `loadDetail();` 与
   `:69` 的 `void loadDetail()` 风格不一；`:101`「刷新」与 `:105`「立即执行」共用 `ReloadOutlined`
   图标，仅靠图标无法区分）——均无功能影响。
8. **`CustomerListPage` 池视图下 `customer:update` 的「编辑」分支不可达**（`:268-278` 的三元在
   `view === 'pool'` 时永远走 claim 分支）：设计使然（公海行只能领），无用例钉住，属已知有意缺口。
9. **`rowSelection` 的视图半边未覆盖**（`:346` 是 `can[pool_manage] && view !== 'pool'`，
   一条判据含两个变量）：本规格只覆盖了码那半边，「持池运维码但切到公海视图时行选择消失」有意未覆盖。
10. **全仓 `testTimeout` 余量已所剩不多**（非本规格引入，但本规格的文件把最紧的几例顶到了台前）：
    086 的 `open/OpenPlatformPage.perm.test.tsx` 在全量运行下已是 43.9s/4 例 = **11.0s/例**，
    比本规格的 Contract 还紧，却**没有**显式超时。上述第 5 条之外的处置只针对本规格的文件，
    这条留给后续——**别让下一个补测试的人以为是自己的用例写坏了**。
