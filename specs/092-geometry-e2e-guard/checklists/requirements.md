# Specification Quality Checklist: 列表页与窄屏外壳几何的端到端护栏

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-14
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
      —— ⚠️ **本项**是**开发者面向的基础设施**（护栏本身），不是业务功能，
      故「不出现实现细节」这一条**按其本意**（不让技术选型替代需求）解读，不按字面执行：
      Playwright / `pnpm run test:e2e` / 选择器 / 文件路径**就是**交付物本身，写出来才是可验收的。
      已核对的**真正**边界是：**不把「怎么写」写成判据**——判据一律表述为
      **几何量之间的算术关系**（FR-EG-005~009），而不是「用某个 API 取值」。
- [x] Focused on user value and business needs
      —— 用户价值链条写明：090/091 的缺陷都是**用户直接看得见**的（197–754px 空白、24px 的缝、200×200 方块），
      而它们的判据**一条自动化用例都没有**，回归成本为零。本项把「修好过一次」变成「不会再坏」。
- [x] Written for non-technical stakeholders
      —— ⚠️ **不适用**：本项的「用户」是**维护这个仓库的开发者**。已在「背景」节用非代码语言
      说明了两条真实先例（091 的窄屏分支全绿、048 的 e2e 从未执行），使利害可由非实现者理解。
- [x] All mandatory sections completed
      —— User Scenarios & Testing（3 个故事 + 边界情形）、Requirements（FR-EG-001~014 + 非目标 + 关键实体）、
      Success Criteria（SC-EG-001~008）、Assumptions 均已填写。

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
      —— **范围这一步的岔路已由用户当面选定**（090 的列表页几何 + 091 的窄屏几何；
      不做宽屏五档外壳零变化），故无待澄清项。此选择已写入「非目标」第 1 条并说明理由，不是静默决定。
- [x] Requirements are testable and unambiguous
      —— 14 条 FR 每条都有落点：存在与覆盖（001~004）、量取口径（005~009）、反假绿（010~014）。
      口径的歧义点**逐个钉死**：滚动条容差（≤16px，写死并注明理由）、
      容差不得放大到掩盖缺陷（缺陷态 24 与 351 差一个数量级）、
      短/长页运行时判定、页脚高度现量不写死。
      ⚠️ **特别钉死的一处**：FR-EG-007 要求内容区可见宽与内容容器宽**分成两条断言**——
      因为只断言两者之差会被缺陷态**同时满足**（0 = 24 − 24）。这是**从 091 的订正里直接学到的**。
- [x] Success criteria are measurable
      —— SC-EG-002 给出短页样本下限；SC-EG-003 给出窄屏三个量的实测对照读数（375 / 351 / 375×48）
      与横向溢出 0；SC-EG-005 要求**样本数可读数且有下限断言**；SC-EG-006 要求**三次运行的留痕**；
      SC-EG-007 给出四道既有门禁。SC-EG-001 给出命令级验收（一条命令跑到全部几何断言）。
- [x] Success criteria are technology-agnostic (no implementation details)
      —— ⚠️ 部分为**像素值**与**元素量**：`375` / `351` / `48` 是**该视口下的观测量**，不是实现细节；
      且判据本身是**相对量**（`滚动盒底边 − 自身下内边距 − 卡片底边 − 卡片下外边距 == 0`），
      绝对像素只作**对照读数**。视口尺寸随判据写明。
- [x] All acceptance scenarios are defined
      —— 三个故事共 **12** 条 Given/When/Then。
      US1 的 4 条覆盖短页吃满、余量只剩外壳占位、长页不得断言填满、输出可读；
      US2 的 5 条覆盖内容区/内容容器/菜单三个量 + 横向溢出 + 失败信息可定位；
      US3 的 3 条覆盖活性计数、数据变化时的显式失败、定向破坏留痕。
- [x] Edge cases are identified
      —— 七类：滚动条占位、视口不得依赖默认值、窄屏不走 `matchMedia`、
      数据量变化导致短/长页归属漂移、后端不归本项管、页脚高度可能变、`/departments` 没有分页。
- [x] Scope is clearly bounded
      —— 「非目标」六条，含**用户当面划出去**的「宽屏五档外壳零变化」，
      以及「**不改任何生产代码**——若发现真缺陷另立一项，不得顺手修」
      （避免「护栏变绿」与「缺陷被修」两件事纠缠、使留痕失去意义）。
- [x] Dependencies and assumptions identified
      —— 假设节写明：dev server / 后端 / 凭据 / 验证码关闭、
      **e2e 打的是「已在跑的后端」故写证据前须比对进程启动时间与 `.class` mtime**、
      数据是活的（短页归零时**因数据而红是预期行为**，处置是增补采样页而非放宽判据）、
      CSS 像素口径、±1px 取整容差、滚动条容差 ≤16px、不新增运行时依赖。

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
      —— 逐条对照：001↔US1/US2 的整体覆盖；002↔SC-EG-001；003↔「视口显式设定」边界情形；
      004↔假设节的登录前置；005↔US1-①；006↔US1-②；007↔US2-①②；008↔US2-③；009↔US2-④；
      010↔US3-①；011↔US3-②；012↔US1-③；013↔US2-⑤；014↔SC-EG-006。
- [x] User scenarios cover primary flows
      —— 三个故事各自**可独立验证**（US1 破坏 090 的修复会红、US2 破坏 091 的修复会红、
      US3 读输出与源码即可判），且不互为前提。
- [x] Feature meets measurable outcomes defined in Success Criteria
      —— 见上两条的逐条对照；SC-EG-005/006 是**反假绿**的两条硬要求，直接对应「背景」里列出的两次先例。
- [x] No implementation details leak into specification
      —— ⚠️ 同第一条：本项的实现细节即交付物。已确保**判据不含实现方式**
      （例如写「内容容器宽度 = 内容区可见宽度 − 24」，不写「用 `getBoundingClientRect().width` 取值」）。

## Notes

- **本清单的第 1 条与第 3 条是「按其本意解读」而非字面通过**，两条均已在上文写明理由。
  这是**有意的**，不是疏漏：本项的交付物是一套测试，若强行剥掉框架与命令名，
  规格会退化成「要有一个几何门禁」这样不可验收的一句话。
- **第 2 条（无待澄清项）成立的前提是用户已当面选定范围**。若没有那次选择，
  本项会有**恰好一条** `[NEEDS CLARIFICATION]`（守 090 还是 090+091 还是再加宽屏），
  且它是**范围级**的——按「范围 > 安全 > 体验 > 技术」的优先级，它正是最该问的那一条。

## 复核记录

- 2026-09-14：初稿写就后逐条自查。发现并当场修正 **1** 处：
  FR-EG-007 初稿只写了「内容容器宽 = 内容区可见宽 − 24」一条断言——
  **该表述会被缺陷态同时满足**（24 − 24 = 0，而缺陷态的内容容器宽正是 0），
  是一条**会放过原缺陷**的假判据。已拆成两条独立断言并写明原因。
  这一处是从 `specs/091-narrow-shell-collapse/spec.md` 的「订正块」直接学到的教训。
