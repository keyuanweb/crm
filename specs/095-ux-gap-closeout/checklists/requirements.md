# Specification Quality Checklist: 067/068 真缺口收口（095）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-15
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [ ] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [ ] No implementation details leak into specification

## Notes

**三项刻意未勾，理由如实列在此处（不勾＝不满足，不粉饰）：**

1. **Focused on user value and business needs** —— 本项是**收口类**（把 067/068 登记清扫盘出的真缺口做完、并把 6 处登记错误就地订正），它的价值是**清偿既有欠账 + 消灭规格层的自相矛盾**，不是新增用户可见能力。`spec.md` 的「背景」一节写的是**账本**（谁的账、几条、怎么来的），不是用户旅程。用户故事一/二有真实用户视角（管理员搜索、看详情、删部门），但故事四（订正收口）**没有**用户视角——这是本项性质决定，不是疏漏。

2. **No [NEEDS CLARIFICATION] markers remain** —— 已勾，但**须说明其成立方式**：本项**不是**靠「信息足够完备、无疑问」达成的，而是**四项边界已由用户逐项裁决**（T033/T034 订正、T030 纳入、067 的 T001 订正、展开态行为变化），裁决记录见 `spec.md` 的「本项做什么」表与 `research.md`。**若把这四项读成「规格自己消化的」，是误读。**

3. **No implementation details leak into specification** —— **不成立，且是有意的**。本项是**改造类**规格，`spec.md` 的 FR 里点名了文件路径（`frontend/src/pages/departments/DepartmentListPage.tsx` 等）、门禁规则号（R1/R6/R7/R8）、以及既有的组件名（`PageState`、`Grid.useBreakpoint`）。理由是：本项的存在意义就是**在既有的、已被门禁约束的具体落点上补做**，抽掉这些坐标后需求退化成「把 UX 做好点」，**反而不可判定**。这与 `/speckit-specify` 模板对「非技术干系人」的默认假设**冲突**——冲突是刻意的，写在此处备查，**不**声称符合。

**另记（不属于本清单的 12 项，但读这份规格时必须知道）：**

- **`data-model.md` 与 `contracts/` 不产出**，理由是**不新增也不修改任何数据实体、不改任何端点**（唯一取数处复用既有 `GET /departments/tree`）。取舍记在 `spec.md` 的「关键实体」与 `plan.md` 的「结构决策」，与 092 的先例一致。**不要**把缺这两个文件读作规格不完整。
- **测试不是 TDD 顺序**：实际编写顺序是**先实现、后补用例**（沿用 087/088/092 的既有做法），故**不得**声称走过 spec-first / 测试先行。守的是 FR-095-011 的**定向破坏留痕**——它证明护栏**有牙齿**，不证明「红先出现」。
- **本规格的「假设」一节不是泛泛而谈**，其中三条是**会让人踩坑的硬约束**（antd 5.22.0 的 `destroyOnClose` 拼写、jsdom + `matchMedia` 恒假导致桌面分支默认跑不到、i18n 缺键直接抛错），读实现前应先读该节。
