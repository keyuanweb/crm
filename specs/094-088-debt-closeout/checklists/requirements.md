# Specification Quality Checklist: 088 收口三笔账（094）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-15
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
      —— ⚠️ **部分不适用**：本项的交付物里**含一个门禁脚本的规则**（R8），它的判据按定义就是源码级的
      （「`column` 是字面量数字且 > 1」）。已确保**规格层**只写「不得写死多列列数」这条**意图**，
      `scanTagEvents` / `attrOf` 等实现手段只出现在 `plan.md` 与 `research.md`。
- [x] Focused on user value and business needs
      —— 用户价值是两条：①窄屏下详情区块不再出现写死列数（3 处存量）；②护栏失败时指对方向（R3 那类误判）。
      两条都写在「背景」的三笔账表里。
- [x] Written for non-technical stakeholders
      —— ⚠️ **不适用**：本项的读者是维护本仓库的开发者。已在背景节用表格把三笔账的「出处/今天的状态/本项做什么」列清。
- [x] All mandatory sections completed
      —— 背景（三笔账 + ②的定性订正）、三个用户故事、FR-094-001~009、SC-094-001~006、非目标、关键实体、假设。

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
      —— **三笔账的处置全部已裁决**：① 与 ③ 由用户 2026-09-15 当面选定（「作废 + 订正记录」「补做 3 处 + 订正 FR-015」），
      ② 交我判断并已在规格里写明订正后的定性。故无待澄清项。
- [x] Requirements are testable and unambiguous
      —— 逐条可判：001（3 处落点写死）／002（A/B 读数 + 禁夸大）／003（边界与两处例外写死）／
      004（R8 判据 + 档位 + 下限 12）／005（破坏留痕）／006（信息二分 + 打印历史读数）／
      007（6 处订正 + 禁类型断言）／008（两处登记）／009（不碰 093 与指针）。
- [x] Success criteria are measurable
      —— SC-094-001 七道门禁 + 用例数下限；002 收尾读数**53 不变**；003 二分信息出现；
      004 三个 `grep` 读数（14 / 0 / 1）；005 6 处可查；006 可见变化如实披露。
- [x] Success criteria are technology-agnostic (no implementation details)
      —— 读数是**源码计数**（`Descriptions` 开标签数、写死多列的处数），不是框架 API 的用法；
      唯一的技术名词（`check-ui.mjs`、`pnpm ui:check`）是**交付物本身**（同 092 的先例）。
- [x] All acceptance scenarios are defined
      —— 三个故事各有一组 Given/When/Then，且互相独立（故事一改代码、故事二读门禁输出、故事三读文档）。
- [x] Edge cases are identified
      —— ①`column={1}` 为何不算违规（最窄档、机制上不可能溢出）；②`{ xs: 1, sm: 2 }` 为何已合规
      （`DEFAULT_COLUMN_MAP` 补齐 md=3，这在 `research.md` §2 有出处）；③「只认单行开标签」的扫描器失效
      （本项**自己踩过**，见 research §3，故定为下限 12 的水位）；④推演与读数不符时以读数为准。
- [x] Scope is clearly bounded
      —— 非目标四条：不做 `items` 现代化、不动 `column={1}`、不做渲染级几何用例、不接 082 与 index.css 清理。
- [x] Dependencies and assumptions identified
      —— 假设节写明 antd 的 clamp 语义、`DEFAULT_COLUMN_MAP`、候选池的源码口径、dev 告警的环境守卫。

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
      —— 001~003↔故事一/SC-094-004；004~005↔故事二前段/SC-094-002；006↔故事二/SC-094-003；
      007↔故事三/SC-094-005；008~009↔SC-094-001 与「不碰别人的工作」。
- [x] User scenarios cover primary flows
      —— 改代码（一）、改护栏（二）、订正记录（三），三类覆盖面互不重叠。
- [x] Feature meets measurable outcomes defined in Success Criteria
      —— SC-094-002/003 是**反假绿**两条硬要求（定向破坏 + 二分信息留痕），直接对应本项的核心质量主张。
- [x] No implementation details leak into specification
      —— 同第一条。

## Notes

- 本项是**收口项**：它不改 088 的任何结论，只清算 088 自己写下的欠账。故规格里**大量引用 088 的原文落点**，
  这是刻意的——订正若不指回原文，「不静默」就无从核验。
- SC-094-002 的「读数 53 不变」需要解释：088 收尾时该数是 53，本项**新增了一条规则却不增加冻结债务**
  （R8 零容忍、不设白名单），这正是 SC-006「白名单单调收缩」不被扰动的证据。

## 复核记录

- 2026-09-15：初稿后逐条自查，修正 **2** 处：
  ① 初稿把「已合规」的 `{ xs: 1, sm: 2 }` 也算进存量违规（沿用 088 原报的 5 处清单）——
     按「是否构成窄屏缺陷」重判后缩为 **3 处真违规**，规格与 `research.md` §2 同步改写；
  ② 初稿把 `MIN_CANDIDATES.R3 = 1` 描述为「假红风险」——**就地订正**为「红色是可辩护的治理，
     错的是失败信息的指向」，并据此把 FR-094-006 从「调下限」改成「改信息」（调下限等于放行）。
