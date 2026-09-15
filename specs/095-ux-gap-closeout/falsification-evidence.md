# 证伪证据（095）

本文件记的是**读数**：复核用的复现输出、定向破坏的**逐字失败信息**、以及每次临时改动的**逐字节还原**核对。
结论与取舍见 `research.md`；本文件只回答「你凭什么这么说」。

> **完成状态**：§A 已在立项时写入；**§B–§G 已在 2026-09-15 的实施中逐节写入**（定向破坏逐个做、逐个还原，破坏期间不提交）。
> 未追加的章节**不得**被读作「做过了」。

## §0 口径与边界（先立规矩，免得读数被误读）

| 项 | 值 | 含义 |
|---|---|---|
| 环境 | jsdom（vitest） | **没有布局引擎** ⇒ 量到的是 **DOM 结构与配置值**，**不是几何量（像素宽高）** |
| `matchMedia` | `src/test/setup.ts` 恒桩成 `matches: false` | ⇒ `Grid.useBreakpoint().lg` 恒假 ⇒ `isMobile` **恒真** ⇒ **桌面分支在默认桩下跑不到** |
| 因而 | 「窄屏抽屉的真实排版」「方向键的真实行为」 | 是**推演**，本文件**不声称量过**；只记「结构前提已断言」 |
| 临时改动的纪律 | 逐个做、逐个**逐字节还原** | 每次还原后 `git diff` 必须为空；破坏期间**不提交** |
| i18n 读数 | `t(key)` 在测试里返回 **key 本身** | 断言用 `/pages\.departmentList\.xxx/` 这类正则，不写中文/英文字面量 |

---

## §A 复核：「`t` 被遮蔽」是假阳性（**不改代码**）

**被复核的原判**（`frontend/src/pages/departments/DepartmentListPage.tsx` 的 086 注释，原文：

> 本文件 `load()` 里 `const t = await fetchDepartmentTree()` **遮蔽了 i18n 的 `t`**，故其 catch 分支的
> `t('pages.departmentList.msgLoadFailed')` 会对数组调用函数而抛 TypeError——加载失败时用户看不到任何提示。

**复现**（与源文件同形：组件顶层有 i18n 的 `t`，`load()` 的 `try` 块内又声明了同名的 `const t`）：

```
$ node -e "
const t = (s) => 'I18N:' + s;                    // ← 组件顶层的 i18n t
async function load() {
  try {
    const t = await Promise.reject(new Error('boom'));   // ← try 块内的同名 const
  } catch (err) {
    console.log('[catch 里读到的 t 类型] =>', typeof t);
    console.log('[catch 调用的结果]      =>', t('pages.departmentList.msgLoadFailed'));
  }
}
load();
"

[catch 里读到的 t 类型] => function
[catch 调用的结果]      => I18N:pages.departmentList.msgLoadFailed
```

**第一版复现的对照（更直接）**——把外层 `t` 去掉，同一段 `catch` 报的是 **`ReferenceError: t is not defined`**：

```
ReferenceError: t is not defined
    ... t('pages.departmentList.msgLoadFailed') ...
```

**判定**：`const t` 声明在 `try` **块**内，`catch` 是**平级**的另一个块作用域，**不在其子作用域内** ⇒
catch 里根本看不到那个 `const t`，读到的是外层 i18n 的 `t`。**既无 TypeError，也无「看不到提示」。**
（第一版复现的 `ReferenceError` 恰好是同一结论的另一面：那个 `const t` 对 `catch` **完全不可见**。）

**旁证**：`no-shadow` 未在 `frontend/eslint.config.js` 启用（只有 `js.recommended` + `tseslint.recommended` +
`react-hooks` + `react-refresh`），`tsc` 亦不对此报错 ⇒ 无编译期告警可佐。

**⇒ 处置**：**订正注释，代码一行不改**。若按原判去「修」，会改一个**不存在的原因**。

### §A.2 「`walk` 未防 `children` 缺失」的触发条件不成立（**不改代码**）

原判（同一注释的后半句）：「同一函数里 `walk(n.children, …)` 也未防 `children` 为空，`children` 缺失时同样抛错。」

**两条读数**：

```
$ grep -n "children" backend/src/main/java/com/crm/dto/department/DepartmentResponse.java
22:  private List<DepartmentResponse> children = new ArrayList<>();

$ grep -n "default-property-inclusion" backend/src/main/resources/application.yml
22:    default-property-inclusion: non_null
```

**判定**：`children` **初始化为 `new ArrayList<>()`** ⇒ **恒非 null**；而 `non_null` 省的是 **null**，
**空数组仍会序列化**（序列化出 `[]`）⇒ 只要后端返回的是这个 DTO，`children` **恒为数组**，`for (const n of undefined)` 触发不到。
前端 `types/department.ts` 也把 `children` 声明为**必填**（`children: Department[]`）。

**⇒ 处置**：**只订正注释，代码不改**（同 §A.1，一并写进同一处注释订正）。

**另记（不修）**：同文件 `walk` 与 `renderTreeNode` 处**已经**对 `children` 写了 `&&` 判断，
与「类型声明为必填」**不一致**。这是**类型与实现的风格分歧**，不是本批的合规问题，**不修**——如实记在此处备查。

---

## §B–§G 的公共读数口径

- 被破坏的文件**只有** `frontend/src/pages/departments/DepartmentListPage.tsx`（§G 亦然；测试文件不动 ——
  若同时动测试，红/绿的归因就不成立了）。
- **逐字节还原的判据**：每次改动**之前**先算整文件 sha1，**还原之后**再算一次，两次必须相等。
  本次全程的基准值：

  ```
  $ sha1sum src/pages/departments/DepartmentListPage.tsx
  e4169587b948246124df8178c0625aa61ca34457 *src/pages/departments/DepartmentListPage.tsx
  ```

  六次破坏**逐次**还原后均复现该值（下文各节不再重复贴同一行，只标「**= 基准值**」）。
- 破坏期间**没有任何提交**：`git log --oneline -1` 始终停在 `8168c68`，直到 §B–§G 全部结束。
- 跑的是**定向单条**（`-t`），但 `-t` 是**按用例全名做子串匹配**，故 §E、§F 两节会把同 `describe`
  里的兄弟用例一并带上（下文如实标出「同时通过 N 条」），**没有**把「整文件跑绿」当成读数。

---

## §B 定向破坏 1/6：高亮

**破坏**：`renderTreeNode` 里 `<Highlight text={node.name} keyword={q} />` 换回裸 `{node.name}`
（即 T014 之前的老写法；`Highlight` 的 import 留着不动，避免把「删 import」也混进来）。

```
$ npx vitest run src/pages/departments/DepartmentListPage.test.tsx -t "命中的片段被"

 → expect(received).toBeInTheDocument()
 FAIL  src/pages/departments/DepartmentListPage.test.tsx > DepartmentListPage 搜索（095 T018/T014/T015/T016） > 输入关键词后过滤生效，且命中的片段被 <mark> 标出
 Test Files  1 failed (1)
      Tests  1 failed | 10 skipped (11)
```

**红在预期的那一条**（`markFor('华东')` 的 `toBeInTheDocument`），**且只红这一条**。
还原后 sha1 **= 基准值**。

## §C 定向破坏 2/6：防抖

**破坏**：`const q = useDebouncedValue(searchValue, 300)` → `const q = searchValue`（直连，防抖消失）。

```
$ npx vitest run src/pages/departments/DepartmentListPage.test.tsx -t "300ms 防抖"

 → expected false to be true // Object.is equality
 FAIL  src/pages/departments/DepartmentListPage.test.tsx > DepartmentListPage 搜索（095 T018/T014/T015/T016） > 300ms 防抖：刚输入时尚未过滤，窗口过后才生效（两段都要断言）
 Test Files  1 failed (1)
      Tests  1 failed | 10 skipped (11)
```

**读法**：红的是**「窗口内」那一段**（`expect(has('上海分部')).toBe(true)`）—— 直连后过滤在同一帧就完成，
子节点当场消失。这正是本仓最想要的形态：**断言的是「还没过滤」**，而不是「等一会儿就过滤了」
（后者在两种实现下都是绿的，等于没守）。还原后 sha1 **= 基准值**。

## §D 定向破坏 3/6：空状态

**破坏**：把 `<Tree>` 外层那个三目整个删掉，让 `<Tree>` 无条件渲染
（即 `<PageState state="empty" />` **不再出现在任何分支里**）。

```
$ npx vitest run src/pages/departments/DepartmentListPage.test.tsx -t "搜不到任何部门"

 → Unable to find an element by: [data-testid="page-state-empty"]
 FAIL  src/pages/departments/DepartmentListPage.test.tsx > DepartmentListPage 搜索（095 T018/T014/T015/T016） > 搜不到任何部门时给出空状态（而非一片空白）
 Test Files  1 failed (1)
      Tests  1 failed | 10 skipped (11)
```

还原后 sha1 **= 基准值**。

## §E 定向破坏 4/6：断点二择（桌面分支）

**破坏**：`const isMobile = !screens.lg` → `const isMobile = true`（把窄屏分支写死，桌面分支永不执行）。

```
$ npx vitest run src/pages/departments/DepartmentListPage.test.tsx -t "桌面端"

 → expected null to be truthy
 FAIL  src/pages/departments/DepartmentListPage.test.tsx > DepartmentListPage 详情与无障碍（095 T030/T032/T037） > 桌面端（本文件内覆盖 matchMedia）：详情走对话框
 Test Files  1 failed (1)
      Tests  1 failed | 10 skipped (11)
```

**这一节是本批最该做的一条**：`plan.md` 的风险表把它列为「用例全绿而桌面分支从未执行」的典型
（本仓有先例）。读数证明：在**本文件覆盖了 `matchMedia`** 的前提下，写死 `isMobile` 会让
`expect(document.querySelector('.ant-modal')).toBeTruthy()` 拿到 `null` 而红 —— 即**桌面那条断言真的在跑**，
不是靠默认桩蒙过去的。还原后 sha1 **= 基准值**。

## §F 定向破坏 5/6：aria-label

**破坏**：删掉搜索框的 `aria-label={t('pages.departmentList.ariaSearch')}` 一行。

```
$ npx vitest run src/pages/departments/DepartmentListPage.test.tsx -t "无障碍"

 → Unable to find a label with the text of: /pages\.departmentList\.ariaSearch/
 FAIL  src/pages/departments/DepartmentListPage.test.tsx > DepartmentListPage 详情与无障碍（095 T030/T032/T037） > 无障碍：搜索框与树容器都有可朗读名称（经 t()，非字面量）
 Test Files  1 failed (1)
      Tests  1 failed | 2 passed | 8 skipped (11)
```

**「2 passed」的来历**（免得被读成「三条无障碍断言都活着」）：`-t "无障碍"` 是按**用例全名**子串匹配，
它同时命中同 `describe` 的移动端与桌面端两条，那两条与 aria-label 无关，故不红。被破坏的那一条**红了**，
红的正是 `getByLabelText`。还原后 sha1 **= 基准值**。

---

## §G 定向破坏 6/6（**计划外新增**）：展开键的类型

这一条**不在** `tasks.md` T018 的五条里，是实施中**新发现的一层缺陷**，故补做并在此登记。

### G.1 发现过程（先说读数、再说结论）

写 T008 的用例时，`expect(has('上海分部')).toBe(true)`（首屏展开全部）**红**。用**临时探针**
（`src/pages/departments/zz-probe.test.tsx`，**untracked、用后已删**，不在任何提交里）把变量收敛到最小：

```
$ npx vitest run src/pages/departments/zz-probe.test.tsx

 NUM-KEYS-EXPANDED: false      ← expandedKeys={[1]}   （number）
 STR-KEYS-EXPANDED: true       ← expandedKeys={['1']} （string）
 TREENODES: 3                  ← 树只有 2 个可见节点，第 3 个是 rc-tree 的隐藏占位
 CLOSE-SWITCHERS: 1
 HAS-SHANGHAI: false
 LIST-ARIA: pages.departmentList.ariaTree
```

两组是**同一棵两节点树**（`P > C`），只有 `expandedKeys` 的元素类型不同 ⇒ **数字键不展开、字符串键展开**。

**原因**（同一次核对里读的 DOM 与源码）：React 会把元素的 `key` 强制转成字符串，故
`<Tree.TreeNode key={node.id}>` 在 rc-tree 内部的键是 `'1'`；而页面此前把 `n.id`（number）塞进
`expandedKeys`，rc-tree 5.10 用 `expandedKeys.includes(key)` **精确比较** ⇒ `1 !== '1'` ⇒ **恒不展开**。

**后果（本批必须处理，否则名实不符）**：`load()` 里的 `allKeys` 与 T008 新写的
`expandAllKeys` **两处都是空操作**，树永远收起 —— 也就是说，**只改 T008 那个 `else` 分支是不够的**。
这与 `4e0b6ce` 提交信息里「修正展开态自相矛盾」的说法之间有一段落差，故在此**如实记下落差**；
修法见下，且这一条已被 §G.2 证实为**可证伪**。

### G.2 破坏（把两处 `String()` 撤回成数字，即回到缺陷态）

**破坏**：`allKeys.push(String(n.id))` → `allKeys.push(n.id as string)`，
`keys.push(String(node.id))` → `keys.push(node.id as string)`。

```
$ npx vitest run src/pages/departments/DepartmentListPage.test.tsx -t "清空关键词后恢复"

 → expected false to be true // Object.is equality
 FAIL  src/pages/departments/DepartmentListPage.test.tsx > DepartmentListPage 搜索（095 T018/T014/T015/T016） > 清空关键词后恢复「全部展开」（095 T008 的可见行为变化）
 Test Files  1 failed (1)
      Tests  1 failed | 10 skipped (11)
```

还原后 sha1 **= 基准值**。

### G.3 顺带订正一处既有叙述

`§0` 那行「`matchMedia` 恒桩成 `matches: false` ⇒ 桌面分支跑不到」在**本文件的 §E** 有了反例：
**在测试文件内覆盖 `matchMedia` 之后，桌面分支是能跑到的**。故本批对 T032/T037 的读数是
「**两分支各有独立断言，且桌面那条已用定向破坏证伪**」，不是「桌面分支跑不到、只能推演」。
（`§0` 那行说的是**全局默认桩**下的行为，两者不冲突；此处只是把边界说清楚，免得被读作能力更弱。）

---

## §I 067 T009（状态流转红色警示）的两处定向破坏

**被破坏的文件**：`frontend/src/pages/map/UsageMapPage.tsx`（生产侧；测试文件不动）。基准值：

```
$ sha1sum src/pages/map/UsageMapPage.tsx
f470e5c9c5dea89d7752ac0b8d3ff2099453eafa *src/pages/map/UsageMapPage.tsx
```

两次破坏均**未提交**，还原后 sha1 复现基准值、`git diff` 为空。

### §I.1 去掉 `stroke` 的 warning 分支

**破坏**：`d.data.warning ? '#cf1322' : (d.data.color ?? '#1677ff')` → `(d.data.color ?? '#1677ff')`。

```
$ npx vitest run src/pages/map/UsageMapPage.test.tsx -t "红色警示"

 → expected '#8c8c8c' to be '#cf1322' // Object.is equality
 AssertionError: expected '#8c8c8c' to be '#cf1322'
 FAIL  src/pages/map/UsageMapPage.test.tsx > UsageMapPage（029 员工使用地图渲染） > 状态流转的异常节点带红色警示（067 T009）
 Test Files  1 failed (1)
      Tests  1 failed | 7 skipped (8)
```

**读法**：红的是 `cs7` 那一路 —— 它**自带**的 `color` 就是 `#8c8c8c`，所以「灰变红」这一步
只能由 warning 分支产生，蒙不对。这正是选 `cs7` 而不是 `cs6`（自带 `#cf1322`）当断言输入的原因：
拿 `cs6` 去测，即使删掉 warning 分支也仍是红的，**用例会假绿**。

### §I.2 悬停移出对 warning 视而不见（恢复成一律 2）

**破坏**：`lineWidth: node.warning ? 3 : 2` → `lineWidth: 2`。

```
$ npx vitest run src/pages/map/UsageMapPage.test.tsx -t "红色警示"

 → expected last "spy" call to have been called with [ [ { id: 'cs6', style: { …(2) } } ] ]
 AssertionError: expected last "spy" call to have been called with [ [ { id: 'cs6', style: { …(2) } } ] ]
 FAIL  src/pages/map/UsageMapPage.test.tsx > UsageMapPage（029 员工使用地图渲染） > 状态流转的异常节点带红色警示（067 T009）
 Test Files  1 failed (1)
      Tests  1 failed | 7 skipped (8)
```

**同时跑的对照**（同一次破坏下）：

```
$ npx vitest run src/pages/map/UsageMapPage.test.tsx -t "节点悬停效果"
 Test Files  1 passed (1)
      Tests  1 passed | 7 skipped (8)
```

**读法（这是 §I 最该记的一条）**：既有的「节点悬停效果」用例**在同一个缺陷下照旧绿** ——
因为它只喂 `a1`（非 warning 节点），`? 3 : 2` 走哪支它都读不到。新用例的 ④ 用 `cs6` 才把这条守住。
即：**新增的这条不是重复覆盖，而是补上了既有用例在结构上够不到的那一半**。

---

## §J 全量并发下的稳健性改动，与六处破坏的**复核**（2026-09-16）

### J.1 起因：同一份代码，只换并发度，读数不稳定（这是读数，不是推断）

| 跑法 | 读数 |
|---|---|
| 单文件定向（`npx vitest run src/pages/departments/DepartmentListPage.test.tsx`） | **11/11 绿** |
| 全量 `--maxWorkers=4 --minWorkers=1` | 曾 `Test Files 1 failed \| 85 passed (86)` / `Tests 2 failed \| 427 passed (429)`，两条**都在本文件** |
| 全量默认池（`pnpm test:coverage`） | 曾 5 条红：`ChangePasswordPage.test.tsx` **1 条（不是本文件）** + 本文件 4 条 |

**归因是负载**：**红的条数与具体哪几条每次都在变**，且其中一条属于**别的文件**；
单文件跑恒绿；而本仓既有记录（`vitest-default-pool-oversubscribes-this-box`）已确定默认池在这台机器上超订、
重文件会撞全局 `testTimeout: 20000` 假红。默认池的 `collect` 阶段本身实测接近 5 分钟。

### J.2 改动（**只在本文件内，逐字未动任何断言**）

1. 新增 `itPage` 包装：本文件 11 条用例的单项上限由全局 `testTimeout: 20000` 放宽到 **60000**；
2. 「编辑：弹窗预填」那条的**同步** `toHaveValue` 改为 `waitFor(…, { timeout: 5000 })` ——
   `destroyOnClose` 让字段是**开弹窗时才挂载**的，「标题已在场」与「字段已取到初值」不保证同帧；
3. 「确认后调用 deleteDepartment」那条的 `findByRole('button', { name: /确\s*定/ })` 由默认 1s 改为显式 5s ——
   Popconfirm 的内容是**点开后才挂载**的。

**判据未放宽**：预填的值仍必须**恰好**是 `华东销售部`；两处 `waitFor` 只是耐心，
实现一旦坏掉仍以**断言失败**（而非超时）转红 —— 下表即为执行证据。

### J.3 六处破坏的**复核**（§B–§G 的读数取自改动**之前**的文件，故必须重做，不得沿用）

| 节 | 破坏 | 本次读数 | 还原后 |
|---|---|---|---|
| §B | `<Highlight …/>` → 裸 `{node.name}` | `1 failed \| 10 skipped`；`expect(received).toBeInTheDocument()` | sha1 **= 基准值** |
| §C | `q = useDebouncedValue(searchValue, 300)` → `q = searchValue` | `1 failed \| 10 skipped`；`expected false to be true` | sha1 **= 基准值** |
| §D | 三目条件写死 `false`（`PageState` 不再出现在任何分支） | `1 failed \| 10 skipped`；`Unable to find an element by: [data-testid="page-state-empty"]` | sha1 **= 基准值** |
| §E | `isMobile = !screens.lg` → `isMobile = true` | `1 failed \| 10 skipped`；`expected null to be truthy` | sha1 **= 基准值** |
| §F | 删掉搜索框的 `aria-label` | `1 failed \| 2 passed \| 8 skipped`；`Unable to find a label with the text of: /pages\.departmentList\.ariaSearch/` | sha1 **= 基准值** |
| §G | 两处 `String()` 撤回为数字 | `1 failed \| 10 skipped`；`expected false to be true` | sha1 **= 基准值** |

六次均**未提交**；每次还原后 sha1 逐次再现
`e4169587b948246124df8178c0625aa61ca34457`、`git diff` 为空（破坏期间 `git log --oneline -1` 停在 `d45c304`）。
§F 的「2 passed」来历同 §F 原文（`-t "无障碍"` 按用例全名子串匹配，同时带上同 `describe` 的两条兄弟用例）。

> **§I（使用地图）未受影响、故未重做**：本次改动只碰 `DepartmentListPage.test.tsx`，
> `UsageMapPage.test.tsx` 与 `UsageMapPage.tsx` 一字未动 ⇒ §I.1/§I.2 的读数继续有效。

### J.4 改后的门禁读数

见文末「§Y 门禁读数」。

---

## §Z 本批**没有**验证的事（原编号 §H，因插入 §I 而改到文末，**内容未改**；与 `quickstart.md` §4 同源，重复在此以免只读本文件的人误解）

> 章节号与 `tasks.md` T018 里写的「§B–§F」**不完全一致**：实际是 §B–§G（§G 是实施中新发现的
> 展开键缺陷，计划外新增）**加 §I**（067 T009 的破坏）。此处如实标出，免得被读成「五条之外还有没做的」。

- 抽屉在**真实窄屏下的几何/排版**、树的**方向键行为**：jsdom **没有布局引擎、没有真实焦点模型** ⇒
  **未量过**，只断言了「结构前提」（`role`/`aria-label`/分支存在）。
- `confirmDeleteRisk` 文案里的**数字**：`setup.ts` 的 i18n mock **丢弃插值** ⇒
  只证明了「风险说明挂上了」，**没有**证明「数字等于该节点的 childCount/memberCount」。
- 树的 `aria-label` **不在** `role="tree"` 那个元素上（实测落在树内部的 `.ant-tree-list`）⇒
  **不声称** `role="tree"` 已被命名。落点由 antd 5.22 / rc-tree 5.10 的 prop 透传位置决定。
- 目录里**没有**新的 e2e、没有视觉背书；本批所有读数都来自 jsdom 单测。

---

## §Y 门禁读数（2026-09-16 追加；置于 §Z 之后是为了**不改 §Z 既有内容**）

跑的是**改后**的工区（含 §J 的稳健性改动），命令即 `package.json` 的 `test:coverage`（**默认 worker 池**）：

```
$ cd frontend && pnpm test:coverage
 Test Files  86 passed (86)
      Tests  429 passed (429)
   Duration  173.91s (transform 34.03s, setup 36.34s, collect 472.26s, tests 1455.36s, environment 57.85s, prepare 13.89s)
All files          |   69.82 |    74.76 |   38.54 |   69.82 |
EXIT=0
```

| 项 | 读数 | 与阈值比（`vite.config.ts`：statements/lines 33.6、branches 47.2、functions 21.4） |
|---|---|---|
| statements | 69.82 | 高于 33.6 |
| branches | 74.76 | 高于 47.2 |
| functions | 38.54 | 高于 21.4 |
| lines | 69.82 | 高于 33.6 |

**不引用单次小数位当论据**（`vite.config.ts` 的明文要求），**未改阈值**（故未动 `083` 的 `data-model.md` §4）。

**另六条门禁**（同一次跑，改后工区）：

```
$ pnpm typecheck   → 无输出（tsc 静默通过）
$ pnpm lint        → 无输出（eslint 静默通过）
$ pnpm i18n:check  → ✓ zh-CN 2879 键 / en 2879 键；路由 58 条 / 清单 56 项 / 粗粒度别名 3 条
$ pnpm menu:check  → ✓ 56 个菜单项
$ pnpm perms:check → ✓ 63 个权限码；8 个文件含已登记 ADMIN 判断，共 9 处
$ pnpm ui:check    → ✓ 271 个产品文件（126 个 tsx）/ 297 个 Form.Item；冻结台账 54 处，未新增违规
```

其中 **`ui:check` 的 54 处既存债与改动前一致（未增长）** —— 这是本批「R6 台账不许增长」那条约束的可核读数。

**测试文件数**：**84 → 86**。基线以 `git ls-tree -r --name-only 8168c68` 计得 **84**
（`plan.md` 写的「83 → 85」**少算 1**，以实测的 84 → 86 为准；差 1 的那笔是 `src/App.render.test.tsx`，
`git ls-files 'src/**/*.test.ts*'` 因 pathspec 的 `**` 行为漏掉了它，而它是 **tracked** 的）。

**分母可信度**：本次读数取自**无第二写入者**的工区（`git status` 无他人未跟踪的 `*.test.tsx`）——
本仓有过「他人未跟踪的测试文件被 vitest 静默计入总数、制造假绿」的先例（见记忆 `coverage-readings-need-a-quiescent-tree-to-be-attributable`）。
