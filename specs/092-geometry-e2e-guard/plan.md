# Implementation Plan: 列表页与窄屏外壳几何的端到端护栏

**Branch**: `092-geometry-e2e-guard` | **Date**: 2026-09-14 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/092-geometry-e2e-guard/spec.md`

> **⚠️ 三项如实告知（不得省略）**
> 1. **本项不改任何生产代码**：纯新增测试。若写用例时发现真缺陷，**另立一项**——
>    理由见 spec 非目标第 3 条（否则「护栏变绿」与「缺陷被修」纠缠，定向破坏的留痕失去意义）。
> 2. **本项的前身是 090 的 T013**，而 T013 在 090 里被标为「**可选**」。本项把它做成**必做**，
>    理由是 [research.md](./research.md) §4 末尾那两次先例：**不做，090/091 的几何就又回到「只能靠人复跑脚本」**。
> 3. **判据守的是「不变式」而不是「一次快照」**：本项**不**冻结基线、**不**逐项比对历史读数，
>    只断言几何量之间的算术关系。这是与 091 的 SC-004 刻意不同的取舍，理由见 research §1。

## Summary

给 090（列表页内容区撑满）与 091（窄屏外壳塌陷）的**几何判据**补上自动化守卫。
这两项的判据全是量出来的，而**测试环境没有布局引擎**（jsdom 的 `clientWidth` / `getBoundingClientRect()`
全是假值），几何在单测里量不出来 ⇒ 两项都只有「人手动跑的取证脚本 + 人眼截图」，
**任何一次外壳或 CSS 改动都能悄悄把几何改回去而全仓测试全绿**——这正是 091 那个缺陷
从 `c8bc7f8`（「016 移动端适配」）活到今天的同一台机器。

做法：新增两个 Playwright 用例文件，用真实布局引擎断言**相对量**的几何不变式；
判据**自带活性下限**（零样本必须是红的）；并以**三次定向破坏**证明它真的会红。

## Technical Context

**Language/Version**: TypeScript（前端既有版本，React 18.2 + Vite 5）

**Primary Dependencies**: `@playwright/test`（`frontend/node_modules` 中已有，**不新增依赖**）；
被测对象是 antd 5.22.0 + @ant-design/pro-components 2.8.10 渲染出的真实页面

**Storage**: N/A（本项不写任何数据；用例只读页面几何）

**Testing**: Playwright（`pnpm run test:e2e`）；**不新增单测**——几何在 jsdom 里量不出来（本项的全部理由）

**Target Platform**: 无头 Chromium（Playwright 自带）；dev server `http://localhost:5173`、后端 8081

**Project Type**: Web 应用（前端测试基础设施）

**Performance Goals**: 新增用例的**总耗时 ≤ 30s**（受 `playwright.config.ts` 的 `timeout: 30s` 约束）。
实测单次页面加载的几何收敛 ≈ **0.5s**（research §2），最长的用例是宽屏 6 次页面加载。

**Constraints**:
- **判据一律相对量**，页脚高度**现量**，不写死 `664` / `624` / `56`
- **视口由用例显式设定**（`test.use({ viewport })`），不依赖 Playwright 默认值
- **窄屏判据不走 `matchMedia`**
- **不改 `playwright.config.ts`**（理由与影响面见 research §6）
- **不改生产代码**（spec 非目标第 3 条）

**Scale/Scope**: 新增 **3** 个文件（1 个 helper + 2 个 spec）；
采样页 **10 个**（宽屏短页候选 4 + 宽屏长页候选 2 + 窄屏 4）；**0** 处生产代码改动。

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 本项是否触及 | 判定 |
|---|---|---|
| 一、契约优先的 API 设计（不可协商） | 无新端点、无 DTO、**无契约变更** | ✅ 不适用 |
| 二、分层架构与关注点分离 | 只新增测试文件；不碰 Service / Repository / 组件 | ✅ 不违反 |
| 三、数据完整性、安全与校验（不可协商） | 不写数据、不改鉴权；用例复用既有登录前置 | ✅ 不违反 |
| 四、**测试优先与质量门禁**（不可协商） | **本项就是为它做的**：把两条已交付的判据变成**每次合并的关卡**。遵循测试金字塔——本项是「**极少量端到端测试**」，只守**跨页面共用的外壳几何**，不下沉到页面级观感 | ✅ **正面符合** |
| 五、简洁、可维护与可观测 | YAGNI：**不新增依赖**、不改配置、不动生产代码；失败信息按 spec FR-EG-013 要求可定位到「具体页面 + 具体量」 | ✅ 符合 |
| 技术与架构约束（前端 React + TS） | 一致 | ✅ 不违反 |
| 开发工作流（Spec Kit 流程） | 本项走 `/speckit-specify → plan → tasks → implement`，规格与任务已/将留档 | ✅ 符合 |

**结论**：**无违规**，无需 Complexity Tracking。

> 一处需要说明的**形态**：章程「四」写明前端测试用「Jest + React Testing Library」。
> 本项**不**新增单测，用的也是 Playwright 而非 Jest——**这不是偏差**：
> Playwright 是本仓**早已在用**的 e2e 工具（`playwright.config.ts` + `e2e/` 下 5 个既有文件 + CI 的
> 独立 `e2e` 作业），章程该条指向的是**单元测试层**，而 e2e 层本仓既定的工具就是 Playwright。
> 本项的判据（真实布局几何）**在原理上就不可能在 jest/jsdom 里成立**——这正是本项存在的理由。

**Phase 1 后的复查**：设计未引入新依赖、新配置、新生产代码路径，结论与上表一致，**无新增违规**。

## Project Structure

### Documentation (this feature)

```text
specs/092-geometry-e2e-guard/
├── spec.md              # 已完成（/speckit-specify）
├── plan.md              # 本文件（/speckit-plan）
├── research.md          # Phase 0 产出：等待策略、容差、样本、破坏点的全部实测依据
├── quickstart.md        # Phase 1 产出：可复跑的验收步骤与预期读数
├── checklists/
│   └── requirements.md  # 规格质量清单
└── tasks.md             # Phase 2 产出（/speckit-tasks，不由本命令创建）
```

**不产出的两项，及理由**：

- **无 `contracts/`**：本项不新增任何对外接口。被测的是**已存在的**页面几何，
  没有需要契约化的请求/响应结构（章程「一」不触及）。
- **无 `data-model.md`**：本项不涉及数据实体。spec 的「Key Entities」是**四个几何量与它们的算术关系**，
  不是持久化实体；它们的定义已足够精确地写在 spec 里，另起一份文件只会是重复。

### Source Code (repository root)

```text
frontend/
├── playwright.config.ts                 # 不改
└── e2e/
    ├── helpers/
    │   ├── login.ts                     # 既有，复用，不改
    │   └── geometry.ts                  # 【新增】量取表达式 + 两阶段等待 + 常量 + 分类与活性报告
    ├── geometry-list-page.spec.ts       # 【新增】090 的不变式（test.use viewport 1280×720）
    ├── geometry-narrow-shell.spec.ts    # 【新增】091 的窄屏不变式（test.use viewport 375×812）
    ├── login.spec.ts                    # 既有 5 个
    ├── menu-visibility.spec.ts
    ├── module-page-auth.spec.ts
    ├── role-permissions.spec.ts
    └── user-management.spec.ts
```

后端 `backend/`：**零改动**。

**Structure Decision**：沿用本仓既有的 e2e 目录约定（`testDir: './e2e'`、helper 放 `e2e/helpers/`）。
把共享逻辑（量取表达式、等待、常量、活性报告）抽到 `helpers/geometry.ts`，
使它**不被 Playwright 当作测试文件收走**（与 `helpers/login.ts` 同理），
同时让两个 spec 的量取口径**只有一份**——这是本项唯一有实质意义的抽象，
因为「两个文件的判据口径不一致」正是 091 刚刚订正过的那类缺陷。

## 设计决策（逐条对应 spec 的 FR）

| # | 决策 | 依据 |
|---|---|---|
| 1 | 判据全部写成**相对量**；页脚高度现量 | research §1；FR-EG-005/006 |
| 2 | 两阶段等待：**要素齐备 + 连续两次读数一致**（100ms 轮询，8s 超时抛错） | research §2（只等卡片不够：几何要到 ~500ms 才定）；FR-EG-002 的「不得 flake」 |
| 3 | 滚动条容差 **≤16px**，写死并注明理由 | research §3（本机实测 0；防 CI 换实现后假红）；不掩盖缺陷（缺陷态 24） |
| 4 | 短/长页**运行时判定**；活性下限硬断言（**零样本 = 红**） | research §4（`/customers` 两次探针读到 19 与 20 行）；FR-EG-010/011 |
| 5 | 样本：宽屏短页 4（**两个入口各有样本**）+ 长页 2 + 窄屏 4 | research §4；`/roles` 因「只溢出 65px、太脆」排除，`/recycle` 因 **404** 排除 |
| 6 | 窄屏菜单锚点 = `.ant-menu` 的直接父元素，并断言它是外壳的直接子元素 | research §5（两种取法实测同一节点）；让「认错对象」不可能 |
| 7 | 每个桶**一个用例**，桶内**逐页 soft 断言**，末尾**硬断言活性下限** | 一次运行报出**全部**坏页（FR-EG-013），而不是只报第一个 |
| 8 | 失败信息带**页面路径 + 量名 + 实测值与应达值** | FR-EG-013 |
| 9 | 不新增依赖、不改配置、不改生产代码 | spec 非目标 3/4；章程「五」YAGNI |
| 10 | 定向破坏**三次**（入口 A / 入口 B / 窄屏分支），逐个还原并核对工作区干净 | research §7；SC-EG-006 |

## 与 spec 判据的对应（可核查）

| spec 条目 | 落在哪 |
|---|---|
| FR-EG-001~002 | 两个新 `*.spec.ts`，被 `testMatch` 默认规则收走，无需改配置 |
| FR-EG-003 | 两个 spec 各自的 `test.use({ viewport: … })` |
| FR-EG-004 | 复用 `helpers/login.ts` 的 `login(page)` |
| FR-EG-005~009 | `helpers/geometry.ts` 里的量取表达式与断言封装 |
| FR-EG-010~011 | 桶内计数器 + 末尾硬断言；短/长页运行时分类 |
| FR-EG-012 | 长页桶的三条断言（溢出、`fill ≤ 0`、末行可见） |
| FR-EG-013 | 每个 `expect(…, message)` 的自定义消息 |
| FR-EG-014 / SC-EG-006 | 三次定向破坏 + 三次运行输出留痕 |
| SC-EG-001~005、007~008 | 见 quickstart.md 的验收步骤 |

## Complexity Tracking

**无章程违规，本节留空。**
