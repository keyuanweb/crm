# 数据模型：使用地图

## FlowDef（前端常量）

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 流程标识（main/sales/support/admin） |
| title | string | 流程名 |
| role | string? | 关联角色（SALES/SUPPORT/ADMIN；主流程无） |
| nodes | FlowNode[] | 节点列表 |
| edges | { source, target }[] | 连线（source/target 为节点 id） |

## FlowNode

| 字段 | 类型 | 说明 |
|---|---|---|
| id | string | 节点 id |
| title | string | 节点名 |
| desc | string | 操作要点 |
| path | string? | 页面入口（点击跳转；无则仅展示） |
| color | string? | 节点主色（默认蓝） |

## QuickAction

| 字段 | 类型 | 说明 |
|---|---|---|
| key | string | 唯一键 |
| label | string | 名称 |
| icon | ReactNode | 图标 |
| path | string | 跳转路径 |

## 约束

- 无后端表；纯前端常量，与现有路由路径对齐。
