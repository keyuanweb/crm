# Tasks: 体验优化批次

**Input**: Design documents from `/specs/065-ux-optimizations/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, contracts/

**状态**: 本批实现已完成（提交记录见各 Task），此表用于记录与核对。

## US1 首页销售预测去重（P0）

- [x] T001 后端 computeForecast 按阶段聚合（提交 d927645）
- [x] T002 前端 aggregateForecast 兜底去重（提交 6982714）
- [x] T003 DashboardStatsServiceTest 通过

## US2 客户详情页体验（P0）

- [x] T004 详情页编辑入口 + Modal + invalidate 刷新（提交 93879ec）
- [x] T005 头部增强（ID/电话/邮箱可点击/健康度徽章）
- [x] T006 基本信息 Descriptions 响应式

## US3 产品页多币种价集成（P0）

- [x] T007 编辑 Modal 集成多币种价格（加载/增删改/差集同步，提交 0235012）
- [x] T008 表单响应式（xs/sm）

## US4 整体布局（P1）

- [x] T009 内容区限宽居中 + 滚动条美化 + 卡片间距统一（提交 5d8e4cd）
- [x] T010 桌面侧栏折叠开关
- [x] T011 404 页（NotFoundPage，双层接入，提交 527c1e0）
- [x] T012 路由切换滚动复位 + 淡入过渡
- [x] T013 限宽 1680 + padding 调整（避免过度缩进，提交 d4bdb4b）

## US5 文案与显示修正（P1）

- [x] T014 i18n 插值 {{name}}/{{year}} 修正（提交 a031e95）
- [x] T015 客户列表完整显示手机号/邮箱（提交 01c8eee）

## 验证

- [x] T016 后端 mvn verify 通过（预测聚合无回归）
- [x] T017 前端 typecheck/lint/build 通过
- [x] T018 roadmap 065 标记 [x]

## Notes

- 客户列表完整显示为用户确认的安全权衡
- 导出路径仍对非管理员脱敏（063 保留）
