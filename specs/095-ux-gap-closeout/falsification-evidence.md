# 证伪证据（095）

本文件记的是**读数**：复核用的复现输出、定向破坏的**逐字失败信息**、以及每次临时改动的**逐字节还原**核对。
结论与取舍见 `research.md`；本文件只回答「你凭什么这么说」。

> **完成状态**：§A 已在立项时写入；**§B–§F 在实施过程中逐节追加**（定向破坏逐个做、逐个还原，破坏期间不提交）。
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

## §B 定向破坏 1/5：高亮

*（实施时追加）*

## §C 定向破坏 2/5：防抖

*（实施时追加）*

## §D 定向破坏 3/5：空状态

*（实施时追加）*

## §E 定向破坏 4/5：断点二择（桌面分支）

*（实施时追加）*

## §F 定向破坏 5/5：aria-label

*（实施时追加）*
