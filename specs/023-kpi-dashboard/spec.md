# Feature Specification: 大屏数据看板（KPI 大屏）

**Feature Branch**: `023-kpi-dashboard`

**Created**: 2026-08-23

**Status**: Draft

**Input**: User description: "大屏数据看板（KPI大屏）：面向管理层的全屏可视化看板，聚合漏斗/排行/健康度/统计指标，深色大屏风格自动刷新"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 全屏 KPI 大屏 (Priority: P1)

管理员打开大屏看板页（全屏、深色科技感风格），一屏展示核心经营指标：商机总数/金额合计/赢单率/客户数/本月新增、销售漏斗、团队排行、客户健康度分布、智能建议摘要、近 30 天趋势。大屏自动刷新（默认 60 秒），适合投放到办公区大屏或管理层会议。

**Why this priority**: 管理层需要一个"一屏尽览"的经营视图，无需操作即可看到公司整体经营状况；大屏是 CRM 数据价值的对外呈现，也是汇报场景的刚需。

**Independent Test**: 可独立验证——打开大屏页，所有区块在无交互下自动渲染并显示真实聚合数据；可全屏展示。

**Acceptance Scenarios**:

1. **Given** 系统有商机/客户/排行/健康度数据，**When** 打开大屏看板，**Then** 各区块（KPI 卡、漏斗、排行、健康度分布、建议摘要）自动渲染显示真实数据。
2. **Given** 大屏打开超过刷新周期，**When** 等待自动刷新，**Then** 数据自动更新（无需手动操作）。
3. **Given** 大屏处于全屏模式，**When** 查看，**Then** 布局适配大屏分辨率（无滚动条、区块均匀分布）。

### User Story 2 - 大屏数据聚合接口 (Priority: P1)

后端提供单一聚合接口，一次性返回大屏所需数据（KPI 指标、漏斗、排行 TopN、健康度分布、建议摘要、趋势），减少前端多次请求。

**Why this priority**: 大屏区块多，若前端逐块请求会频繁轮询；单一接口 + 后端缓存提升效率与稳定性。

**Independent Test**: 可独立验证——调用聚合接口一次返回全部区块数据。

**Acceptance Scenarios**:

1. **Given** 大屏页加载，**When** 调用聚合接口，**Then** 一次返回 KPI/漏斗/排行/健康度/建议摘要/趋势全部数据。
2. **Given** 聚合接口响应，**When** 校验各区块数据，**Then** 与既有仪表盘/排行/建议接口数据一致。

### User Story 3 - 健康度分布与趋势（可选） (Priority: P2)

大屏展示客户健康度分布（红/黄/绿数量）与近 30 天商机金额趋势（按日折线）。

**Why this priority**: 健康度分布让管理层快速感知客户整体质量；趋势图展示经营走向。

**Independent Test**: 可独立验证——有健康度数据与历史商机时，分布与趋势正确渲染。

**Acceptance Scenarios**:

1. **Given** 系统有不同健康度客户，**When** 查看大屏健康度分布区块，**Then** 显示红/黄/绿三色数量。
2. **Given** 系统有近 30 天商机数据，**When** 查看趋势区块，**Then** 按日展示商机数量/金额趋势。

### Edge Cases

- 无数据时各区块显示占位（0 值或空态）而非报错。
- 大屏自动刷新失败时保留上次数据并提示（不白屏）。
- 数据权限：大屏面向管理层，仅 ADMIN 可访问（或按既有 dashboard 权限）。
- 大屏分辨率适配：1024/1440/1920 宽度下布局不溢出。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统必须提供大屏聚合接口，一次返回：核心 KPI（商机数/金额/赢单率/客户数/本月新增）、销售漏斗、团队排行 TopN、客户健康度分布、智能建议摘要、近 30 天商机趋势。
- **FR-002**: 大屏页必须以全屏深色风格渲染各区块（KPI 卡、漏斗、排行、健康度分布、建议摘要、趋势），无操作自动展示。
- **FR-003**: 大屏必须支持自动刷新（默认 60 秒，可配置），刷新失败保留上次数据并提示。
- **FR-004**: 大屏必须支持进入/退出全屏模式。
- **FR-005**: 大屏布局必须适配常见大屏分辨率（≥1024px），无横向滚动条。
- **FR-006**: 大屏数据必须与既有统计接口一致（复用 dashboard/leaderboard/健康度/建议数据源）。
- **FR-007**: 大屏访问必须遵循数据权限（ADMIN 或按既有 dashboard 权限）。
- **FR-008**: 大屏聚合接口必须支持后端缓存（复用 Redis 5 分钟），降低轮询压力。

### Key Entities

- **KPI 大屏聚合（KpiBoard）**: 派生聚合 DTO（复用 DashboardStats、LeaderboardItem、健康度分布、SuggestionSummary、趋势），实时计算 + Redis 缓存。
- **大屏页面（KpiDashboardPage）**: 前端全屏组件（深色主题 + 自动刷新 + 全屏切换）。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 大屏聚合接口一次返回全部区块数据（≥6 类），响应 ≤ 1 秒。
- **SC-002**: 大屏各区块数据与既有接口一致率 100%（抽查 10 项）。
- **SC-003**: 自动刷新周期内数据更新正确（60 秒后看到新数据，抽查 3 次）。
- **SC-004**: 大屏在 1920×1080 与 1440×900 分辨率下无横向滚动条、区块完整显示。
- **SC-005**: 无数据时各区块显示占位不报错（抽查 5 区块）。

## Assumptions

- 大屏聚合接口复用现有 DashboardStatsService/TeamLeaderboardService/SuggestionService 的聚合逻辑，组合输出，不重复计算。
- 健康度分布基于 018 客户健康度（Customer360Service）统计红/黄/绿数量（抽查最多 200 个客户）。
- 近 30 天趋势基于 sales_opportunity.created_at 按日聚合数量/金额。
- 大屏仅 ADMIN 可访问（管理层视图）。
- 自动刷新 60 秒可配置；Redis 缓存 5 分钟。
