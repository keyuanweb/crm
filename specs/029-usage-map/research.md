# 研究：使用地图设计

## R1 AntV G6 v5 集成

**决策**: `@antv/g6` v5。用法：
```ts
import { Graph } from '@antv/g6'
const graph = new Graph({ container, data: { nodes, edges }, node: {...}, layout: { type: 'dagre' } })
graph.render()
```
- useEffect 中 init，cleanup 时 destroy()
- 节点点击事件：graph.on('node:click', ...) → navigate(path)
- 布局：dagre（有向分层），适合流程图

## R2 流程定义

**决策**: 前端常量 `types/usageMap.ts`：
- `FLOW_DEFS`: 主流程 + 各角色流程（id/title/role/nodes/edges）
- `QUICK_ACTIONS`: 高频操作（key/label/icon/path）
- 节点：{ id, title, desc, path?, color? }；edges: { source, target }

## R3 角色联动

**决策**: 当前用户默认视图 = 其角色对应流程（SALES→销售流程，SUPPORT→客服流程，ADMIN→全部/主流程）；Tabs 切换全部视图；不依赖 028 菜单过滤（地图是全局导览）。

## R4 渲染降级

**决策**: G6 init/render try-catch，失败显示 Alert"地图渲染失败，请刷新页面"，其余功能（高频入口）仍可用。

## R5 首页入口

**决策**: DashboardPage 欢迎区（标题右侧）加"查看使用地图"按钮（icon: CompassOutlined），跳转 /usage-map。所有登录用户可见（不依赖权限）。
