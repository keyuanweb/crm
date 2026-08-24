# Tasks: 前端体验优化模块

**Input**: Design documents from `/specs/039-dashboard-ux/`

**Prerequisites**: plan.md (required), spec.md (required), research.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel
- **[Story]**: US1/US2

---

## Phase 1: 用户故事 1 - 菜单切换无闪烁 (P0)

**Goal**: 页面 chunk 预加载 + startTransition + 同高骨架 fallback。

**Independent Test**: 登录后点击菜单，内容区无空白/转圈闪现。

### 实现

- [x] T001 [US1] App.tsx 定义 PRELOAD_PAGES（全部 51 个懒加载页面 import 函数）in `frontend/src/App.tsx`
- [x] T002 [US1] 登录完成（booted）后立即调用 preloadPages()（不等 requestIdleCallback，避免浏览器忙时不触发）in `frontend/src/App.tsx`
- [x] T003 [US1] 菜单 onClick 用 startTransition 包裹 navigate，Suspense 挂起保持旧 UI in `frontend/src/App.tsx`
- [x] T004 [US1] Suspense fallback 改为 PageSkeleton（与内容区同高灰条骨架，非居中 Spin）in `frontend/src/App.tsx`
- [x] T005 [US1] preloadPages 捕获异常（预加载失败静默，点击时按需加载兜底）in `frontend/src/App.tsx`

**Checkpoint**: US1 可用——菜单切换无闪烁

---

## Phase 2: 用户故事 2 - 首页客户分析卡片布局 (P0)

**Goal**: 客户分析卡自然高度 + 响应式栅格 + 公告卡弹性填充。

**Independent Test**: 桌面/窄屏下客户分析卡统计项排列整齐。

### 实现

- [x] T006 [US2] 客户分析卡取消 flex:1 拉伸（内容少时不再顶部留白），改自然高度 in `frontend/src/pages/stats/DashboardPage.tsx`
- [x] T007 [US2] 卡内统计项 Col span=8 改 xs=24/sm=8 响应式（窄屏垂直堆叠）in `frontend/src/pages/stats/DashboardPage.tsx`
- [x] T008 [US2] AnnouncementCard body flex:1 + overflow:auto，flex 列中弹性填充 in `frontend/src/components/AnnouncementCard.tsx`

**Checkpoint**: US2 可用——客户分析卡布局稳定

---

## Phase 3: 收尾与验证

- [x] T009 Frontend typecheck / lint / build 通过
- [x] T010 手动验证（切换 10 次 0 闪烁；桌面/窄屏客户分析卡无挤压）
- [x] T011 更新 roadmap 039 标记 `[x]`

**Checkpoint**: 模块完整可用

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (US1)**: 无依赖（独立文件 App.tsx）
- **Phase 2 (US2)**: 无依赖（独立文件 DashboardPage/AnnouncementCard）
- **Phase 3**: 依赖 US1/US2 完成

### Parallel Opportunities

- US1 与 US2 文件不同（App.tsx vs DashboardPage/AnnouncementCard），可并行

## Notes

- 预加载用 `import()` 幂等性：首次下载缓存、后续立即 resolve，无副作用
- 本模块纯前端，无后端/契约/迁移变更
- 提交规范：Conventional Commits
