# Tasks: 员工使用地图

**Input**: Design documents from `/specs/029-usage-map/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/usage-map.md

**Tests**: 章程原则四要求测试先于实现（红→绿），本功能含前端渲染测试。

## Phase 1: 基础设施

- [x] T001 [P] [US1] 前端：安装 `@antv/g6`（5.1.1）依赖。

## Phase 2: 前端测试先行（TDD 红）

- [x] T002 [P] [US1] 前端：编写 `frontend/src/pages/map/UsageMapPage.test.tsx`——mock G6（vi.hoisted Graph 构造/render/destroy），断言：页面标题与流程 Tabs 渲染、高频入口按钮渲染、G6 渲染失败显示降级提示（findByText 异步等待延迟创建）。
- [x] T003 [P] [US1] 前端：DashboardPage.test.tsx 追加首页入口测试——欢迎区"查看使用地图"按钮存在。

## Phase 3: 前端实现

- [x] T004 [P] [US1] 前端：`types/usageMap.ts`——FlowDef/FlowNode/QuickAction 类型 + MAIN_FLOW（主流程 7 节点）+ ROLE_FLOWS（销售/客服/管理员操作链）+ QUICK_ACTIONS（6 个高频操作）。
- [x] T005 [US1] 前端：新增 `pages/map/UsageMapPage.tsx`——G6 v5 dagre 流程图（节点点击 navigate、角色 Tabs 切换、渲染 try-catch 降级 Alert、延迟创建 + cancelled 标记规避 StrictMode 双挂载竞态、render 异步后 fitView）+ 底部高频操作按钮组。（依赖 T001/T004）
- [x] T006 [US1] 前端：`DashboardPage.tsx` 欢迎区加"查看使用地图"按钮（CompassOutlined → navigate('/usage-map')）；`App.tsx` 注册路由 /usage-map。（依赖 T005）

## Phase 4: 验证与收尾

- [x] T007 前端：`pnpm run typecheck` + `lint` + `test` 全量通过（36 tests：新增 UsageMapPage 3 测试 + 首页入口 1 测试）。
- [x] T008 [P] 手动冒烟：首页入口 → 使用地图 → G6 canvas 渲染（7 节点主流程）→ 角色 Tabs 切换（销售/客服/管理员）画布重建正常 → 高频入口渲染；修复 G6 v5 在 StrictMode dev 下 destroy 后内部异步 draw 报错的竞态（延迟创建 + cancelled + try-catch destroy）；按用户反馈二次修复：① 去掉 fitView（移除"刚开始的放大/缩放效果"，保持自然大小）；② 恢复 `graph.render().then()` promise 链（去掉 fitView 后需保留以触发异步绘制，否则 canvas 空白）——canvas 像素采样验证 coloredRatio 0.828（节点）+ whiteRatio 0.172（白色文字 14px 清晰渲染）；③ 去掉 drag-canvas/zoom-canvas behaviors（不可拖动）；④ 容器 overflowX auto + minWidth 按节点数动态（自然宽度超出横向滚动）；冒烟零错误。
- [x] T009 [P] [US1] 前端：右上角 Header（通知铃铛旁）加"使用地图"按钮（CompassOutlined，navigate('/usage-map')）——所有登录用户可见的全局入口；App.tsx 加 Button/CompassOutlined import；typecheck/36 tests 通过，冒烟实测：Header 入口存在、点击跳转 /usage-map 渲染正常、零错误。
- [x] T010 [P] [US1] 前端：按用户反馈三次修复流程图——① 根因：G6 v5 的 `ports` 配置格式错误导致节点样式（含 labelText）渲染异常、文字不显示 → 移除 ports（dagre 布局自动锚点）；② 节点改白描边（stroke #fff lineWidth 2）+ 圆角 10 + 尺寸 170×56 + 文字 15px 加粗白字（清晰醒目）；③ 恢复 `graph.render().then(() => graph.fitView())`（适配完整显示，无横向滚动）；容器恢复 width 100%；canvas 像素采样验证：coloredRatio 0.69（节点/边）+ whiteRatio 0.307（白色文字+描边，文字清晰可见）；typecheck/36 tests 通过，冒烟零错误。
- [x] T011 [P] [US1] 前端：按用户反馈改节点文字为黑色——节点背景改白底（fill #ffffff）+ 彩色描边（stroke 各节点 color，lineWidth 2.5）+ 黑字（labelFill #1f1f1f，15px 加粗）；canvas 像素采样验证：darkRatio 0.190（黑色文字）+ lightRatio 0.596（白底节点）+ coloredRatio 0.215（彩色描边/连线）；文字清晰可读、颜色由描边区分；typecheck/36 tests 通过，冒烟零错误。
- [x] T012 [P] [US1] 前端：按用户反馈收窄画布区域——单行流程图（LR dagre）内容高约 92px 却在 420px 画布中大量上下留白 → 容器高度 420→160px；实测：canvasH 160、内容垂直 30~122（高 92px 占 57%，上下对称留白居中）、图完整显示、零错误。
- [x] T013 [P] [US1] 前端：按用户反馈图再缩小且无放大效果——① 节点 170×56→125×42、文字 15→13px、描边 2.5→2px、圆角 10→8；② 移除 fitView（dagre 自然大小渲染，无缩放放大效果）；③ 画布高 160→130；实测：canvas 1182×130、内容 748×54（占宽 63% 高 42%，图紧凑自然显示）、零错误、测试 3/3 通过。
- [x] T014 [P] [US1] 前端：按用户反馈增加状态流转视图（含审核）——① `types/usageMap.ts` 新增 `STATE_FLOWS`（5 个状态机：合同状态流转含审批（草稿→待审批→已批准/已驳回→生效中→已完成/已终止）、报价单状态流转（含驳回重提）、工单状态流转（待处理→处理中→已解决→已关闭，含退回）、商机阶段流转（初次接触→需求→方案→谈判→赢单/输单）、线索状态流转（新→跟进中→转化/废弃））；② `FlowDef.edges` 加 label（流转动作）；③ UsageMapPage 一级 Tabs 拆"业务流程/状态流转"，状态流转内二级 Tabs 选状态机；④ G6 边样式回调：通过类（通过/成交/转化/结项/关闭）绿色、驳回类（驳回/失败/废弃/退回/无效）红色虚线、其他灰；边带动作标签（labelBackground 白底）；⑤ 修复 useEffect cleanup 位置（return 误移出导致 TS1128）与依赖数组改 [activeDef]；实测：状态流转 Tab 正常、合同/报价状态机含通过绿/驳回红（redRatio 0.015 + greenRatio 0.087）、零错误、typecheck/测试通过。
- [x] T015 [P] [US1] 前端：按用户反馈画布高度 150→200px 且图横向垂直居中——G6 v5 的 centerView() 未生效 → 改用 `graph.getData()` 读节点坐标计算包围盒 + `graph.translateBy([dx,dy])` 平移居中（保持原缩放不放大）；实测：canvasH 200、内容中心 (588,106) ≈ 视口中心 (591,100)（水平差 3px、垂直差 6px 居中）、零错误、测试 3/3 通过。
- [x] T016 [P] [US1] 前端：按用户反馈消除连线重叠——边类型 line 改 **cubic 曲线**（`edge.type: 'cubic'` + `curveOffset: 24`），G6 v5 对反向边（如待审批↔已驳回）与并行边自动分配相反曲率分离；驳回虚线/通过绿等样式保留；实测：状态流转-合同视图渲染正常（含反向边）、零错误、测试 3/3 通过。
- [x] T017 [P] [US1] 前端：按用户反馈图一开始就居中且无移动动画——Graph 配置加 `animation: false`（禁用 dagre 布局动画与平移动画）；实测：快速连续位置采样 3 次均为 (588,106) 完全一致（无移动动画）、图渲染即居中（cx≈视口中心、cy≈视口中心）、零错误、测试 3/3 通过。
- [x] T018 [P] [US1] 前端：按用户反馈修复"修改后重提"反向边重叠——G6 平行边自动分离不覆盖严格反向边（cs2→cs6 与 cs6→cs2）→ 构造 edges 数据时运行时检测反向对，后出现的反向边 `curve: -1`（其余 curve: 1）；curveOffset 改按边回调 `22 * (d.data.curve ?? 1)`（反向边曲率相反）；实测：合同状态流转中红色线垂直跨度 84px（驳回与修改后重提两线上下分离不重叠）、零错误、测试 3/3 通过。

## Dependencies & Execution Order

- T001（依赖安装）。
- T002/T003 可并行，均为红阶段；依赖 T001。
- T004 无依赖；T005 依赖 T001/T004；T006 依赖 T005。
- Phase 4 在所有实现完成后执行。

## Notes

- G6 v5 API（Graph 构造 + render().then + fitView + destroy）。
- 流程定义前端常量；节点 path 与现有路由对齐。
- StrictMode dev 双挂载竞态：延迟创建 + cancelled 标记规避（生产单挂载无此问题）。
- 渲染失败降级 Alert；首页入口所有登录用户可见。
