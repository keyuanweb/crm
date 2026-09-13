# Specification Quality Checklist: 验证门禁转绿（陈旧集成测试失败背后的真实缺陷）

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-13
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
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
- [x] No implementation details leak into specification

## Notes

**结论：全部通过。** 三处澄清已于 2026-09-13 由项目负责人裁决（结论见 `spec.md`「澄清」节），无遗留标记。

### 澄清裁决的落地痕迹（供 planning 直接采用）

| # | 议题 | 裁决 | 由此**新增**的需求 |
|---|---|---|---|
| Q1 | 契约 422 vs. 章程原则三的校验约束 | **B**：保留 `@NotBlank`，在该端点把校验失败映射为 422 `CLOSE_RESULT_REQUIRED` | **FR-V15**——其他端点的 400 语义**不得**改变，且须有测试钉住（这是方案 B 相对方案 A 多出来的风险，必须显式覆盖） |
| Q2 | 投递记录的落地形态 | **A**：派发时落"投递中"记录，结束时原地更新为终态 | FR-V05/V07/V08 随之硬化（新增状态值、定义中断残留归宿、一次投递只对应一条记录） |
| Q3 | 范围边界 | **只做这 3 个缺陷**，不纳入相邻技术债 | **FR-V14** 由"不引入迁移"升级为**范围守卫**——确需迁移时必须停下重新裁决，不得就地扩围 |

### 逐项复核时的两处口径声明（订正不静默）

1. **`No implementation details` 按"需求与验收标准正文"判定为通过**，但本文件**整体**并非无实现细节——`spec.md` 的「Input」（逐字引用的用户输入）与「背景」节含文件名与行号（如 `AuthService.java:91`）。这是**本仓库的既有惯例**：003 与 083 的先例都把"缺陷在哪个文件哪一行"作为**取证**写入规格，理由是断言必须可被独立复核。故判定通过，但声明该偏差，以免被读成"规格里没有任何技术细节"。

2. **`Success criteria are technology-agnostic` 按"不含框架/语言/工具名"判定为通过**，但 SC-V01/V02 含"构建校验""覆盖率门槛"等**工程过程**术语。它们描述的是章程原则四所要求的**门禁结果**，不是实现手段（未指定工具、插件或阈值表达式）。同样声明口径。

### 基线证据（已由本轮实测回填，不再是引用旧记录）

- 单元测试 **551 例 / 0 失败**；集成测试 **275 例 / 4 失败 / 0 错误**，失败集**恰好** 4 例、**无第 5 例**（原文见 `spec.md`「假设」）。
- **`jacoco:check` 在本次构建中零命中**——实测证实覆盖率门槛从未被判定，本规格的核心判断成立。
- **第一次重跑作废并已排除**：在开发后端正在运行的机器上，构建会因该进程占用 `target/*.jar` 而在打可执行包一步失败，**根本没跑到集成测试**。该环境噪声已写入 `spec.md`「假设」，验收时须显式规避——否则会把"没跑到集成测试"误读成"集成测试通过"。
