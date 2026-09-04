# 数据模型：使用地图视觉样式优化

## 节点样式配置（Node Style Config）

| 字段 | 类型 | 说明 |
|---|---|---|
| shadow | string | 节点阴影效果（如 `0 2px 8px rgba(0,0,0,0.15)`） |
| borderRadius | number | 节点圆角半径（像素） |
| hoverScale | number | 悬停时节点缩放比例（如 1.05） |
| hoverHighlight | string | 悬停时边框高亮颜色 |

## 节点详情（Node Detail）

| 字段 | 类型 | 说明 |
|---|---|---|
| title | string | 节点标题 |
| description | string | 节点描述（即 FlowNode.desc，弹窗展示完整内容） |
| path | string? | 页面入口路径（可选） |

## 状态颜色映射（State Color Map）

| 状态 | 颜色 | 说明 |
|---|---|---|
| DRAFT | #8c8c8c | 草稿/初始状态 |
| PENDING | #fa8c16 | 待审批/处理中 |
| APPROVED | #1677ff | 已批准 |
| ACTIVE | #52c41a | 生效中/已完成 |
| REJECTED | #cf1322 | 已驳回/失败 |
| TERMINATED | #8c8c8c | 已终止 |

## 快捷按钮样式（Quick Button Style）

| 字段 | 类型 | 说明 |
|---|---|---|
| icon | ReactNode | 按钮图标 |
| label | string | 按钮文字 |
| hoverBg | string | 悬停时背景色 |
| hoverColor | string | 悬停时文字颜色 |

## 约束

- 所有样式配置为前端常量，不引入后端依赖。
- 样式需兼容 Ant Design 5 主题变量。
- 移动端适配使用响应式断点。
