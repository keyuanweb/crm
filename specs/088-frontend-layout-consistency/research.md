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

⚠️ **上一条建议在 T040 执行时被证伪了一半，已订正**（2026-09-15）：那条建议的后半句
「该用『哪些文件还在白名单里』」对 R2 **够不着**——R2 的 `allowed` 是 `null`，
**它根本没有白名单**，没有条目可当出口判据。而前半句的担忧（数字因迁移而掉，
不等于真还债）**是对的**，只是有办法当场分辨：

| 读数 | 真还债（本项的实际情形） | 迁移假掉 |
|---|---|---|
| 待还 | 22 → **0** | 也会降 |
| **候选点** | 55 → **55（不变）** | **同时降**（`FormModal` 不含 `<Modal` 子串，退出分母） |

⇒ 可用判据订正为：**待还归零 \*且\* 候选点不变**，才能断言"22 处是在原地被修掉的、
没有一处是靠换控件悄悄离开分母"。T040 走的是 `width`（不换控件），故两个数一降一平，
正是这一行。这条比原建议更可执行，也已写进 `tasks.md` 的 T040 条目。

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

### 11.7 创建弹窗的宽度：520（antd 默认）→ **640**（`md`）——**补记**（2026-09-13）

**这是一处 +120px 的可见变化，§11.6 只记了作废弹窗的 520 → 480，把它漏了。**
补记于此，因为它同样进 T038 的验收清单。

原 `<Modal>`（`f9f2f57:frontend/src/pages/invoices/InvoiceListPage.tsx:207-215`）**没有 `width`**，
吃 antd 默认的 520 → `FormModal size="md"` = 640。**不是随手取档**：
`FormGrid` 在这个弹窗里要排出两列，需要可用宽 ≥ `2 × minItemWidth` = 512，
即弹窗宽 ≥ 560。520 档下可用宽只有 472 ⇒ **只会排出 1 列**，5 个字段竖着堆，
"紧凑"这个决策方向当场落空。所以 `md` 是这页能成两列的**最低档**，
不是"640 是事实上的默认档"（§2.4）这条统计的顺带结论。

两处宽度变化并列如下，供验收时对照：

| 弹窗 | 改前 | 改后 | 档位 | 理由 |
|---|---|---|---|---|
| 创建（开票） | 520（antd 默认） | **640** | `md` | 两列栅格的最低档（512 + 48 = 560） |
| 作废 | 520（antd 默认） | **480** | `sm` | 四档里最接近原值的一档（§11.6） |

⇒ 同一页两个弹窗**一个变宽一个变窄**，这是有意为之，不是取档不一致。

---

## 12. P2 样板页 2/4：`TagListPage`（T032/T033，2026-09-13）

### 12.1 T032 是**零视觉差异**的（与 T030 不同，如实记下）

T030 顺手删掉了 `Row` 的 `marginBottom:16` 与一个手搓的 `<div style={{height:16}} />`，
所以那一次有**可归因的视觉变化**（统计行到表格 32px → 16px，见 §11.1）。本页没有：
根节点下**只有 `ProTable` 一个 DOM 子元素**——`Modal` 经 portal 挂到 `body`，
**不是本容器的 DOM 子节点**（同 §11.1 里 `.page-stack` 的两点非显然之处之①）。
单行 grid 上 `row-gap` 无处生效，且本页本来就没有可删的间距。

⇒ 这一次 T032 是**纯约定性**改动（1 文件 +2/−2），目的是让 P3 铺开时"页级外壳"有统一形态。

**新增一条留给 T038 的验收观察项**（**未验证，不作为结论**）：`display: grid` 让子元素成为
grid item，其行内轴的自动最小尺寸默认是 `auto`，而 block 布局下不是。理论上若某子元素的
min-content 宽大于容器，grid 会整体撑破而不是内部滚动。本页与 `InvoiceListPage` 的 `ProTable`
内部 `.ant-table-content` 带 `overflow-x: auto`，按规范滚动容器的自动最小尺寸为 `0`，
故**预期**无影响——但 jsdom 的 `cssstyle` 根本不反映 `overflow`/`gridTemplateColumns` 的
计算结果，**这条只能靠肉眼看**。它同时影响 P3 要铺开的 53 个含 `ProTable` 的文件，
所以在 T038 一次看清比在 P3 逐页踩便宜。

### 12.2 T033 只做 `FormModal`，**刻意不做 `FormGrid`**

tasks.md 的 T033 文案里没有 `FormGrid`，这是对的，不是漏写。栅格算一遍就清楚：

- 本页弹窗原先没设宽度 → antd 默认 520 → 四档里取最接近的 **`sm` = 480**；
- 可用宽 ≈ 432px；`FormGrid` 的 `minItemWidth` = `labelWidth(96) + MIN_FIELD_WIDTH(160)` = **256**；
- 432 / 256 = 1.68 ⇒ **只有 1 列**。

一列的栅格是个空动作，所以本页不进 `FormGrid`。**T033 因此是 P2 里唯一一次纯粹验证
`FormModal` API 的提交**（plan 的意图原文：「先用它把 API 在最简单的情形上验证」），
后面三页才有栅格。

### 12.3 标签宽度：`80px` → `96/112`（本页唯一肉眼可见的尺寸变化）

`80px` 是全库 5 种 `labelCol` 定宽里**最窄**的一个（§2.2：`100px`×11、`90px`×6、`110px`×2、
`80px`×2、`70px`×1）。改成 `useFormMetrics().labelWidth` 后中文 **96** / 英文 **112**，
即标签栏比原先**宽 16px（中文）/ 32px（英文）**，输入区相应变窄。这是决策 2（统一标签宽度）
在本页的落地，属**验收项**。

测试侧的钉法值得记一笔：断言读的是 `.ant-form-item-label` 的 **style 属性字符串**，
而不是 `getComputedStyle`。理由有二——① antd 把 `labelCol` 落到
`<Col {...mergedLabelCol} className="ant-form-item-label">`（`node_modules/antd/lib/form/FormItemLabel.js`
末尾确认），属性字符串是它**直接写上去**的，如实；② jsdom 的 `cssstyle` 不反映 `flex` 这类
简写的计算结果。实测该属性值是 **`flex: 0 0 96px;`**——`parseFlex` 把 `'96px'` 展开成了
`0 0 96px`，所以断言写 `toContain('96px')` 而不是等值比较。

**证伪力已实测**：把 `metrics.labelWidth` 临时改回硬编码 `'80px'` 再跑，
**只有第 1 条用例失败**、其余 4 条仍绿，失败信息为
`expected 'flex: 0 0 80px;' to contain '96px'`；还原后 11/11 绿，无探针残留。

### 12.4 顺带收掉 `saving` state，并修掉一个**此前就存在**的裸 rejection

`confirmLoading={saving}` → `FormModal` 内部用 `submitting` 接管，页面的 `saving` state
（1 个 `useState` + 2 处写入）整体删除。

另有一处**改前就存在**、但值得记下来的问题：`onOk={() => void onSave()}` 里
`onSave` 的第一行是 `await form.validateFields()`，校验失败时它会**抛错**，
而这个 `void` 调用没有任何 catch ⇒ 一个无人接管的 promise rejection（控制台可见，功能无碍）。
换成 `FormModal` 后这条路径变成 `await onSubmit()`，若照样抛出则由 `handleOk` 的
`try/finally` 原样传出去——`FormModal` 是**刻意不吞异常**的（见其文件头）。
所以按 T031 的同一条处置，把校验 rejection **就地吃掉**：

```ts
const values = await form.validateFields().catch(() => undefined)
if (!values) return
```

### 12.5 决定**不**在本提交里动那个色块 `Col`（R3 命中点）

R3（表单内 `<Col>` 不得只写 `span`）在本页命中 `:141` 的 `<Col key={c} span={2}>`——
颜色选择器那 10 个色块。**它在 P2 里保持原样**，理由不是懒：

- 它是**全宽项**，`FormGrid` 的正确处置是"留在栅格之外作兄弟节点"，所以它**本来就不该**
  进 `FormGrid`；而 R3 给的修复建议（"换成 `FormGrid` 或至少补断点"）对这一处**是错的**。
- 它确实有真实缺陷：`span={2}` = 1/12 宽，320px 弹窗里每个色块约 20px，`Tag` 会被压扁。
  但正确修法是**换成一个 `flex-wrap` 的色块行**（10 个 Tag 自动换行、每个有最小宽度），
  这是一次**结构变更**，不是补 `xs/sm/md/lg` 能解决的。
- 它落在 T045（"R3 从 `--strict` 翻成 error，96 处销账完毕后"）的口径里，
  把它们混进 P2 会让"样板页的视觉差异"与"R3 的 96 处销账"两个可归因的变化纠缠在一起。

⇒ 记给 T045：**这 96 处里至少有一处（本页）不能用 `FormGrid` 修**，
P3 铺开时不得机械替换。本页仍在 R3 名单上（R3 计数 96 未变），这是预期状态。

### 12.6 `check-ui.mjs` 的三项读数变化

| 规则 | 改前 | 改后 | 说明 |
|---|---|---|---|
| **R2**（`--strict`，承载表单的 Modal 必须定宽） | **24 处** | **23 处** | 本页那个无宽度弹窗销账。**与 §11.2 是同一条机制**：R2 扫的是 `<Modal` 这个字面量，而 `scanTagEvents` 有"标签名必须整体匹配"的守卫（`<FormModal` 里根本不含 `<Modal` 这个子串） |
| R3（`--strict`，表单内 Col 只写 span） | 96 处 | **96 处**（未变） | 见 §12.5 |
| 总问题数（`--strict`） | 120 处 | **119 处** | |

⇒ **再次印证 §11.2 的结论**：P3/T040 的出口判据不能写成"R2 还剩多少处"，
必须写成"**哪些文件还在 R2 名单上**"——否则每迁移一页，"待还债务"的分母自己就缩一格，
读数会一直"看起来在还"。

---

## 13. P2 样板页 3/4：`ProductListPage`（T034/T035，2026-09-13）

### 13.1 T034 是零视觉差异（同 T032）

根 fragment → `div.page-stack`，1 文件 +2/−2。根节点下只有 `ProTable` 一个 DOM 子元素
（`Modal` 经 portal 挂到 `body`），单行 grid 上 `row-gap` 无处生效，也没有可删的间距。

### 13.2 **本页是这次样板里唯一需要证明"没有回退"的一页**——结论：没有回退（有一处 16px 视口带的差异）

本页此前是本仓库自己的响应式参考实现（`<Col xs={24} sm={12}>` × 6，
全库仅有的 6 个响应式表单 Col）。plan 的判据写得很硬：
「若 `FormGrid` 让 ProductListPage 回退，**那是设计错了，不是页面错了**」。
所以这里把两种算法的分列条件**算出来**比一遍，而不是"看着一样"。

两种机制的分列条件（弹窗 `md`=640、`Modal` 的两侧内边距各 24px、antd 对 `.ant-modal`
的 `max-width: calc(100vw - 32px)` 夹取）：

| 机制 | 分列依据 | 2 列的条件 |
|---|---|---|
| `<Col xs={24} sm={12}>` | **视口**断点 | `vw ≥ 576`（`sm`） |
| `FormGrid` auto-fit | **容器**宽度 ≥ `2 × minItemWidth` = 512 | 容器 = `min(640, vw − 32) − 48` ⇒ `vw ≥ 592` |

⇒ 两者**只在 `576 ≤ vw < 592` 这 16px 视口带里不同**：Col 版排 2 列，`FormGrid` 退成 1 列。

**这一带里 Col 版给的 2 列是坏的**：容器只有 496–511px，两列各 240–247px，
扣掉 96px 标签后**控件仅 144–152px**，已经低于 `MIN_FIELD_WIDTH`（160，"能看清 12~15 个字符"的
经验下限）——日期与金额控件在这个宽度上会被截断。`FormGrid` 的 auto-fit **拒绝**在这里分列，
正是 §`FormGrid.tsx` 文件头说的"列数由容器推导、不由视口推导"的**设计语义**，不是回退。

**如实记下这是"差异"**：在那 16px 带里列数由 2 变 1，**属验收项**（§13.4 清单）。
判据仍是 plan 那句——**2 列在可用宽度下是否仍然拿到**：是（`vw ≥ 592` 一律 2 列，
桌面档 640 弹窗恒 2 列）。

**纵向间距无变化**：原 `Row gutter={16}` 只有横向 gutter，纵向来自
`Form.itemMarginBottom: 12`；`FormGrid` 的 `rowGap` 刻意是 0（见 `formGridStyle.ts`），
纵向同样来自那 12px。6 个字段 = 3 行，行距逐字相同。

### 13.3 R6 白名单复核：条目由 **2 处收成 1 处**，"不是缺陷"这个判断对了一半

T034 的 tasks 原文要求"该页 `Currency`/`Price (CNY)` 两处在册，届时一并复核"。复核结论：

| 处 | 原理由说"不是缺陷，是单位/币种示例" | 复核结论 |
|---|---|---|
| `placeholder="Currency"` | ✗ 判断错了 | 它是**字段提示词**（一个英文单词），不是 `sales@corp.com` 那种"该填什么**格式**"的示例；而且**同一个弹窗里另外 3 个 placeholder 早就走了 `t()`**。**已修**：新增键 `pages.product.list.priceCurrencyPlaceholder`（zh `币种` / en `Currency`），白名单 count 2 → 1 |
| `placeholder="Price (CNY)"` | ✗ 判断错了，且性质更重 | **这不是 i18n 问题，是语义问题**：该行选的是**非基准币种**（`fetchCurrencies` 过滤掉 `isBase`），而 `setProductPrice(productId, currencyCode, price)` 存的正是**所选币种**的价 ⇒ 在 USD 行里提示"按 CNY 填"是**误导性文案**。正解是"按所选币种填写"一类，但**那是要拍板的业务文案**，本批次不改 |

⇒ 待裁决项，与 plan 末节那 6 件事并列交给你（T038）。

### 13.4 其余改动与差异清单（验收对照）

| # | 改动 | 差异 |
|---|---|---|
| 1 | `Modal width={640}` → `FormModal size="md"` | **无**（640 = `md` 档原值） |
| 2 | `labelCol` 定宽 `110px` → `useFormMetrics()` 的 96/112 | 标签栏中文窄 14px、英文宽 2px（110 → 96 / 112） |
| 3 | 6 个字段：`<Col xs={24} sm={12}>` ×6 → `FormGrid` | 见 §13.2：`vw ≥ 592` 恒 2 列；`576 ≤ vw < 592` 由 2 列变 1 列（那一带原本的 2 列控件仅 144–152px，不可用） |
| 4 | 删 `saving` state（1 个 `useState` + 2 处写入） | 无（提交中状态由 `FormModal` 的 `confirmLoading` 接管） |
| 5 | `onOk={() => void onSave()}` → `onSubmit={onSave}` | 无（顺带修掉一处**改前就存在**的裸 rejection：`onSave` 首行的 `validateFields()` 校验失败会抛错，原调用无 catch） |
| 6 | 新增 `TagListPage` 那一条同样的取消键默认值 | **新出现**：弹窗左键由英文 `Cancel` 变 `common.button.cancel` |

### 13.5 多币种子区块**刻意留在 `Row`/`Col`**（不进 `FormGrid`）

那条 `{editing && …}`（`:298-355`）保持原样，三条理由：

1. **它不在 `<Form>` 里**。`</Form>` 在它之前就闭合了——它是 `FormModal` 的**第二个子节点**，
   与 `<Form>` 并列，不是表单的一部分。`FormGrid` 的设计前提是"包住 `Form.Item` 并排在
   `labelCol` 的框架内"，这里既没有 `Form.Item` 也没有 `labelCol`（那两个控件直连 state，
   不经表单）。（顺带一提：这也就不会碰到 `FormGrid.tsx` 使用纪律的第 2 条
   "同一 `Form` 内不得混用 `FormGrid` 与 `<Col>`"——那条管的是 `Form` **内部**。）
2. **它已经是响应式的**（`xs={24} sm={9/9/6}`），不是 R3 的 96 处之一，没有欠账要还。
3. 它的版式是"2 个控件 + 1 个移除按钮"的**三元组**，不是"成对字段"；
   9/9/6 这个比例是有意的（移除按钮占 25%），换 auto-fit 会把它变成均分三列，
   那是**改设计**，不属于"布局规范统一"。

⇒ 该子区块的 `Currency` / `Price (CNY)` 两处 placeholder 已在 §13.3 处理。


## 14. P2 样板页 4/4：`CustomerListPage`（T036/T037，2026-09-13）

两提交：`T036` 外壳、`T037` 表单原语。这一页是 P2 四页里**唯一的两弹窗页**，
也是唯一一页表单**完全没有断点**的（R3 名单里有它 **7** 处，见 §14.5）——所以它是四页里
唯一"改完真的修掉一个可用性缺陷"的样板页，另外三页都只是**保持**既有行为。

### 14.1 T036 的净视觉变化是**零**：手搓 spacer 与 `row-gap` 同值

本页原生结构是 `<Space>`（视图切换）+ 手搓 `<div style={{ height: 16 }} />` + `ProTable`。
删掉那个 spacer 换成 `.page-stack` 的 `row-gap: 16px` 后，间距**逐像素相同**——
删掉的只是一个不再需要的 DOM 占位元素。

与 T030 的差别要记住：T030 删的是"`Row` 自身 `marginBottom:16` **再叠**一个手搓 16px spacer"
里的一层，故间距净减 16px（**可归因**）；本页那条 spacer 本来就是**唯一的**那 16px。
**同样是"删一个 spacer"，一页是 32→16，另一页是零**——所以这类改动不能按动作归因，
只能按算出来的数值归因。

`index.css:127-130` 的 `.page-container > div > .ant-card { margin-bottom: 16px }` 与 `row-gap`
**叠加**（§11.1 第 2 条）：本页 `ProTable` 也是最后一个 DOM 子元素
（两个 `Modal` 经 portal 挂到 `body`），故只表现为页面底部多 16px，与 T030 同形，无害。

### 14.2 T037 是四页里唯一"真的修掉一个可用性缺陷"的样板页

另外三页的 `FormGrid` 转换是**保持**既有行为（`TagListPage` 没进栅格、`ProductListPage`
本来就是全库唯一的响应式实现）。本页不同，它的 7 个字段被 `<Row gutter={16}>` +
7 个 `<Col span={12}>` 包着，**写死两列、无任何断点**：

| 视口 | antd 弹窗实宽 | 可用宽（扣 24×2 内边距） | 改前（`span={12}`） | 改后（`FormGrid`·`minItemWidth=256`） |
|---|---|---|---|---|
| 1440 / 1024 | 640 | 592 | 2 列（各约 288） | 2 列 |
| 768 | 640 | 592 | 2 列 | 2 列 |
| 375 | `calc(100vw - 32px)` = 343 | ~295 | **2 列，各约 140px** | **1 列** |
| 320 | 288 | ~240 | **2 列，各约 112px** | **1 列** |

改前 375/320 下每列 140/112px 都低于 `MIN_FIELD_WIDTH=160`——这是 §2.3 量出的
"96/102 个表单 Col 写死 span" 那类缺陷的**标准形态**，不是本页独有。
所以它同时是 T038 验收里"缩到 375px 看弹窗"这一项的**预期观察点**。

### 14.3 第二个弹窗（批量转移）**一并换了 `FormModal`**：这是一处可归因的尺寸变化

`520 → 480`（antd 默认 → `sm`），−40px。取 `sm` 的理由与 T031 的作废弹窗相同：
纵向单字段表单，432px 可用宽足够。同时它还清掉了两笔非尺寸的债：

- **R2（表单弹窗必须显式定宽）**：这个 `Modal` 此前根本没设宽度，是 23 处之一；
- **`confirmLoading`**：此前没有——提交期间 OK 按钮可重复点，而
  `batchTransferCustomers` **不带幂等键**（不是"点两次无害"的接口）。
  换成 `FormModal` 后由 `onSubmit` 的 promise 驱动 loading。

⚠️ 这一条**不能**被当成"顺手优化"略过：它是本页唯一会改变像素的改动，
写在提交信息里才不会让将来的人去别处找原因。

### 14.4 `CustomFieldFormItems` 与备注框**刻意留在栅格之外**（`FormGrid` 纪律第 1 条）

两项都是**全宽项**：自定义字段的数量与宽度由后端配置决定、备注是长文本。
放进栅格会让它们悄悄退化成 N 列里的一列——**这是 `FormGrid` 最可能的误用**，
而它不会让任何既有断言转红。

本页因此是四页里**唯一能同时演示"该进栅格的进、该留外的留"**的一页，
`CustomerListPage.form.test.tsx` 第 2 条把它钉死为**结构断言**：
`grid.children` **长度恰好为 7**（不多不少），且备注框在弹窗里但不在栅格内。
**实测过它真会红**：把备注 `Form.Item` 挪进一个新 `<FormGrid>` 后，第 1、2 条转红
（`getByTestId('form-grid')` 命中多个），其余 5 条仍绿。

### 14.5 门禁读数：R2 `23→22` / R3 `97→90`，两个分母**同步**缩小

初稿这里写的 R3 是 `96→90`（各减 6），**那是错的**：旧版这一页有 **7** 个 `<Col span={12}>`
（`:415/420/425/430/435/440/445`），而候选点只减了 6，差 1 不解释清楚就等于编数。
于是用**隔离测量**而不是推算——把 T036 版这一页另存为一个副本文件（`__probe_old.tsx`）放进
`src/`，让 `ui:check` 把它当一个独立文件统计，再减去不放入时的读数：

| 规则 | T037 前 | T037 后 | 说明 |
|---|---|---|---|
| R2 待还 | 23 处 / **58 个候选点** | **22 处 / 56 个候选点** | 两个 `Modal` 都换成 `FormModal` ⇒ **两个**都退出 R2 的分母；但只有原本没定宽的那个是命中 |
| R3 待还 | **97 处 / 99 个候选点** | **90 处 / 92 个候选点** | 7 个 `<Col span={12}>` 全数消失 ⇒ **命中与候选点各减 7**（隔离测量：副本文件的贡献恰为 7/7） |
| 白名单合计 | 54 处 | **54 处** | 本页从未在册，双向校验确认未误判 |

R2 的"分母比分子多减 1"再次印证 §11.2 的结论：**R2 的欠账数下滑快于真实还债速度**，
故 P3 的出口判据必须按**文件**而非按计数。R3 不受此影响——`<Col span>` 消失就是真的还了债，
`FormGrid` 不是 R3 的候选点类型，没有"换成 `FormGrid` 后不再被量到"这回事。

⚠️ **一条必须记下的对账失败**：§11.2 记的 T031 前后是 `96 处 / 102 个候选点`，
而本次隔离测出的 T037 前是 `97 处 / 99 个候选点`——**差 +1 命中 / −3 候选点**，
且它**不能用本批次的任何一次提交解释**（T030/T032/T034 只动外壳、T033 无表单 `<Col>`、
T035 减 6 个**带断点**的 Col ⇒ 应为 −6 候选点、命中不变）。当时 §11.2 的读数取自哪一棵树
已不可考（本工作区曾有过兄弟 agent 的变异自验期，探针文件会瞬时改变这些计数）。
**故：R3 的绝对计数在跨小节之间不可比，只有"同一次隔离测量出来的增量"可用。**
这与 P3 出口判据按文件而非按计数的既定结论方向一致，无需另行处置。

---

## 15. 真实浏览器实测：§12.1 的 watch item 证伪，另挖出一个**先于本批次**的窄屏缺陷（2026-09-13）

P2 八次提交完成后、T038 视觉验收之前，用一次**临时 Playwright 探针**在真机引擎里补量了三件
jsdom 结构上测不到的事（`cssstyle` 不反映 `gridTemplateColumns` / `overflow` 的计算结果，
也读不到真实布局）。探针文件按纪律**跑完即删**，脚本原文见 §15.5 以便复现。

### 15.1 §12.1 的担心不成立：文档级横向溢出在 40 组组合下恒为 0

| 组合 | 结论 |
|---|---|
| 4 页 × 5 视口（1920/1440/1024/768/375）× zh/en = **40 组** | `documentElement.scrollWidth − clientWidth` **全部 = 0** |

即"`display: grid` 让子元素拿到 `min-content` 的自动最小尺寸、把页面撑破而不是内部滚动"**没有发生**。
归因不是"没到出事的宽度"，而是**页面本来就有一层自己的横向滚动容器，且它把 grid 的自动最小尺寸也一并压掉了**：

- `App.tsx:693` 的 `<Content className="page-scroll" style={{ overflow: 'auto' }}>` 是横向滚动容器。
  flexbox 规定 **flex item 的 `min-width: auto` 在其 `overflow` 不为 `visible` 时解析为 `0`** ——
  于是这一层既承担滚动的，也终止了"内容最小宽"向上传导；`display: grid` 的自动最小尺寸再大，
  也只能让 `main` 自己的 `scrollWidth` 变大（375px 下量到 708），不会传到文档。
- 因此 §12.1 的 watch item **可以销案**：在这套外壳下，ProTable 页面不会被 `page-stack` 撑破。
  （同时也说明 P3 铺开时不必为它准备断点兜底。）

### 15.2 同一批读数挖出的真缺陷：**≤ 767px 时内容区宽度为 0**（全站，不只是样板 4 页）

多路由 × 多宽度复测（`/stats` 是 088 **从未碰过**的对照页）：

```
inner=1024 | /stats      | 主轴=row    | 内容容器宽= 824 | main 宽= 824 (client= 824) | Sider=  200x852 | 文档溢出=0
inner= 768 | /stats      | 主轴=row    | 内容容器宽= 568 | main 宽= 568 (client= 568) | Sider=  200x852 | 文档溢出=0
inner= 767 | /stats      | 主轴=column | 内容容器宽=   0 | main 宽=  24 (client=  24) | Sider=  200x200 | 文档溢出=0
inner= 500 | /stats      | 主轴=column | 内容容器宽=   0 | main 宽=  24 (client=  24) | Sider=  200x200 | 文档溢出=0
inner= 375 | /stats      | 主轴=column | 内容容器宽=   0 | main 宽=  24 (client=  24) | Sider=  200x200 | 文档溢出=0
```

`/invoices`、`/customers` 逐行相同。断点是**阶跃**的：`App.tsx:300` 的 `isMobile = window.innerWidth < 768`
一翻，`App.tsx:660` 的 `flexDirection` 就从 `row` 变 `column`，内容区宽度从 824/568 直接掉到 **0**。

**成因是三条规则的合成**（`0` 不是我们的 CSS 写的，是 antd 写的）：

```css
/* node_modules/antd —— 内容容器被**写死** width: 0，指望 flex-grow 在行向里撑开 */
:where(…).ant-layout.ant-layout-has-sider > .ant-layout,
:where(…).ant-layout.ant-layout-has-sider > .ant-layout-content { width: 0px; }
```

1. 该规则给出一个**确定**的 `width: 0`，靠 `flex: 1 1 auto` 在**行向**里长回可用宽 —— 768 及以上正是如此（568/824 ✓）；
2. `App.tsx:660` 在窄屏把主轴改成 `column` ⇒ `flex-grow` 作用于**高度**，宽度的确定值就停在 `0px`；
3. `main`（`Content`）的 `clientWidth` 于是只剩 `padding: 12px × 2 = 24`，内容盒 **0 宽** ——
   `.page-container` / `.page-fade` 跟着是 `0`（`page-container` 只是 `width: 100%`），
   内部所有内容被 `overflow: auto` 裁进一个 0 宽的可见区 = **页面看上去是空的**。

同一个 `column` 还顺带把 Sider 弄坏了：antd 给 `<Sider>` 写的是 `flex: 0 0 200px` +
`width/min-width/max-width: 200px`，用于行向；列向里 `flex-basis: 200px` 变成**高度**
（页内 `style={{ height: 48 }}` 被 `flex-basis` 盖过）⇒ 实测 Sider 是 **200×200 的方块**，
右侧 175px 空着。

**归属（重要，别记到 088 头上）**：`git log -S "flexDirection: isMobile ? 'column' : 'row'" -- src/App.tsx`
命中 **`c8bc7f8`（016-system-enhancement「自定义字段/通知中心/数据导出/移动端适配」）**，
现行行 blame 为 `2026-08-30`。**088 全程未改 `App.tsx`**（P1 只动 `LocaleProvider` 与 `components/ui/`，
P2 只动 4 个页面 + `index.css` 的 `.page-stack`）。即：**那次「移动端适配」提交让窄屏彻底不可用**，
且此后一直如此。

**为什么 72 个测试文件一个都没抓到**：jsdom 的 `window.innerWidth` 默认 1024 ⇒ 永远走 `row` 分支，
而这条缺陷**只在 `column` 分支存在**。它同时解释了为什么这条只可能由真机验收发现。

**处置**：**本批次不改**——`App.tsx` 不在 P2 授权的 8 次提交范围内，且这是全站外壳的行为变更，
应由你决定是否立独立批次（最小改法是把列向下的两个内联值补齐：内容容器加 `width: '100%'`
覆盖 antd 的 `0`，`Sider` 的 `flex-basis` 改成 48px 并放宽三个 width 约束）。
但 **T038 的 375px 那一档必然撞上它**，届时看到空白页即是本条，不是样板页改坏了。

### 15.3 `FormGrid` 与标签宽度的真机读数（补 jsdom 的两个盲区）

| 读数 | 真机结果 |
|---|---|
| 标签宽 `useFormMetrics` | zh **96px** / en **112px**，逐像素生效（jsdom 只能读 `style` 字符串，读不到布局） |
| 创建弹窗宽 | invoices/products/customers **640px**、tags **480px**，且以**内联** `style="width: 640px"` 落地 |
| 弹窗宽在 375px | 实测 **359px** = antd 的 `calc(100vw − 16px)` 夹取；内联仍是 `640px`（夹的是 `max-width`） |
| 栅格轨道数 | 容器 **592px**（md 档）⇒ **2 列**；容器 **311px**（375px 视口）⇒ **1 列** ✓ |
| tags 页弹窗 | **无** `[data-testid="form-grid"]` —— **设计使然**：`TagListPage.tsx:134-155` 是 2 个纵向
`Form.Item` + 一个颜色选择器 `Row/Col`，**没有成对字段**可进栅格（使用纪律第 1 条），
但它一样用了 `FormModal` + `useFormMetrics`（标签宽 96/112 同样量到） |

375px 下的 `311px → 1 列` 是本批次**唯一能实测到"修掉了真缺陷"**的地方：
同一视口下老的 `span={12}` 会给出约 140px 的两列（低于 `MIN_FIELD_WIDTH=160`），
`FormGrid` 的 `auto-fit` + `minmax(min(100%, 256px), 1fr)` 退成一列。

### 15.4 由此对 T038 验收清单的两处修订

1. **375px 那一档要分开看**：窄屏内容区 0 宽（§15.2）会先于任何样板页的差异呈现，
   验收 375px 时**先记录这一条的状态**（现状=空白），再谈样板页。
2. §12.1 的 watch item **从"待肉眼确认"改为"已实测证伪"**，验收清单里可以划掉。

### 15.5 探针脚本（跑完即删，此处留原文以便复现）

以 `frontend/e2e/__measure.spec.ts` 运行（`npx playwright test __measure`），跑完即 `rm`；
`/stats` 是对照页，`login` 来自 `e2e/helpers/login.ts`：

```ts
import { test } from '@playwright/test'
import { login } from './helpers/login'

test('窄屏内容区宽度', async ({ page }) => {
  test.setTimeout(180_000)
  await login(page)
  const rows: string[] = []
  for (const w of [1024, 768, 767, 500, 375]) {
    for (const path of ['/stats', '/invoices', '/customers']) {
      await page.setViewportSize({ width: w, height: 900 })
      await page.goto(path)
      await page.waitForTimeout(2200)
      const r = await page.evaluate(() => {
        const hasSider = document.querySelector('.ant-layout-has-sider')!
        const wrapper = hasSider.children[1] as HTMLElement
        const main = wrapper.querySelector('main') as HTMLElement
        const sider = hasSider.children[0] as HTMLElement
        return {
          inner: window.innerWidth,
          dir: getComputedStyle(hasSider).flexDirection,
          wrapperW: Math.round(wrapper.getBoundingClientRect().width),
          mainW: Math.round(main.getBoundingClientRect().width),
          sider: `${Math.round(sider.getBoundingClientRect().width)}x${Math.round(sider.getBoundingClientRect().height)}`,
        }
      })
      rows.push(`${r.inner} | ${path} | 主轴=${r.dir} | 内容容器宽=${r.wrapperW} | main=${r.mainW} | Sider=${r.sider}`)
    }
  }
  console.log(rows.join('\n'))
})
```

⚠️ 一条**踩坑记录**：第一次写这个探针时用 `getByRole('button', { name: '开票', exact: true })` 点不到任何按钮，
**全部弹窗读数为 null**。原因是 antd 的图标渲染成 `<span role="img" aria-label="plus">`，
它的 `aria-label` 会并进按钮的可访问名 ⇒ 实际名字是 `"plus 开票"`。
**显式按钮名一律用正则、不用 `exact`**（既有 e2e 用例也都是正则）——否则报错形态是"什么都没找到"，
很容易被误读成"页面上没有这个按钮"。

---

## 16. T038 另两条"只能肉眼判断"的验收项：主色与紧凑密度的实测读数（2026-09-13）

同一次真机探针（跑完即删）顺手把验收清单里的**色差**与**密度**也量了。方法上有一条纪律：
**token 的落地值从 CSSOM 规则读，不从 DOM 读**——DOM 会被页面级覆盖污染（§16.4 是一例现成的虚惊）。

### 16.1 主色：半 Indigo 半蓝的分裂已经消失，唯一残留在页头 logo

| 表面（接入前是否跟着 Indigo） | 实测计算值 |
|---|---|
| `.ant-btn-primary` backgroundColor（前 ✅ CSS 覆盖） | `rgb(99, 102, 241)` |
| `.ant-pagination-item-active` border + 内层 `a` 颜色（前 ✅） | `rgb(99, 102, 241)` / `rgb(99, 102, 241)` |
| `.ant-menu-item-selected` backgroundColor（前 ✅） | `rgb(238, 242, 255)`（`#eef2ff`，Indigo-light） |
| **`.ant-checkbox-checked .ant-checkbox-inner`**（前 ❌ antd 蓝） | `background-color: rgb(99, 102, 241)` |
| **`.ant-select-focused … .ant-select-selector`**（前 ❌） | `border-color: rgb(99, 102, 241)` |
| **hover 态**（前 ❌） | `rgb(145, 151, 255)`（由 `#6366f1` 生成的派生色，说明确实由 token 生成而非 CSS 手写） |

"前 ❌"那三行是 CSSOM 里读到的**规则原文**（元素不必处于选中态），它们此前是 `#1677ff`：
`colorLink` 与 `colorPrimary` 一接，链接色/聚焦环/选中态这一整片就跟着回来了。

**唯一还看得见的 antd 蓝**：`App.tsx:627` 的页头 logo（`<TeamOutlined style={{ fontSize: 20, color: '#1677ff' }} />`）
与 `:651` 的头像底色。元素级扫描在**每一页**都恰好命中 3 个元素（`span.anticon` + `svg` + `path`，`color` 逐级继承），
即这两处字面量。它们是 R1 白名单里"租户主题兜底/演示数据"之外的**品牌色字面量**，
接入 theme 后它们成了全站唯一一块蓝 → **T038 看样板页时若觉得某处蓝得不搭，就是它**；
要不要改成 `palette.primary` 是 P4 的清扫项（`App.tsx` 不在 P2 授权范围内，本次未动）。

`/stats` 另有 13 个 antd 蓝元素（`DashboardPage` 的 7 处字面量，`plotly`/自绘色），
**不在本批次范围**，也从不在样板 4 页里。

### 16.2 紧凑密度：四条 token 逐条落地

| token | 落地读数（CSSOM 原文 / DOM） |
|---|---|
| `Form.itemMarginBottom: 12` | `.ant-form-item → margin-bottom: 12px`；弹窗内 DOM 实测 `12px` |
| `Table.cellPaddingBlockSM: 6` | `.ant-table-small … td → padding: 6px 8px`；DOM `6px 8px`（默认是 12px 8px） |
| `Card.headerHeight: 44` | `.ant-card .ant-card-head → min-height: 44px`（默认 56） |
| `Card.paddingLG: 16` | `.ant-card .ant-card-body → padding: 16px`（默认 24）；`ant-card-head` 的 `padding: 0 16px` 也由它推导，**独立印证该别名 token 在组件名下确实生效** |
| `fontSize: 13` | `body` 13px、`.ant-form-item` 13px、`.ant-modal .ant-modal-body` 13px、`.ant-form-item-label > label` 13px |
| 刻意**未**设 `controlHeight` | `.ant-form-item-control-input → min-height: 32px`、`label → height: 32px` = antd 默认 32 ⇒ 文档说的"写它等于没写"得到实测支持 |

### 16.3 两条新事实（都会影响"看起来紧不紧"的判断，写下来免得当成 bug）

1. **卡片标题仍是 14px**：`.ant-card .ant-card-head → font-size: 14px`、`.ant-card-meta-title → font-size: 14px`。
   antd 的卡片标题吃的是 `fontSizeLG`，不是 `fontSize` ⇒ 所谓"全站 13"实际是**正文 13 / 卡片标题 14**。
   要不要把标题也压到 13（或干脆保留这层层级）属视觉判断，**留给 T038 拍板**，本批次未动。
2. **ProTable 的卡片不受 `Card` token 管辖**：`.ant-pro-card .ant-pro-card-body → padding-block: 16px; padding-inline: 24px`
   来自 ProComponents 自己的主题；本页 DOM 实测 `.ant-pro-card-body padding=0px 24px 16px`。
   ⇒ 在 4 个样板页里，"卡片正文 24→16"**并不会**生效（它本来就是 16），
   这条 token 的受众是**裸 `<Card>` 的页面**。P3 铺开时不要拿样板页的观感去推"Card token 生效了"。
3. 页面级 `styles={{ body: { padding: … } }}` 会盖过 token：`DashboardPage.tsx:79` 等 **7 处**都写了
   `padding: '20px 24px'` 或 `padding: 20`。铺开到这类页面时，密度不会跟着 token 变——那不是 token 失效。

### 16.4 一条方法论：token 的落地值别从 DOM 读

第一次量 `/stats` 的 `.ant-card-body` 得到 `20px 24px`，与 theme 里"`Card.paddingLG: 24 → 16`"的声明不符，
看上去像"token 没生效"。追下去是 `DashboardPage.tsx:79` **自己写死**的 `styles={{ body: { padding: '20px 24px' } }}` ——
**不是 token 失效，是我量到了别人的内联覆盖**。改从 CSSOM 读规则原文后，`padding: 16px` 立刻现身。
⚠️ 因此：**"某 token 没生效"这类结论必须用 CSSOM 规则原文佐证**，DOM 计算值只能证明"这个页面上是这样"。

## 17. P2 的四个新测试文件在全量跑时是红的：一次"症状搬了家"的排查（2026-09-13）

P2 收尾时发现：**这四个新文件（`{invoices,tags,products,customers}/*.form.test.tsx`）
单独跑全绿，跑全量就红**，且每次红的条数不固定（先是 `4 failed | 76 passed (80)`，
非覆盖模式一次又只剩 1 条）。本节的结论是**并发超订**，而不是断言写错。
排查里有一次值得记下来的误判，见 17.1。

### 17.1 第一层症状（1000ms 的 `findBy*`）与它为什么不成立

最初的失败形态是 Testing Library 的默认等待：`findBy*` / `waitFor` **只等 1000ms**，
而 `ProductListPage.form.test.tsx` 在负载下整个文件要 37.8 秒（隔离跑 13.6 秒）。
据此我加了 `src/test/timeouts.ts` 的 `HEAVY_RENDER_ASYNC_TIMEOUT = 20000`
（照抄 `src/App.render.test.tsx:98` 的 `SHELL_TIMEOUT` 约定），四个文件各调一次
`configure({ asyncUtilTimeout })`。

**结果：失败点没有消失，只是换了位置**——变成
`Error: Test timed out in 20000ms.`，即仓库自己的 `vite.config.ts:24` 的 `testTimeout`。

这条订正的值在于：**"等待上限不够"当时看起来像个解释，其实只是症状**。
一个上限被撞到时，正确的下一步是问"这条用例的**总耗时**是多少"，
而不是把那个上限调大——否则会一路把上限搬到下一个边界上，每一层都像是"再大一点就好"。

### 17.2 根因：默认 worker 池在 16 核机器上是超订的

同一棵冻结的树、同一条命令（`vitest run --coverage`），只改 worker 池上限：

| 量 | 默认池（16 核 → ~16 worker） | `--maxWorkers=4 --minWorkers=1` |
|---|---|---|
| 结果 | **2 文件 / 6 用例失败** | **80 文件 / 365 用例全绿** |
| 墙钟 | 142.6s | 239.4s |
| 累计 `collect` | 459.2s | 245.5s |
| **累计用例耗时** | **1243.0s** | **622.7s** |
| `products/*.form`（5 条） | 109.8s（**3 条撞 20s 上限**） | 62.7s（12.5s/条） |
| `customers/*.form`（7 条） | 132.7s（**3 条撞 20s 上限**） | 92.1s（13.2s/条） |
| 隔离跑（4 个文件） | — | 3.8s/条 |

**累计用例耗时腰斩**（1243s → 623s）是关键读数：worker 从 ~16 降到 4，
总计算量少了近一半，说明默认池**不是"多跑并行"而是"排队等 CPU"**——
每个 worker 都在跑 jsdom + antd，16 个进程互相抢内存带宽。
墙钟反而变长（142s → 239s）是因为并发度真的降了，而 142s 那次是**带失败**的读数。

失败形态也符合这个解释：**失败条目不固定**（同一条用例这次过、下次超），
且不是"越靠后越容易失败"的累积形态——`customers` 的第 5 条失败、第 6/7 条又过。
这正是"每条用例耗时在 20–25s 之间浮动、正好压在 20s 边界上"的样子。

顺带记两个同类读数（说明这不是我那 4 个文件独有的问题）：
`customers/CustomerListPage.test.tsx` 默认池下 2 条 22.7s、`users/UserManagementPage.test.tsx`
3 条 21.1s；换 4 个 worker 后 `customers/CustomerListPage.perm.test.tsx` 13 条 51.7s、
`App.render.test.tsx` 5 条 33.7s。**仓库里已经有一批"重文件"贴着这条线走。**

### 17.3 两层修复：兜底在本批次，根治要独立提交

**（a）本批次（4 个文件，已改，未提交）**：`src/test/timeouts.ts` 增加
`HEAVY_RENDER_TEST_TIMEOUT = 60000`，四个文件各调一次
`vi.setConfig({ testTimeout: HEAVY_RENDER_TEST_TIMEOUT })`。
60s 相对"最坏观测值 25s"留 2.4 倍余量。**这是兜底**：它让这四个文件在任何 worker
配置下都不再贴边，但没有改善全仓的并发超订。

**（b）根治（`vite.config.ts`，未做）**：加 `maxWorkers`。
⚠️ **必须同时给 `minWorkers`**：vitest 1.6 单独给 `maxWorkers` 会直接抛
`RangeError: options.minThreads and options.maxThreads must not conflict`，
**一条用例都不跑**（我实测撞到过，`Errors 1 error / Test Files no tests / Duration 17ms`）。
这是一次会改变全仓 80 个文件跑法的改动，**必须能与任何 UI 改动分开归因**，
因此不并入本批次——它也正是 plan 里 P0 那条阻塞项预留的分支
（"仍不稳 → 作为一次独立提交加 `maxWorkers`/`fileParallelism` 并复测"）。

### 17.4 一条与 P0 直接相关的结论

P0 要的是"**3 次读数稳定成一个可引用的区间**"。本节说明：
**读数不稳的观察里，至少有一部分与 `src/pages/**` 的并发编辑无关，而是 worker 池超订。**
`vite.config.ts:61-64` 当时把成因猜在"多会话并行改 `src/pages/**`"上——
那个猜测没有被证伪，但**这一条是另一个独立成因，且可复现**。
后续引用"区间"时，必须同时记录 **worker 池是默认还是被限过**，
否则两次读数不可比（同一条命令，142.6s / 1243.0s 与 239.4s / 622.7s 是同一棵树）。

## 18. P0 阻塞项的交付物：P2 之后的覆盖率基线带（3 次读数，2026-09-13）

plan 的 P0 要求"在冻结的、无其它会话触碰 `src/pages/**` 的工作区上连跑 3 次
`pnpm test:coverage`，把读数稳定成一个可引用的区间"。本节是它的结果。
工作区状态：`git status --short` 只有 `M .github/workflows/ci.yml`、`M Dockerfile`、
`M backend/pom.xml`（**并行会话的在飞改动，与本批次无关**）与 `M specs/088-*/research.md`；
`frontend/src/**` 无第三方在写（`ListAgents` 只有 `engineering-consolidation-ci-gates` 一个并行会话，
手势在 JDK 三件套上）。

| # | 调用 | 结果 | statements | branches | functions | lines |
|---|---|---|---|---|---|---|
| 1 | `vitest run --coverage --maxWorkers=4 --minWorkers=1` | 80/80 文件、365/365 用例绿 | 68.21 | 73.71 | 35.86 | 68.21 |
| 2 | `pnpm test:coverage`（默认池） | 全绿 | 68.21 | 73.71 | 35.86 | 68.21 |
| 3 | `pnpm test:coverage`（默认池） | 全绿 | 68.21 | **73.78** | 35.86 | 68.21 |
| — | `pnpm test:coverage`（默认池，第 4 次） | **1 条无关用例失败**（见 §17） | 未打印 | — | — | — |

**可引用的区间**：statements **68.21** / functions **35.86** / lines **68.21**（三次逐字相同）；
branches **73.7 ± 0.1**（73.71 / 73.71 / 73.78）。

三条推论：

1. **新基线 = P1 快照的自然增量**。P1 后是 67.65 / 73.02 / 34.29 / 67.65（见 `vite.config.ts` 的注释），
   P2 后是 68.21 / 73.7 / 35.86 / 68.21。statements +0.56pp、functions +1.57pp，
   与"P2 新增 4 个文件 / 22 条整页渲染用例"同一量级——没有第四个解释需要找。
2. **覆盖率读数与 worker 池无关**。第 1 行（4 worker）与第 2 行（~16 worker）四项里三项逐字相同，
   branches 的差异（73.71 vs 73.78）与第 3 行的差异同量级，而第 2/3 行是同一种调用。
   ⇒ **branches 的小数位抖动是固有的，不是并发产物**；§17 那个"读数不稳里有一部分与并发编辑无关"
   的结论到此可以收紧成：**并发在这里影响的是"红不红"，不是"覆盖率是多少"**。
3. **对 P3 的用法**：P3 每批"与基线区间比"时，branches 只比到小数点后一位；
   statements / functions / lines 可以比到两位。这正好落实 `vite.config.ts` 里那句
   "不要引用单次运行的小数位当论据"。

**⚠️ 一条跨规格的待办（不得由本批次单方面改）**：`vite.config.ts` 的覆盖率注释里写着
"改动此值时必须**同步**更新 `specs/083-engineering-consolidation/data-model.md` §4 的实测记录"。
把本节读数写进 `vite.config.ts` 的注释（P1 时是惯例，见其中"【实测刷新，2026-09-13（088 的 P1 完成后）】"一段）
**会触发对 083 的同步义务**，而 `specs/083-*` 目前归并行会话 `engineering-consolidation-ci-gates`。
⇒ 两个选择：**(a)** P2 的快照只写进本文件（research.md），`vite.config.ts` 的注释留到 083 侧空闲时一起刷；
**(b)** 同时改两处，但要先与并行会话打招呼。**本批次先按 (a) 做**，并把这一条挂在待办上。
