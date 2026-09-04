# Feature Specification: 使用地图视觉样式优化

**Feature Branch**: `067-usage-map-ux`

**Created**: 2026-01-15

**Status**: Draft

**Input**: User description: "优化使用地图视觉样式"

## Clarifications

### Session 2026-01-15

- Q: 节点详情的操作要点（actions）数据从哪来？→ A: 保持现有 desc，弹窗展示完整描述
- Q: 节点悬停动画的交互方式是什么？→ A: 轻量级样式变化（仅改变阴影和边框颜色，无尺寸变化）
- Q: 是否需要支持深色模式或品牌色定制？→ A: 不需要，使用默认主题

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 提升流程图视觉层次感 (Priority: P1)

员工打开使用地图时，希望流程图清晰醒目、层次分明，关键节点和连线一眼可见，减少视觉疲劳。当前节点样式偏扁平、颜色对比度不足，需要增强视觉吸引力。

**Why this priority**: 使用地图的核心价值在于可视化展示，视觉样式直接影响信息传达效率和使用意愿。

**Independent Test**: 可独立验证——打开使用地图，主流程节点清晰可见、颜色对比明显、连线流畅。

**Acceptance Scenarios**:

1. **Given** 使用地图主流程视图，**When** 查看，**Then** 节点有阴影/圆角/渐变等立体效果，视觉层次分明。
2. **Given** 流程图节点，**When** 鼠标悬停，**Then** 节点有放大/高亮等交互反馈。
3. **Given** 流程图连线，**When** 查看，**Then** 连线有箭头标识方向、颜色区分通过/驳回状态。

---

### User Story 2 - 优化节点信息和交互 (Priority: P2)

员工点击节点时，希望看到更详细的操作要点说明，而不是仅跳转页面。需要节点详情弹窗/侧边栏，展示操作指南、注意事项等补充信息。

**Why this priority**: 节点详情能帮助新员工更快理解各环节操作要点，提升使用地图的学习价值。

**Independent Test**: 可独立验证——点击任意节点，弹出详情卡片展示操作要点和页面入口按钮。

**Acceptance Scenarios**:

1. **Given** 流程图某节点，**When** 点击，**Then** 弹出节点详情（标题、描述、操作要点、跳转按钮）。
2. **Given** 节点详情弹窗，**When** 点击"跳转"按钮，**Then** 跳转到对应模块页面。
3. **Given** 节点详情弹窗，**When** 点击关闭/遮罩，**Then** 弹窗关闭。

---

### User Story 3 - 增强状态流转可视化 (Priority: P2)

员工查看状态流转图时，希望不同状态用更明显的颜色区分，关键状态（如待审批、已驳回）有警示标识，流转动作标签更清晰。

**Why this priority**: 状态流转图帮助理解业务对象生命周期，清晰的视觉标识能减少操作错误。

**Independent Test**: 可独立验证——切换到状态流转视图，各状态颜色区分明显、驳回状态有红色警示。

**Acceptance Scenarios**:

1. **Given** 状态流转图，**When** 查看，**Then** 草稿/待审批/已批准/生效中/已完成/已驳回等状态用不同颜色标识。
2. **Given** 已驳回/已终止等异常状态节点，**When** 查看，**Then** 有红色边框或图标警示。
3. **Given** 流转连线，**When** 查看，**Then** 通过/驳回/终止等动作标签清晰可见。

---

### User Story 4 - 优化快捷入口样式 (Priority: P3)

员工使用底部高频操作快捷入口时，希望按钮样式更美观、有图标+文字组合、悬停有反馈，提升点击欲望。

**Why this priority**: 快捷入口是高频操作触达通道，美观的样式能提升使用率。

**Independent Test**: 可独立验证——快捷入口按钮有图标+文字、悬停有颜色/阴影变化。

**Acceptance Scenarios**:

1. **Given** 快捷入口按钮组，**When** 查看，**Then** 每个按钮有图标、文字、圆角背景。
2. **Given** 快捷入口按钮，**When** 鼠标悬停，**Then** 按钮有颜色变化或阴影加深效果。
3. **Given** 快捷入口按钮，**When** 点击，**Then** 跳转到对应页面。

---

### Edge Cases

- 节点描述内容过长：弹窗内文字溢出时显示滚动条或截断省略。
- 移动端：弹窗改为底部抽屉（Drawer）样式，适配小屏幕。
- G6 渲染失败：降级提示保持不变，不影响快捷入口使用。
- 自定义主题：样式优化需兼容现有 Ant Design 主题变量。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 流程图节点必须有视觉增强效果（阴影、圆角、悬停交互）。
- **FR-002**: 节点点击必须弹出详情卡片，展示标题、描述、操作要点、跳转按钮。
- **FR-003**: 状态流转图必须用颜色区分不同状态类别，异常状态（驳回/终止）有红色警示。
- **FR-004**: 快捷入口按钮必须有图标+文字组合，悬停有视觉反馈。
- **FR-005**: 所有样式优化必须兼容响应式布局（移动端/桌面端）。
- **FR-006**: 样式优化不能影响现有功能（节点跳转、角色切换、状态流转切换）。

### Key Entities

- **节点样式（Node Style）**: { shadow, borderRadius, hoverHighlight }（G6 节点配置；悬停仅改变阴影和边框颜色，无尺寸变化）。
- **节点详情（Node Detail）**: { title, description, path }（弹窗展示内容；description 即 FlowNode.desc，无独立 actions 字段）。
- **状态颜色（State Color）**: { DRAFT: gray, PENDING: orange, APPROVED: blue, ACTIVE: green, REJECTED: red, TERMINATED: gray }。
- **快捷按钮（Quick Button）**: { icon, label, hoverBg, hoverColor }。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 节点视觉评分提升——用户调研中"流程图清晰度"评分从当前 6/10 提升至 8/10（抽样 20 人）。
- **SC-002**: 节点详情使用率——打开使用地图后，30% 以上用户点击至少一个节点查看详情。
- **SC-003**: 状态流转可读性——100% 用户能正确识别已驳回/已终止等异常状态（抽样测试）。
- **SC-004**: 快捷入口点击率提升——优化后一周内，快捷入口点击次数较优化前提升 20%。
- **SC-005**: 无回归——所有现有功能（节点跳转、角色切换、状态流转切换）正常工作。

## Assumptions

- 使用 Ant Design 5 默认主题（蓝色系），不支持深色模式或品牌色定制。
- G6 v5 节点样式通过 `node.style` 配置（shadow、borderRadius 等）。
- 节点详情弹窗使用 Ant Design Modal（桌面端）或 Drawer（移动端）。
- 悬停效果通过 G6 的 `node:mouseenter`/`node:mouseleave` 事件实现，仅改变阴影和边框颜色，无尺寸变化。
- 节点详情直接展示 FlowNode.desc 内容，不扩展独立的 actions 字段。
- 移动端适配使用 Ant Design 的响应式断点（useBreakpoint）。
