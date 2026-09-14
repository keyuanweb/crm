# 判据证伪留痕（091，SC-006）

本文件回答一个问题：**新加的结构判据，到底抓不抓得住那个缺陷？**

之所以要专门留痕：本仓有过「用例是绿的，而它声称的场景根本没执行」的先例
（见 [research.md](./research.md) §4 —— 窄屏分支**每次全量测试都真的执行了**，
断言却全落在文案上，而文案在缺陷下完全正常）。**「新增 N 条用例、全绿」不构成证据。**

判据位置：`frontend/src/App.render.test.tsx` → `describe('091：窄屏外壳的结构前提')`。

---

## 一、它断言的是什么

| 用例 | 断言的前提 | 与缺陷的因果关系 |
|---|---|---|
| 窄屏下不被识别为「含侧边栏的布局」，宽屏下被识别 | 外壳那层容器上 **有/无** `ant-layout-has-sider` | 这个类名是组件库那条 `width: 0` 规则的**命中前提**（`antd/es/layout/style/index.js` 的 hasSider 分支）。类名在 ⇒ 规则生效并靠**横向**可伸缩把宽度补回来；而外壳在窄屏把主轴改成纵向，补偿失效 ⇒ 宽度停在 0。类名不在 ⇒ 规则根本不触发 |
| 挂载时窗口就已经是窄屏 | 窄屏下**不渲染** `.ant-layout-sider`、布局不含该类名、菜单仍在且已拍平 | 同上；同时覆盖**窄屏判定的初值**路径（既有用例只覆盖「先宽屏渲染再 resize」） |

**为什么不是「实现耦合」**：断言的目标是**缺陷的命中前提本身**，
而不是「某个恰好为真的实现细节」。证据就是下面第三节——把命中前提放回去，用例立刻红。

---

## 二、权威读数：`hasSiderLayout()` 两种取值的实测后果

浏览器实测（`measure-narrow-shell.mjs`，375 视口，`/customers`）：

| | `ant-layout-has-sider` | 内容容器宽 | 内容区可见宽 | 内容本身宽 | 横向滚动条 |
|---|---|---|---|---|---|
| 有（缺陷态） | 在 | **0** | **24** | 633 | 无（`docOverflow = 0`） |
| 无（修复后） | 不在 | **351** | **375** | 633 | 无 |

⇒「类名在不在」与「内容看得见看不见」之间是**确定的因果**，不是相关性。

---

## 三、三次运行（同一文件，同一命令）

命令：`cd frontend && npx vitest run src/App.render.test.tsx`

### 运行 1 —— 修复**之前**（先写判据，红）

```
Tests  2 failed | 5 skipped (7)
FAIL  src/App.render.test.tsx > 091：窄屏外壳的结构前提 > 窄屏下不被识别为「含侧边栏的布局」，宽屏下被识别
AssertionError: expected <div …(2)>…(2)</div> to be null
FAIL  src/App.render.test.tsx > 091：窄屏外壳的结构前提 > 挂载时窗口就已经是窄屏…
AssertionError: expected <aside …(2)>…(1)</aside> to be null
```

失败时打印的 DOM 把根因原样打了出来 —— `ant-layout-has-sider` 与 `flex-direction: column`
并存在同一层，`<aside class="ant-layout-sider">` 仍在渲染：

```html
<div class="ant-layout ant-layout-has-sider css-dev-only-do-not-override-fgmt3e"
     style="flex: 1 1 0%; min-height: 0; flex-direction: column;">
  <aside class="ant-layout-sider ant-layout-si...
```

### 运行 2 —— 修复之后（绿）

```
Test Files  1 passed (1)
Tests       7 passed (7)
```

（同文件既有的 5 条用例一并通过；文件总耗时 11.6s。）

### 运行 3 —— **定向破坏**：把窄屏外壳改回旧写法

改动只有一处：`{isMobile ? (普通容器…) : (<Sider>…)}` 改成 `{false ? (…) : (<Sider>…)}`
—— 即「窄屏照样按含侧边栏的布局渲染」，与修复前的行为等价。

```
Tests  2 failed | 5 skipped (7)
FAIL  窄屏下不被识别为「含侧边栏的布局」，宽屏下被识别
      AssertionError: expected <div …(2)>…(2)</div> to be null      ← 那一层又带上了 has-sider
FAIL  挂载时窗口就已经是窄屏…
      AssertionError: expected <aside …(2)>…(1)</aside> to be null  ← 侧边栏又渲染出来了
```

**还原后**：`Tests 7 passed (7)`。

---

## 四、结论

判据**会被定向破坏弄红**，且红的正是它声称的那两个前提 ⇒ 它不是一条恒真的断言。
对照 research.md §4 的三层分析，本次补上的正是第三层（**一条本可断言的因果链，此前没有任何断言碰它**）。

**仍未覆盖的部分（如实列出，勿混谈）**：

1. **几何本身在单测里量不出来**（测试环境无布局引擎）⇒ 宽度类判据**只能**在浏览器里成立，
   走 `measure-narrow-shell.mjs` 这条可复跑的脚本，不进单测。
2. **菜单条内部的观感**（分隔线、`...` 溢出触发器的位置）只做过截图核对，未逐项量。
3. **各页面的窄屏适配**不在本项范围内，窄屏下表格仍需横向滚动 —— 那是**预期**，不是本项失败
   （见 [spec.md](./spec.md) 非目标第 1 条）。
