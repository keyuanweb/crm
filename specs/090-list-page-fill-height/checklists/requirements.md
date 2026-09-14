# Specification Quality Checklist: 列表页内容区撑满（090）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-14
**Feature**: [spec.md](../spec.md)

> **本清单是事后回填时补做的**（本项为「代码先落地、规格后补」，见 spec.md「本规格的由来」）。
> 结论按 spec.md 的**当前**状态给出，逐条附证据；**不追溯声称本项曾在前置阶段通过过本清单。**

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
      —— ⚠️ **不完全适用，已在 spec.md 说明**：本项是**纯样式修复**，「用户可感的 WHAT」与「CSS 的 HOW」高度重合。
      spec.md 把 WHAT 放在用户故事与 FR（「卡片下边界 = 内容区下边界」「分页落到卡片底部」），
      把 HOW（选择器、flex 链）留在 plan.md。FR-003/FR-008 确实写了实现约束——**这是刻意的**：
      它们是**防回归的硬约束**（不写 `min-height:0`、不用带组合符的 `:is()`），不写成需求就没人守。
- [x] Focused on user value and business needs
      —— 唯一动因是用户报的「表格查询底部空白太多了」，验收以「用户看到的空白量」表述。
- [x] Written for non-technical stakeholders
      —— 用户故事与 SC 用像素与"空白"表述；技术细节集中在 plan.md。
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
      —— FR-001~008 每条都有可量的判据（边界等于内容盒底、gap ≤56、行数不变、hidden=none…）。
- [x] Success criteria are measurable
      —— SC-001 空白 ≤56px（改前 197–754px）；SC-002 19 行不变且内部 overflow 0；SC-003 卡片底边 1044；
      SC-004 四个取样页逐像素一致；SC-005 四道门禁全绿。
- [x] Success criteria are technology-agnostic (no implementation details)
      —— ⚠️ 部分为**像素值**而非"技术实现"：`cardBot=1044` 是**该视口下的观测量**，
      不是实现细节；口径与视口（1280×1100）已随判据写明。
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
      —— 非列表页、展开行内嵌子表、未选中页签被复活、767px 窄屏、不支持 `:has()` 的浏览器，五类。
- [x] Scope is clearly bounded
      —— 「非目标」五条，含**明确划给 088** 的主题 token 化与**明确另立一项**的窄屏塌陷。
- [x] Dependencies and assumptions identified
      —— 假设节写明视口取值、空白下限 56 的构成、以及「全部列表页」的枚举口径（48+7+3）。

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
      —— US1 普适列表页 / US2 Tabs 内嵌 / US3 长表格不裁切 / US4 查询表单，
      四个故事**各自独立可测**（US1 与 US2 用不同判据，US3 是反向保护）。
- [x] Feature meets measurable outcomes defined in Success Criteria
      —— 逐条实测见 tasks.md T007 与 `measure-fill-height.mjs` 的复跑输出。
- [x] No implementation details leak into specification
      —— 见上文第 1 条的**保留意见**（FR-003/FR-008 属刻意的防回归约束）。

## Notes

- **必须随本清单一起读的三条**（否则会把回填读成前置规划）：
  1. 本项**事后立项**：`f75496a` / `d60ad0f` 两个提交先于本目录存在；
  2. 范围**先窄后全**：Tabs 内嵌 5 张表曾被划为缺口并如实告知，事后补齐（`d60ad0f`）；
  3. 本项**无自动化用例覆盖几何**（jsdom 无布局引擎），证据形态是**浏览器实测 + 可复跑脚本**，
     不是单测——这是判据选择，不是覆盖缺口遗漏（见 tasks.md T013 的遗留说明）。
- 遗留未做 2 项（T012 窄屏塌陷、T013 几何护栏）**已在 `tasks.md` 勾选状态中如实保留为未完成**，
  登记表（`specs/README.md` / `specs/roadmap.md`）亦同口径写明。
