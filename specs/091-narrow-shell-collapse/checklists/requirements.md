# Specification Quality Checklist: 窄屏外壳内容区塌陷修复（091）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-14
**Feature**: [spec.md](../spec.md)

> **本清单在 `/speckit-plan` 之前按 spec.md 的当前状态给出**，逐条附证据。
> 三项**保留意见**见文末 Notes，读本清单者请一并读。

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
      —— ⚠️ **不完全适用，见 Notes 第 1 条**。本项是**纯外壳几何修复**，「用户可感的 WHAT」与
      「CSS/组件库的 HOW」高度重合。spec.md 把 WHAT 放在用户故事与 FR（「内容区占据视口全部可用宽度」
      「菜单是一条整宽横条」），把 HOW（组件库的布局补偿规则、`width:0` 的来龙去脉）**只放在「背景」一节
      用于解释缺陷机理**，不进 FR。FR-W-008 / FR-W-009 确实写了实现约束——**这是刻意的**，它们是
      **防回归的硬约束**（不新增 `!important`、不动宽屏 DOM 与类名），不写成需求就没人守。
- [x] Focused on user value and business needs
      —— 唯一动因是「≤767px 下用户完全无法使用系统」；四个用户故事全部以用户能看见/能操作为表述。
- [x] Written for non-technical stakeholders
      —— 用户故事与 SC 用「可见宽度」「方块」「空白」表述；组件库机理集中在「背景：根因」一节。
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
      —— 唯一的岔路（窄屏菜单取「顶部整宽菜单条」还是「抽屉/汉堡」）**有合理默认**：
      代码注释与既有的菜单拍平逻辑已声明前者，故记为**已定决策**并同时写入「非目标」与「假设」，
      列为验收项而非待澄清项。
- [x] Requirements are testable and unambiguous
      —— FR-W-001~009 每条都有可量的判据（**内容区可见宽 = 视口宽；内容容器宽 = 视口 − 24**、
      菜单宽高、宽屏逐项相同、767/768 两侧分别成立、用例变红后转绿）。
      > **【2026-09-14 逐条复核时的自查】**本行原写作「可见宽度 = 视口 − 24」，**当时没发现它有问题**——
      > 它把「内容区可见宽度」与「内容容器宽度」当成了一个量，而这正是后来在 `spec.md` 里
      > 订正的那处（见该文件「订正块」）。**本条清单当时判「通过」并未挡住它**，如实记下：
      > 这类「同一个名字指两个元素」的缺陷，靠通读判据是发现不了的，**要在真的两个元素上都取数才会撞出来**。
- [x] Success criteria are measurable
      —— SC-001 给出四档宽度下的目标值（~~375 档 **351**、767 档 **743**~~ ⇒ **订正：内容区可见宽 375 档
      **375**、767 档 **767**；内容容器宽 375 档 **351**、767 档 **743**，两个量分开报**），
      并写明两者当前值分别为 **24** / **0**；
      SC-003 菜单从 **200×200** 到「宽 **375**（＝视口宽，非 351）× 高 48」；SC-006 留痕两次运行输出。
- [x] Success criteria are technology-agnostic (no implementation details)
      —— ⚠️ 部分为**像素值**而非「技术实现」：`375` / `351` / `743` 是**该视口下的观测量**，不是实现细节；
      口径（内容区可见宽 ＝ 视口宽；内容容器宽 ＝ 视口宽 − 内容区自身左右内边距）与视口尺寸均已随判据写明。
- [x] All acceptance scenarios are defined
      —— 四个故事共 11 条 Given/When/Then，其中 US3（宽屏零变化）与 US4（用例会红）是**反向保护**。
- [x] Edge cases are identified
      —— 320px 极窄、767/768 两侧边界、跨断点缩放、不渲染常规外壳的页面（数据大屏）、
      修复后可能出现的页面级横向滚动（**表格**的，属页面级、预期仍存在）、菜单项数量与可达性
      （实测形态是 `...` **溢出折叠**，无可滚动余量），六类。
- [x] Scope is clearly bounded
      —— 「非目标」五条，含**明确划出去**的「各页面窄屏适配」与「移动端专用交互」。
- [x] Dependencies and assumptions identified
      —— 假设节写明可用宽度口径、视口取 CSS 像素、实测环境、既有套件视口为 1024 且本项不改它、
      以及「窄屏下页面级观感问题预期仍存在且不计为本项失败」。
- [x] 与既有序号的衔接已写明
      —— 「与 090 的衔接」一节指明本项是 090 T012 的落地，且是 088 视觉验收 375px 档的前置。

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
      —— US1 内容区可见（P1）/ US2 菜单条（P2）/ US3 宽屏零变化（P3）/ US4 窄屏分支被真实执行（P4）；
      四者**各自独立可测**，且 US3 与 US4 是**方向相反的保护**（一个防改坏宽屏、一个防再次腐烂）。
- [x] Feature meets measurable outcomes defined in Success Criteria
      —— 待实现后按 `tasks.md` 实测回填（本项为 spec-first，此处记 **待验证**）。
- [x] No implementation details leak into specification
      —— 见 Notes 第 1 条的**保留意见**（FR-W-008/009 属刻意的防回归约束）。

## Notes

- **必须随本清单一起读的三条**：
  1. **本项是 spec-first**（与 090 的事后回填不同）：`specs/091-*/` 先于任何代码改动存在。
     若日后发现代码先落地，须按 090 的体例在 spec.md 顶部加「事后立项」声明——**不得静默**。
  2. **判据全部锚在外壳几何上**。窄屏下**页面级**的问题（表格挤压、表单拥堵、弹窗宽度）
     **预期仍然存在**，且**不是**本项失败。若验收时把它们算作本项缺陷，那是范围蔓延，
     应另立一项而不是扩大本项。
  3. **FR-W-007/SC-006 是本项最容易被做假的一条，且它曾在初稿里被写错过一次**（已订正，不静默）：
     初稿声称「窄屏分支从未被执行过」——**错的**。`App.render.test.tsx` 有一条 084 的用例把宽度设成
     375 并派发 resize，每次全量测试都真的跑了窄屏那一侧。缺陷能活下来的真实原因是
     **断言全落在菜单文案上**（文案在缺陷下完全正常），而**几何在测试环境里量不出来**。
     故本项要加的是**结构层**的判据（那个使布局塌陷的前提在窄屏下不成立），不是又一条文案断言。
     验收时必须看到**定向破坏 → 用例变红 → 还原 → 转绿**的两次输出；
     并且必须能**指出该用例断言的是哪一个前提**、它与缺陷的因果关系是什么——
     只报「新增 X 条用例、全绿」不构成证据（本仓有过「用例绿而它声称的场景根本没执行」的先例）。
- `Status` 保持 `Draft`，待 `/speckit-plan` 与实现回填实测值后再改。
- 本清单**不**声称覆盖「实现是否正确」——那是 tasks.md 的实测表与 SC-001~006 的职责。
