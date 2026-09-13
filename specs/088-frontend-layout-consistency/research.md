# 调研：前端布局规范与表单体验（088）

**Created**: 2026-09-13

本文件是 088 的**取证底稿**，决定 plan.md 的取舍。三件事：①把 plan 起草时引用的每个数字
用脚本重新导出（§2、§3）；②**推翻我自己在 plan 里写下的一个阻塞项前提**（§1）；
③订正 plan 里数错、指错、以及口径陷阱造成的假数（§4、§5）。

> 实现期（P1）又发现两处**本文件自己写错的数**，以及覆盖率基线的一次更新，
> 集中在 §8、§9、§10。**§7 表格里「规则 2 = 23」「规则 6 = 11（6 处该翻译）」两行的口径已被 §8/§9 取代**，
> 但两张表量的是不同范围，故**保留原表**而不是改数字——见 §8 开头。

**所有数字都由 `measure-ui-baseline.mjs` 产出，不是手数的。** 重跑方式：

```bash
cd <repo root>
node specs/088-frontend-layout-consistency/measure-ui-baseline.mjs   # 约 3 秒，退出码 0 = 自检通过
```

同目录的 `baseline-output.txt` 是 2026-09-13 的完整输出存档。

> **为什么坚持「脚本产出」而不是「agent 数一遍」**：plan 起草时有两个数字
> （「49 页裸 fragment / 12 页包 Card」）在复核时**未能复现**——换个数法得到 88 / 40。
> 那次失败不是谁数错了，而是**定义没被写下来**：同一个词（"页面"、"表单"）在两次统计里
> 指的不是同一批文件。故本脚本的第一条纪律是**每个数字连定义一起输出**（§2 各表头都带定义），
> 并且末尾有反空洞自检（§6）——它在本次开发中真的抓住了两个 bug，见 §5.3。

---

## 1. 覆盖率基线：**P0 的阻塞项不存在，且我在 plan 里写的前提是错的**

plan 的 P0 把「覆盖率读数不确定性」列为**阻塞项**，理由写在「推进顺序」一节：

> 本规格每一个门禁读数都是 `functions` 的增量，而 `functions` 只有约 **0.9pp 余量**
> （阈值 21.4，实测 22.29–22.43）……基线不稳，"有没有回归"这句话就不可证伪。

**实测结论：余量不是 0.9pp，是 12.54pp；读数在冻结工作区上稳定。阻塞项解除。**

### 方法（可复现）

```bash
cd frontend
SNAP() { find src -type f \( -name '*.ts' -o -name '*.tsx' \) | sort | xargs sha1sum | sha1sum | cut -d' ' -f1; }
echo "before=$(SNAP)"; pnpm test:coverage; echo "after=$(SNAP)"
```

`SNAP` 是整棵 `src/**` 的内容摘要。**四次运行前后摘要逐字相同**
（`0baf274cb894a13376d8947fb34535a23ed64ce2`）——即"复跑期间有别的会话在改 `src/pages/**`"
这个候选成因**本次被排除**，因为文件集在整个运行窗口内一个字节都没动。

### 读数（3 次，工作区冻结）

| 指标 | 阈值 | run 2 | run 3 | run 4 | 余量 |
|---|---|---|---|---|---|
| statements | 33.6 | 67.15 | 67.15 | 67.15 | **+33.55** |
| branches | 47.2 | 72.60 | 72.61 | 72.61 | **+25.41** |
| functions | 21.4 | 33.94 | 33.94 | 33.94 | **+12.54** |
| lines | 33.6 | 67.15 | 67.15 | 67.15 | **+33.55** |

72 个测试文件 / 308 个用例全通过，退出码 0，单次约 108–115 秒。

**稳定性**：statements / functions / lines **三次逐位相同**；branches 只在末位百分位上动
（72.60 → 72.61 → 72.61，0.01pp）。plan 记录的旧区间（functions 22.29–22.43）**未复现**。

### 阈值门禁是**活的**（不是假设）

退出码 0 只说明"没报错"，不说明"检查发生了"。故实测一次：

```
$ pnpm exec vitest run src/store/authStore.test.ts --coverage --coverage.thresholds.functions=99
exit_code=1
ERROR: Coverage for functions (2.3%) does not meet global threshold (99%)
ERROR: Coverage for lines (0.13%) does not meet global threshold (33.6%)
```

故全量运行的退出码 0 是**真实的通过**，而非门禁空转。

### 为什么与 `vite.config.ts` 记的数（47.02 / 22.29）差这么多

**记录过期了，不是数字错。** `vite.config.ts:45-56` 那次快照写的是「22 文件 / 96 用例」，
而现在是 **72 文件 / 308 用例**。086（45 页权限接线）与 087（18 个码的渲染补课）新增的
大量整页渲染用例，把此前从未执行过的页面代码真正跑了起来——statements 47.02 → 67.15
（+20pp）、functions 22.29 → 33.94（+11.65pp）正是这个量级。

**推论（对 plan 的修订）**：

1. ~~P0 要让基线稳定~~ → **不必**。三次读数已稳定，且余量 12.54pp，不需要引入
   `maxWorkers` / `fileParallelism`——plan 里"绝不与 UI 改动捆绑"的那次独立提交**取消**。
   （plan 那条纪律本身仍然对：**一个会移动覆盖率数字的配置变更必须能单独归因**，只是本轮无需触发。）
2. ~~风险"新组件压穿 `functions` 覆盖率（余量仅 ~0.9pp）"~~ → **该风险不存在**。
   连带地，plan 里**用这条风险论证的架构决定**（"`PageState` 只能合成一个、`PageShell` 干脆不做"）
   **论证失效**——结论可以保留（更少更大的组件本身仍是好设计），但**不许再引用"函数计数余量"当理由**。
3. `vite.config.ts` 的实测快照与 `specs/083-engineering-consolidation/data-model.md §4`
   需要刷新（该文件自己声明了这条同步义务）。**阈值一律不动**（084 T037 明令不得下调）。

---

## 2. 现状数字（脚本产出，含定义）

完整输出见 `baseline-output.txt`。下表是 plan 会引用的部分。

### 2.1 规模

| 项 | 值 | 定义 |
|---|---|---|
| 产品 `.ts/.tsx` | 259 | 排除 `*.test.*` 与 `src/test/**` |
| 页面 | **101** | `src/pages` 下的 **`.tsx`** |
| （被排除的非组件） | 4 | `dataVision/hooks/*.ts`、`dataVision/types.ts` |
| 页面总行数 | 22,514 | |
| 测试文件 | 72 | |
| `src/index.css` | 816 行，20 处 `!important` | |

### 2.2 表单

| 项 | 值 |
|---|---|
| 含表单的页面 | 59 |
| `<Form>` | 71 |
| `<Form.Item>` | **289** |
| `layout="vertical"` | **43** |
| `layout="horizontal"` | 22 |
| `layout="inline"` | 1 |
| 未设 layout | 5 |
| `<Descriptions layout="horizontal">`（**不是表单**） | 4 |
| `labelCol={{flex:'Npx'}}` 分布 | 100×11, 90×6, 110×2, 80×2, 70×1 |
| `labelCol` 用 `span` 数字的表单 | **0** |

4 个 `Descriptions`（**标签起始行**，非 `layout=` 所在行）：
`contracts/ContractDetailPage.tsx:313`、`customers/CustomerDetailPage.tsx:353`、
`leads/LeadDetailPage.tsx:213`、`orders/OrderDetailPage.tsx:295`。

### 2.3 栅格（**两个分母，别混**）

| 项 | 值 |
|---|---|
| `<Col>` 总数（页面级） | 160 |
| 页面级带响应式断点 | 31 |
| 页面级只写死 `span` | 129 |
| **落在 `<Form>` 区域内（表单级）** | **102** |
| **表单级只写死 `span`** | **96（94.1%）** |
| 表单级带断点 | 6 |

`Row gutter` 取值 **6 种**：16×28, 8×4, 12×3, 20×2, 24×1, 4×1。

### 2.4 弹窗

| 项 | 值 |
|---|---|
| `<Modal>` | 70 |
| **无 width 且区域内含表单**（受"必须定宽"约束） | **23** |
| 无 width 但不含表单（不在该规则内） | **9** |
| 无 width 合计 | 32 |
| width 取值种类 | **10** |

width 分布：640×13, 480×5, 520×5, 720×4, 680×3, 560×3, 760×2, 600×1, 800×1, 420×1。
（640 是事实上的默认档 ⇒ plan 的 `md` 档取值正确。）

属性覆盖：`okText` 60/70、`cancelText` **0/70**、`confirmLoading` 36/70、
`destroyOnClose` **62**、`destroyOnHidden` **0**、`footer` 9。

### 2.5 宽度缺陷（**"非缺陷"必须一起记，否则后人会去"修"**）

| 控件 | 无 width 处数 | 性质 |
|---|---|---|
| `DatePicker` / `RangePicker` / `TimePicker` | **4** | **真缺陷**——antd 5 无 `in-form-item` 规则，`.ant-picker` 是 `display:inline-flex` 且无宽度 |
| `Select` / `TreeSelect` / `Cascader` / `InputNumber` | **79** | **非缺陷**——antd 5 自动补 `.ant-select-in-form-item{width:100%}` |

4 处真缺陷（已验证行内容）：
`announcements/AnnouncementPage.tsx:207`（DatePicker）、`sla/SlaCalendarPage.tsx:94`（TimePicker）、
`:97`（TimePicker）、`:109`（DatePicker）。

### 2.6 设计系统采纳度（**JSX 调用点**，不含 import 与组件自身定义）

| 组件 / 写法 | 调用点 | 文件 |
|---|---|---|
| `<Tag color=>` | **138** | 67 |
| `<StatusTag>` | 26 | 4 |
| `<AmountDisplay>` | 4 | 1 |
| `<StatCard>` | 3 | 3 |
| `<Statistic>` | 28 | 8 |
| `.toLocaleString(` | 39 | 22 |
| `¥` 字面量 | 23 | 11 |
| `/ 100`（分转元） | 34 | 11 |
| `borderRadius: 10` | **66** | 54 |
| 从 `components/ui` barrel 导入 | **4** | 4 |

那 4 个 barrel 采纳者就是 077 交付的那 4 个详情页：
`contracts/ContractDetailPage`、`customers/CustomerDetailPage`、`leads/LeadDetailPage`、`orders/OrderDetailPage`。
**101 个页面里 4 个**——"设计系统只覆盖 4/101"这个判断成立。

### 2.7 品牌色：**没有任何一个文件同时含两套主色**

| 字面量 | 次数 | 文件 |
|---|---|---|
| `#1677ff`（antd 默认蓝） | **33** | 12 |
| `#6366f1`（Indigo） | 1 | `src/index.css` |
| `#4f46e5` | 1 | `src/index.css` |
| `#4338ca` | 1 | `src/index.css` |
| `#eef2ff` | 1 | `src/index.css` |

`#1677ff` 逐文件：`types/usageMap.ts`×11、`stats/DashboardPage.tsx`×7、`map/UsageMapPage.tsx`×4、
`App.tsx`×2、`components/CommentSection.tsx`×2，其余 8 个文件各 1
（`components/InstallPrompt`、`dataVision/components/FunnelChart`、`invoices/InvoiceListPage`、
`landing/LandingPageView`、`LoginPage`、`tasks/TaskListPage`、`visits/VisitListPage`）。

**这个"零重叠"是关键证据**：Indigo 只在 CSS、antd 蓝只在 TS/TSX——所以**两套主色并存**
是真实的结构性分裂，不是某个文件的笔误。

⚠️ **绝不能批量替换的两个站点**（`check-ui.mjs` 规则 1 的第一价值就是禁止它）：
- `pages/landing/LandingPageView.tsx:54` 的 `lp.themeColor || '#1677ff'`——**租户自配主题的兜底值**，不是品牌色；
- `types/usageMap.ts` 的 11 处——**图谱演示数据**。

### 2.9 表单字段规范（`check-ui.mjs` 规则 4/5/6 的基数）

| 项 | 值 | 备注 |
|---|---|---|
| rules 含 `required: true` 的 `Form.Item` | 143 | 其中落在 `<Form.List>` 区域内 3 |
| **规则 4**：`required: true` 无 `message` | **5** | `calls/CallRecordPage.tsx:224,229`、`custom-object/CustomObjectListPage.tsx:179`、`marketing/EmailCampaignPage.tsx:254`、`roles/RoleListPage.tsx:326` |
| **规则 5**：`required: true` 无 `label`，**`Form.List` 之外** | **3** | 全部在 `LoginPage.tsx:218,229,240` |
| （`Form.List` **之内**，有意为之 → 白名单） | 3 | `custom-object/CustomObjectListPage.tsx:173,176,179` |
| **规则 6**：裸字符串 `placeholder`/`aria-label`/`title` | **11** | 见下 |

规则 6 的 11 处逐一：`App.tsx` `aria-label="折叠/展开菜单"`、`LoginPage.tsx` `title="点击刷新验证码"`
与 `aria-label="验证码图片"`、`contacts/ContactListPage.tsx` `placeholder="Default: Other"`、
`mail/MailSyncPage.tsx` 三处（`sales@corp.com` / `imap.corp.com` / `smtp.corp.com`）、
`marketing/OnlineFormPage.tsx` `placeholder="field"`、`products/ProductListPage.tsx` 两处
（`Currency` / `Price (CNY)`）、`settings/OpportunityStagePage.tsx` `placeholder="BUDGET_APPROVAL"`。

⚠️ **规则 6 的 11 处里有一半不是"漏翻译"**：`sales@corp.com` / `imap.corp.com` / `smtp.corp.com`
是**格式示例**，`field` / `BUDGET_APPROVAL` 是**代码值示例**——这些**不该**被翻译。
真正该走 `t()` 的是 `折叠/展开菜单`、`点击刷新验证码`、`验证码图片`、`Default: Other`、
`Currency`、`Price (CNY)` 这 6 处。**故规则 6 从第一天起就必须带白名单机制**
（这也是它不能"一次写成 error 再慢慢补"的原因，见 plan 的「先宽后紧」）。

### 2.10 孤儿组件

| 项 | 值 |
|---|---|
| `src/components` 下 `.tsx` 组件 | 19 |
| 零引用（**真孤儿**） | **1** —— `components/ContactsCard.tsx` |
| 只被 barrel 提到（导出了但没人用） | 0 |

### 2.8 `index.css` 的死代码（**口径陷阱已修正**）

| 项 | 值 |
|---|---|
| 类选择器名总数 | 85 |
| 其中 antd/pro 生成的（`.ant-*`/`.anticon-*`/`.pro-*`） | 44 |
| 其中**自有类** | 41 |
| 自有类里 0 处 tsx 引用的（**真死代码**） | **12（29%）** |

真死代码（全量）：`.dashboard-progress`、`.filter-bar`、`.filter-tag`、`.funnel-bar`、
`.page-header`、`.page-header-actions`、`.page-header-subtitle`、`.page-header-title`、
`.progress-lg`、`.progress-sm`、`.view-switcher`、`.view-switcher-item`。

**两个口径陷阱**（都曾把数字带偏，已修）：
1. `.ant-btn-primary` 这类**本来就是 antd 生成的类名**，CSS 写它是在"覆盖"，tsx 天然不会引用——
   算成死类会得出虚高的 65%。**只有"自有类"那 41 个里的 0 引用才叫死代码。**
2. 类名正则会匹配**注释里的点**：`/* … Row/Col 内 Form.Item 的间距 */` 会产出假类名 `.Item`。
   剥注释后总数从 86 降到 85、自有类清单才干净。

判定方向说明：使用率测试是 `tsx 原文里是否出现该名字`，**偏保守**（名字出现在别处即算"在用"），
故**死代码清单是可靠的**（假阴性方向只会漏报、不会误报"在用"）。

---

## 3. plan 引用数字的逐条对账

| plan 里写的 | 本次实测 | 判定 |
|---|---|---|
| 101 个非测试页面 / 22,413 行 | 101（`.tsx`） / 22,514 行 | ✅ 页数精确；行数微差 |
| 59 个页面含表单 | 59 | ✅ |
| 71 个 `<Form>` | 71 | ✅ |
| 277 个 `Form.Item` | **289** | ⚠️ 修正 |
| `vertical` 48 | **43**（另 5 个未设 layout） | ⚠️ 修正 |
| `horizontal` 22 · `inline` 1 | 22 · 1 | ✅ |
| 4 个 `<Descriptions layout="horizontal">` | 4 | ✅ |
| `labelCol` 5 种取值 1/2/6/11/2 | 70×1, 80×2, 90×6, 100×11, 110×2 | ✅ 逐个吻合 |
| 「横向表单漏 labelCol」不成立（0 处） | 0 | ✅ 该"常见判断"确为幻影 |
| 96/102 个表单 Col 写死 span（94%） | 96/102（**94.1%**） | ✅✅ 精确吻合 |
| 全库仅 6 个响应式表单 Col | 6（表单级） | ✅ |
| 8 种 gutter | **6 种** | ⚠️ 修正 |
| 70 个 Modal | 70 | ✅ |
| 32 个 Modal 没设宽度 | 23 含表单 + 9 不含 = **32** | ✅✅ 两个数都对 |
| 12 个不含表单的 Modal | **9** | ⚠️ 修正 |
| 11 种 Modal 宽度 | **10 种** | ⚠️ 修正 |
| `okText` 60/70 · `cancelText` 0 · `confirmLoading` 36 | 60 · 0 · 36 | ✅ |
| `destroyOnClose` 62 · `destroyOnHidden` 0 | 62 · 0 | ✅ |
| `footer` 9 | 9 | ✅ |
| 4 处 DatePicker/TimePicker 缺陷 + 3 个行号 | 4 处，行号**逐一吻合** | ✅✅ |
| 70 处 Select 无宽度（非缺陷） | **79** | ⚠️ 修正 |
| 130 处 `<Tag color=>` vs ~20 处 `StatusTag` | **138** vs **26** | ⚠️ 近似偏高 |
| 39 处 `toLocaleString` · 21 处 `¥` · 31 处 `/100` | 39 · 23 · 34 | ⚠️ 后两个近似 |
| 28 处 `<Statistic>` vs 5 处 `StatCard` | 28 vs **3** | ⚠️ `StatCard` 修正 |
| 4 处 `AmountDisplay` | 4 | ✅ |
| 31 处 `borderRadius: 10` / 34 处 `cardProps` | **66** / 54 文件 | ⚠️ 修正 |
| 33 处 `#1677ff` / 12 文件 + 逐文件分布 | 33 / 12，**分布逐一吻合** | ✅✅ |
| 4 个页面 import `components/ui` | 4 | ✅✅ |
| `.ai-card` 是死 CSS | **该选择器根本不存在** | ❌ **订正** |
| `index.css` 约一半是死代码 | 自有类 29% | ❌ **订正**（原数来自 §2.8 的口径陷阱） |
| 约 12–13 个 DOM 结构敏感测试 | **13** | ✅ |
| `CampaignListPage.perm` 33 处 `within` | 33 | ✅✅ |
| `CustomerListPage.perm.test.tsx` 的 `within`/`closest` = 0/0 | 0/0，且 `toHaveClass`×2 | ✅✅ 见 §4.2 |
| `OpportunityStagePage.perm` 靠 `tagName` 判别 | 全库唯一，×2 | ✅✅ |
| 「49 页裸 fragment / 12 页包 Card」 | **46 / 4** | ❌ **撤回，见 §5** |
| 规则 4：`required: true` 无 `message` 5 处 | **5**，含 `CallRecordPage:224,229` | ✅✅ 逐个吻合 |
| 规则 5：`required: true` 无 `label` 6 处 | **6**（3 在 `Form.List` 外 + 3 在 `Form.List` 内） | ✅✅ 数吻合，且"要给 Form.List 留白名单"的预判正是那 3 处 |
| 规则 6：裸 `placeholder`/`aria-label` 8 处 | **11** | ⚠️ 修正；且其中 5 处**不该**被翻译（见 §2.9） |
| 孤儿组件 1 个（`ContactsCard.tsx`） | 1（19 个组件里） | ✅✅ |

---

## 4. 订正：plan 里三处需要改写的表述

### 4.1 `.ai-card` 不是死代码——它不存在

plan 的「非目标」把 `.ai-card` 与 `.page-header*` 并列为死代码。实测
`src/index.css` 里**没有 `.ai-card` 这个选择器**（`grep -n 'ai-card' src/index.css` 无输出）；
真正存在且在用的是 `.ai-suggestion-card` 与 `.ai-icon-pulse`。**纯误记，删掉即可。**

### 4.2 `CustomerListPage` 的测试耦合：两份报告都没说错，说的是**两个不同的文件**

这是本规格内部唯一一次"两个 agent 互相矛盾"的地方，现已用数字结清：

| 文件 | `within` | `closest` | `toHaveClass` |
|---|---|---|---|
| `customers/CustomerListPage.perm.test.tsx` | **0** | **0** | **2** |
| `customers/CustomerListPage.test.tsx` | **3** | 0 | 0 |

**两个文件都叫 CustomerListPage 的测试。** 说"它用 within"的报告看的是后者；说"0/0"的看的是前者。
plan 里"我上一版写错了"那条订正**方向正确但表述不完整**——应写成：
**perm 那个文件 0/0（它的约束是 `toHaveClass`×2），非 perm 那个有 3 处 `within`。**
逐行核对：`:154` 与 `:245` 的 `toHaveClass('ant-btn-primary')` 都在 **`.perm.test.tsx`**。

### 4.3 `Descriptions` 的行号锚点

plan 引的是 `layout=` 所在行（317/357/217/299），脚本引的是**标签起始行**
（313/353/213/295）。两者差 4 行，都没错；**统一用标签起始行**——它是唯一的，
而"第几个 `layout=`"在文件里不唯一。

---

## 5. 撤回：「49 页裸 fragment / 12 页包 Card」

plan 已自行标注这两个数"未能复现，在 P0 重新导出之前不得作为证据引用"。现用 AST 重新导出。

**方法**：找默认导出组件 → 取其函数体内**最外层**的 `return` 表达式（不进入嵌套函数，
以排除 `renderXxx` 内部回调）→ 解包括号/断言 → 若为条件表达式则取两支的并集 →
按最外层 JSX 的种类归类。这不是启发式，是 TypeScript AST。

| 根元素 | 页数 |
|---|---|
| `<>` fragment | **46** |
| `<div>` | **38** |
| `<other>`（自绘图表 / 少见） | 9 |
| `<Card>` | **4** |
| `<span>` | 2 |
| `<Space>` | 1 |
| `<GlowBorder>` | 1 |
| 合计 | **101** ✅ 与 §2.1 的页数吻合 |

**与旧数的关系**：旧数（49 / 12）**既不等于 46 / 4，也不等于任何"另一种数法"**——
它的口径已经无从考证，故**废弃**，以本节数字为准。若要说"页面外壳不统一"，
**正确的表述是**：101 页里 46 页裸 fragment、38 页自裹 `<div>`、**只有 4 页用 `<Card>`**——
三个来源平分天下，这正是 `.page-container > .ant-card { margin-bottom: 16px }`
那条 hack（`index.css:126-130`，注释自陈"避免各页 12/16/20 混用"）不得不存在的原因。

---

## 6. 元事实：脚本自己的自检抓到了两个 bug

`measure-ui-baseline.mjs` 末尾有 §9 反空洞自检。**它在本次开发中真的红了两次**，
两个都是会造成整类数字失真的 bug——记在这里，因为它们解释了为什么不能只信"脚本跑通了"：

1. **闭标签被整类丢弃**。`scanTagEvents` 里两种针长度不同（`</Form` 比 `<Form` 多一个 `/`），
   我却一律用 `openNeedle.length` 往后看一格，于是 `</Form>` 看到的"后一个字符"是 `m`——
   恰好是标识符字符，被"标签名整体匹配"守卫判成 `</FormItem` 而**整个丢弃**。
   后果：`tagRegions` 恒为空 ⇒「表单级 Col = 0」、32 个无宽弹窗**全部**被判成"不含表单"
   （真值 23 含 / 9 不含）。自检的 `formCols === 0` 与 `pickers.length === 0` 两条同时转红。
2. **行号用 `code.indexOf(tag)` 取**。同一段标签文本出现两次时，两次都指向第一次出现——
   `SlaCalendarPage.tsx:94` 的两个 `<TimePicker format="HH:mm" />` 会报成同一行。
   修法是让扫描器直接返回每个事件的真实下标。

另有三个**口径**问题（不是 bug，是定义），修完后数字才可比：
`.css` 文件没被收进扫描集（`#6366f1` 在 `index.css` 里，漏掉就看不到"零重叠"这个关键证据）；
「页面」口径未限定 `.tsx`（多算 4 个 hooks/types）；CSS 类名未剥注释（`.Item` 假类名）。

**结论：数字的可信度来自自检，不来自脚本存在。**

---

## 7. 对本规格设计的直接影响（写给 plan 的修订）

1. **P0 由"两个阻塞项"缩为"一个"**：覆盖率基线不必再查（§1 已结论）；剩余的是把数字写进本文件（已完成）。
2. **`check-ui.mjs` 六条规则的实测违规数**（均已重导，见 §2.5–§2.10）：

   | 规则 | 实测 | 处置 |
   |---|---|---|
   | 1 品牌色字面量 | `#1677ff` **33 处 / 12 文件**；`#6366f1`/`#4f46e5`/`#4338ca`/`#eef2ff` **各 1 处且全在 `index.css`** | 白名单必须含 `index.css` 与 `src/theme/**`；两个"绝不能扫"的站点见 §2.7 |
   | 2 表单弹窗必须定宽 | **23** | 与 plan 一致 |
   | 3 表单内 `<Col>` 必须带断点 | **96/102** | 与 plan 一致 |
   | 4 `required: true` 带 `message` | **5** | 与 plan 一致 |
   | 5 `required: true` 带 `label` | **6**（3 外 + 3 内） | 与 plan 一致；白名单就是那 3 处 `Form.List` |
   | 6 禁裸字符串文案属性 | **11**（不是 8） | 修正；且 **5 处不该被翻译**，故**从第一天起就要能白名单** |

   **规则 6 是六条里唯一不能"先写成 error 再慢慢补"的**：它命中的 11 处里有 5 处
   （邮件格式示例、字段名示例）**本来就是英文**，写成 error 会逼人给不该翻译的东西加翻译键。
   这也是 plan「先宽后紧」策略的一个额外理由——不只是"量太大"，而是**规则本身需要先长出白名单**。
3. **P3 的分批依据有了精确基数**：纵向表单 43 个（不是 48）、横向 22 个、无 width 的含表单弹窗 23 个。
4. **`fontSize: 13` 的支持证据更硬**：`index.css:59` 的 `--font-size-base: 13px` 与 `body{font-size:13px}`
   已生效，而 antd 默认 14 —— 页面上**今天就是混的**，接 token 是**统一**而非**改小**。
5. **`FormModal` 的默认值清单**由 §2.4 直接给出：`okText`（10 个弹窗缺）、
   `cancelText`（**70 个全缺**）、`confirmLoading`（**34 个表单弹窗缺**，36/70 有）、
   `destroyOnClose`（62 有，**拼写对当前 antd 5.22.0 正确，不要顺手改成 `destroyOnHidden`**）、
   `footer`（9 个弹窗已自定义，`FormModal` 需留透传口）。

---

## 8. 实现期订正一：规则 2 的真实基数是 **25 处 / 19 文件**，不是 §7 的 23

`check-ui.mjs` 的 R2 实测命中 **25 处**（`--strict` 下逐条列出，可复现），比 §7 表格记的 23 多 2 处。

**差额的性质：两个数都对，量的是不同范围。**

| 口径 | 命中 |
|---|---|
| `src/pages/` 内 | **23** —— 与 plan 初稿、与 §2.4 的"23 个含表单的无宽弹窗"**逐字吻合** |
| 全部产品 tsx（含 `src/components/`） | **25** |

多出的 2 处全部在 `src/components/`：`FollowUpTimeline.tsx:141`、`LeadConvertModal.tsx:43`。
**这是同一类组件（弹窗内嵌表单），只是活在 `components/` 而不是 `pages/`**——
plan 初稿的口径漏掉它们，正如 §2.6 的"设计系统只覆盖 4/101 页"也漏掉了 `components/` 里的消费方一样。

**处置**：规则保持"扫全部产品 tsx"（少扫一类文件正是口径缺陷的经典形态——§4.2 的两个
`CustomerListPage` 测试文件之争是同一种病），故**基数取 25**；**23 不删**，它是 pages-only 视角下的正确数字。
25 处的文件分布（同一文件有多个无宽弹窗，故处数 > 文件数）：

```
src/components/FollowUpTimeline.tsx            1
src/components/LeadConvertModal.tsx            1
src/pages/approval/ApprovalCenterPage.tsx      1
src/pages/contracts/ContractDetailPage.tsx     2
src/pages/customers/AtRiskCustomersPage.tsx    1
src/pages/customers/CustomerDetailPage.tsx     1
src/pages/customers/CustomerListPage.tsx       1
src/pages/departments/DepartmentListPage.tsx   1
src/pages/invoices/InvoiceListPage.tsx         1
src/pages/marketing/EmailCampaignPage.tsx      1
src/pages/open/OpenPlatformPage.tsx            2
src/pages/orders/OrderDetailPage.tsx           1
src/pages/personal/PersonalCenterPage.tsx      1
src/pages/quotes/QuoteDetailPage.tsx           1
src/pages/settings/FieldPermissionPage.tsx     1
src/pages/stats/DashboardPage.tsx              1
src/pages/tags/TagListPage.tsx                 1
src/pages/users/UserManagementPage.tsx         4
src/pages/visits/VisitListPage.tsx             2
```

---

## 9. 实现期订正二：规则 6 的 11 处**构成**与 §2.9 不同（总数巧合相同）

§2.9 记的规则 6 是「裸字符串 `placeholder` / `aria-label` / **`title`**」共 11 处；
`check-ui.mjs` 的 R6 **只扫 `placeholder` / `aria-label`**（`title=` 未被收纳）——**两者总数都是 11，构成差 1 处**：

| 站点 | §2.9 | 实现 R6 |
|---|---|---|
| `App.tsx:623` `aria-label="折叠/展开菜单"` | ✓ | ✓ |
| `LoginPage.tsx` `title="点击刷新验证码"` | ✓ | **✗（不扫 `title`）** |
| `LoginPage.tsx:274` `aria-label="验证码图片"` | ✓ | ✓ |
| `contacts/ContactListPage.tsx:307` `placeholder="Default: Other"` | ✓ | ✓ |
| `mail/MailSyncPage.tsx`（`sales@corp.com` / `imap.corp.com` / `smtp.corp.com`） | ✓ ×3 | ✓ ×3 |
| `marketing/OnlineFormPage.tsx:295` `placeholder="field"` | ✓ | ✓ |
| `products/ProductListPage.tsx`（`Currency` / `Price (CNY)`） | ✓ ×2 | ✓ ×2 |
| `settings/OpportunityStagePage.tsx:294` `placeholder="BUDGET_APPROVAL"` | ✓ | ✓ |
| `exports/ScheduledExportCreatePage.tsx:115` `{"status": "active"}` | **✗（未列出）** | ✓ |
| 合计 | **11** | **11** |

**两处措辞订正**：

1. §2.9 的结论"真正该走 `t()` 的是……这 **6** 处"应改为 **2 处**——即两个 `aria-label`
   （`App.tsx:623`、`LoginPage.tsx:274`）。理由是 R6 的判定只针对 `placeholder`/`aria-label`，
   而 `title="点击刷新验证码"` 不在本条规则范围内（它属另一个属性族，本规则**刻意不收**，
   因为 `title` 在 antd 组件里常被用作**数据展示**而非无障碍名称）。
2. 既然真实缺陷只有 2 处，且**两处都被既有测试直接断言着**
   （`App.render.test.tsx:274,277` 的 `getByLabelText('折叠/展开菜单')`、`LoginPage.test.tsx:48` 的书面记录），
   则 §7 第 2 条那句"规则 6 从第一天起就必须带白名单"**依然成立且更强**：
   11 处的**全部**都进了白名单——9 处是"不该翻译"，2 处是"该翻译但归 i18n 批次"。

---

## 10. P1 完成后的覆盖率复测（2026-09-13）

`src/theme/index.ts` + 4 个原语 + 4 个测试文件（35 用例）落地后：

| 指标 | 阈值 | 基线（§1） | **P1 后** | 变化 |
|---|---|---|---|---|
| statements | 33.6 | 67.15 | **67.65** | +0.50 |
| branches | 47.2 | 72.60–72.61 | **73.02** | +0.42 |
| functions | 21.4 | 33.94 | **34.29** | **+0.35** |
| lines | 33.6 | 67.15 | **67.65** | +0.50 |

`Test Files 76 passed (76)` / `Tests 343 passed (343)`（基线 72 / 308；**+4 文件 / +35 用例**逐一对上）。
新原语的自身覆盖：`src/components/ui` 92.12 / 86.84 / 90.9 / 92.12，`src/theme` 100/100/100/100。

**`functions` 余量 12.54pp → 12.89pp，该风险项二次证伪**（它已先被 §1 证伪过一次）。

⚠️ **口径边界**：同一次会话内 `branches` 报过 **73.08** 与 **73.02**（两次相邻运行），
与 `vite.config.ts:61-64` 自己记录的非确定性同源。余量 25.8pp，无害——
但这**再次印证**"单次小数位不可当论据"：此后一律**与 §1/§10 的区间比**，不与上一次比。
本次未复现 §1 的"statements/functions/lines 三次逐位相同"那种稳定性（仅两次运行），故不给出新的稳定性结论。

### 护栏自验：三条路径各实测转红一次（SC-004）

| 场景 | 造法 | 观察到的输出 | 还原后 |
|---|---|---|---|
| ① 真违规 | 新建探针 `src/zz-check-ui-probe.ts` 内含 `'#1677ff'` | R1 红，**exit 1**，并打印**可粘贴的白名单条目** | 删探针 → 绿 |
| ② 白名单陈旧（"少"方向） | `MailSyncPage` 条目计数 3 → 4 | `【R6 白名单陈旧】… 白名单登记 4 处，实际命中 3 处`，**exit 1** | 改回 3 → 绿 |
| ③ 反假绿下界 | `MIN_CANDIDATES.R6` 1 → 999 | `【R6 自检失败】… 本规则只解析出 11 个候选点（预期 ≥ 999）`，**exit 1** | 改回 1 → 绿 |

**为什么②是三者中最重要的**：双向校验的"少"方向正是让白名单**单调收缩**的机制——
没有它，白名单只会在修好一处之后继续留着一条永远为真的条目，最终退化成一张没人维护的清单。
这与 §6 的结论是同一条：**门禁的可信度来自它被证明会红，不来自它存在。**

## 11. P2 样板页 1/4：`InvoiceListPage`（T030/T031，2026-09-13）

两提交：`T030` 外壳（`.page-stack`）、`T031` 表单原语 + 销账。本节只记**实测与意外**，
不重复 tasks.md 里已有的口径。

### 11.1 T030 唯一的视觉差异：统计行到表格的间距 32px → 16px

原状是 `Row style={{ marginBottom: 16 }}` **再叠**一个手搓 `<div style={{ height: 16 }} />`
——两条各自"看着像 16px"的规则合起来是 32px。这正是 `.page-stack` 要统一的东西：
它把纵向节奏收成一个 `row-gap: 16px`，于是页内不需要任何页级 margin。
**这条差异必须在提交信息里明说**，否则将来有人看到"统计行与表格挨近了"会去别处找原因。

另记两条**非显然之处**（已写进 `index.css` 的注释）：
1. antd `Modal` 经 portal 挂到 `body`，**不是** `.page-stack` 的 DOM 子节点，因此不会白吃一个 `row-gap`
   （否则页面根元素由 fragment 改为 grid 容器时，两个弹窗会各占一条 16px 的轨道）。
2. `index.css:127-130` 的 `.page-container > div > .ant-card { margin-bottom: 16px }` 与
   `.page-stack` 的 `row-gap` 会**叠加**：页面里 ProTable 的卡片仍会自带 16px 下边距。
   本页 ProTable 是最后一个元素，故只表现为页面底部多 16px，无害；**但铺开时要记住这条**——
   若某页把卡片放在中间，它会同时吃到 `row-gap` 与 `margin-bottom`。

### 11.2 R1 白名单 33/12 → 32/11；**R2 的"候选点"分母也随迁移缩小**

`ui:check` 在 T031 前后的实测：

| 规则 | T031 前 | T031 后 | 说明 |
|---|---|---|---|
| R1 违规 | 33 处 / 12 文件 | **32 处 / 11 文件** | `InvoiceListPage` 的统计数字色随 `StatCard` 一起收掉 |
| R2 待还 | 25 处 / **61 个候选点** | **24 处 / 60 个候选点** | ← 两个数**同时**减 1 |
| R3 待还 | 96 / 102 | 96 / 102 | 本页没有表单 `<Col>`，未动 |
| 白名单合计 | 56 处 | **55 处** | 双向校验确认（"少"方向）没被误判成陈旧 |

⚠️ **R2 的两个数同时减 1，说明它的候选点是从 `<Modal` 元素数出来的**——
本页两个 `Modal` 都换成 `FormModal` 后，它们**不再出现在 R2 的分母里**。
这不是假绿（`FormModal` 恒设宽度，没有"没被量到"的风险），但它意味着
**R2 的"25 处 / 19 文件"这类数字会随 P3 铺开而单向下滑，且下滑速度快于真实还债速度**。
所以 P3 每批的出口判据不该用"R2 剩几处"，而该用"**哪些文件还在白名单里**"。
反假绿下界（`MIN_CANDIDATES`）不受影响：全库仍有 60 个候选点。

### 11.3 决定**不**收该页的 `/100`（并给 089 一条实测结论）

plan 的非目标里写"本批次只在样板页里顺手收掉已经在那儿的（`InvoiceListPage` 正好有 3 处 `/100` …）"。
**逐处核过之后，这一页只有 1 处能收，而只收 1 处会让页面内部自相矛盾，故一处都不收。**
实测（4 处 `/100`，不是 3 处）：

| 位置 | 消费方 | 能不能换 `AmountDisplay` | 原因 |
|---|---|---|---|
| `:79` 订单下拉的选项 `label` | 拼进**字符串模板** `\`${o.orderNo}（¥${…}）\`` | **不能** | `AmountDisplay` 是组件不是字符串；且该 `Select` 带 `optionFilterProp="label"`，换成 JSX 会让**搜索失效** |
| `:129` 表格金额列 `render` | 单元格 | 能 | 唯一的干净落点 |
| `:176` / `:181` 两个金额统计块 | `StatCard` 的 `value` | **不能** | `StatCardProps.value` 是 `string \| number`，塞不进组件 |

**为什么宁可不收**：只把 `:129` 换成 `AmountDisplay` 会同时改变单元格文本
（`1234.56` → `¥1,234.56`，多出货币符号与千分位），于是**表格有 ¥ 而统计块没有**——
这正是 088 要消除的那类不一致，用一个"部分采纳"制造出来。
**给 089 的输入**：`/100` 的全库清扫**不是机械替换**，每一处都要先看消费方是
"字符串模板 / 数字（喂 antd 或 StatCard）/ ReactNode"三类中的哪一类；这是 089 该先做的事。

### 11.4 顺手发现一个**既有缺陷**：这一页首屏从不拉统计

`loadStats()` 只被 `reload()` 调用（`:60-63`），而 `reload()` 只在 `onCreate`/`onVoid` 里调用
（`:96` / `:112`）——**首屏没有任何 effect 拉统计**，所以三个统计卡片在第一次进入页面时恒为
`0% / 0.00 / 0.00`，只有用户开过票或作废过一张之后才会变成真值。
换成 `StatCard` 之前它们同样是 0（原 `<Statistic>` 拿的是同一个 `stats` state），
**与 T031 无关**；但验收时它极易被误读成"`StatCard` 把数字改坏了"，故单列一条。

处理方式：`InvoiceListPage.form.test.tsx` 的第一条用例**显式钉住这个零值**
（并在注释里说明为什么钉一个 bug），随后**另起一次提交**加 `useEffect` 修它——
那时这两行断言会翻成真实值，**"修好了"就在 diff 里可见**。
不把修复混进 T031，理由与 plan 的原则一（每个视觉变化必须可归因）同源：
一个行为修复与一次布局改造捆在一起，会让"数字对了"这件事无法归因到任何一次提交。

**修复的实测留痕**（2026-09-13）：加 `useEffect` 后，**临时禁用它再跑一次**——
`form.test` 首条失败且**只有它失败**（`Unable to find an element with the text: 3500.00`，
其余 4 条仍绿），确认这条断言承载的是真实行为而不是恰好为真；随后已还原并复跑全绿。
效果只挂载期调用一次，形态照本仓惯例 `useCallback` + `useEffect(..., [load])`
（`exports/ScheduledExportListPage.tsx:60-62`）——直接写 `[]` 会让 `react-hooks/exhaustive-deps`
报缺依赖，而本仓 lint 是**零警告**的。
另注：mock 的 `totalInvoiceAmount` 由 123456 改为 **350000**，使三个统计值在页内**互不相同**
（123456 分 → 1234.56 元，会与表格行金额撞车，`getByText` 命中多个元素即抛错）。

### 11.5 `Statistic` → `StatCard` 的四处可见差异（验收清单）

| # | 差异 | 说明 |
|---|---|---|
| 1 | **数值与标签的上下顺序对调** | `Statistic` 是"标题在上、数值在下"，`StatCard` 恰好相反（`stat-card-value` 在 `stat-card-label` 之上） |
| 2 | 三张卡片**不再各自带色** | 原 `#1677ff`（蓝）/ `#3f8600`（绿）/ 默认黑 → 统一为 `--color-text-primary` |
| 3 | 圆角 10 → 12，且多一道 1px 边框 | 由 `borderRadius: 10` 字面量换成 `.stat-card` 的 `var(--radius-lg)` + `--color-border-light` |
| 4 | 窄屏由 3 列变 1 列 | `Col span={8}`（无断点）→ `xs={24} sm={12} lg={8}`；320px 下原先是 3 个约 90px 宽的卡片 |

**无差异项**（特意核过，别当回归去"修"）：两个金额卡片的数字**逐字不变**
（都是调用点 `toFixed(2)` 出来的字符串，`StatCard` 直接渲染字符串，不经 `toLocaleString`）。
唯一有理论差异的是比率卡：`invoiceRate` 是 **number**，原 `<Statistic>` 会加千分位
（`1234.5` → `1,234.5`），而 `${…}%` 不会——**百分比不可能超过 1000，故该差异不可达**。

### 11.6 作废弹窗的宽度：520（antd 默认）→ 480（`sm`）

该弹窗此前**没设宽度**，吃 antd 默认的 520；`FormModal` 要求四档之一，取最接近的 `sm`=480。
它不是 R2 的违规点（内部没有 `<Form>`，故从未进过 R2 的候选集），这次是被"同页两个弹窗走同一契约"顺带收编的。
