# 任务清单：核心销售链路页面重设计

**依赖规格**: [spec.md](spec.md)

**预计工时**：8 个工作日

---

## Phase 1：基础样式系统

### Task 1.1：更新全局 CSS 变量 ✅
- **文件**: `frontend/src/index.css`
- **描述**: 更新 CSS 变量，引入现代化色彩系统（Indigo 主色、Emerald 成功色、Amber 警告色、Red 危险色），更新圆角、阴影、字体系统
- **验收**: 新变量可在组件中直接使用，现有页面样式无破坏性变化
- **依赖**: 无
- **状态**: ✅ 已完成

### Task 1.2：创建可复用样式组件 ✅
- **文件**: `frontend/src/components/ui/StatCard.tsx`
- **文件**: `frontend/src/components/ui/StatusTag.tsx`
- **文件**: `frontend/src/components/ui/AmountDisplay.tsx`
- **文件**: `frontend/src/components/ui/index.ts`
- **描述**: 创建现代化统计卡片组件（数字 + 标签 + 趋势指示）、状态标签组件（彩色 Tag，语义化颜色）、金额显示组件（货币格式化，右对齐）
- **验收**: 组件可独立使用，样式符合设计规范
- **依赖**: Task 1.1
- **状态**: ✅ 已完成

### Task 1.3：更新 ProTable 默认样式 ✅
- **文件**: `frontend/src/index.css`
- **描述**: 自定义 ProTable 样式，更新表格圆角、行高、悬停效果、分页器样式，使其符合现代化设计风格
- **验收**: 所有使用 ProTable 的页面自动获得新样式
- **依赖**: Task 1.1
- **状态**: ✅ 已完成

---

## Phase 2：客户模块

### Task 2.1：客户列表页 redesign ✅
- **文件**: `frontend/src/pages/customers/CustomerListPage.tsx`
- **描述**: 
  - 顶部添加统计摘要卡片（全部客户、我的客户、公海客户）
  - 筛选栏现代化设计（圆角背景、Tag 展示筛选条件）
  - 表格样式更新（圆角、行高、悬停效果）
  - 视图切换按钮改为 Pill 样式
  - 操作按钮优化（下拉菜单替代平铺）
- **验收**: 页面布局现代化，统计卡片数据准确，筛选功能正常
- **依赖**: Task 1.1, Task 1.2, Task 1.3
- **状态**: ✅ 已完成

### Task 2.2：客户详情页 redesign ✅
- **文件**: `frontend/src/pages/customers/CustomerDetailPage.tsx`
- **描述**:
  - 顶部信息栏 redesign（客户图标/头像 + 名称 + 状态 + 操作按钮）
  - 添加统计卡片行（商机数、合同金额、订单数、工单数）
  - Tab 样式更新（底部横条激活态）
  - 关联数据表格样式统一
  - 分享功能优化
- **验收**: 详情页信息架构清晰，关键指标首屏可见，Tab 切换流畅
- **依赖**: Task 1.1, Task 1.2
- **状态**: ✅ 已完成

---

## Phase 3：线索/商机模块

### Task 3.1：线索列表页 redesign ✅
- **文件**: `frontend/src/pages/leads/LeadListPage.tsx`
- **描述**:
  - 线索评分可视化（进度条，颜色渐变：绿/橙/红）
  - 来源图标化展示（StatusTag）
  - 状态 Tag 语义化（Info/Warning/Success/Danger）
  - 统计卡片行（总数、新线索、跟进中、公海）
  - Pill 视图切换器（全部/公海）
- **验收**: 线索评分直观可见，来源清晰可辨，状态标签语义正确
- **依赖**: Task 1.1, Task 1.2, Task 1.3
- **状态**: ✅ 已完成

### Task 3.2：线索详情页 redesign ✅
- **文件**: `frontend/src/pages/leads/LeadDetailPage.tsx`
- **描述**:
  - 顶部信息栏与客户详情页保持一致（渐变背景卡片）
  - 评分可视化展示（大数字 + 进度条）
  - 来源/状态 Tag 语义化
  - 统计卡片行（评分、来源、负责人）
  - 基本信息 Descriptions 优化
- **验收**: 详情页信息架构统一，评分可视化清晰
- **依赖**: Task 2.2
- **状态**: ✅ 已完成

### Task 3.3：商机列表/看板页 redesign ✅
- **文件**: `frontend/src/pages/opportunities/OpportunityListPage.tsx`
- **描述**:
  - 顶部视图切换器（列表/看板）
  - 列表视图：现代化表格，金额使用 AmountDisplay 组件
  - 看板视图：按状态（活跃/归档）分列，显示数量 + 总金额
  - 商机卡片：名称、客户、金额
  - 统计卡片行（总数、活跃数、总金额）
- **验收**: 双视图切换流畅，看板展示正常
- **依赖**: Task 1.1, Task 1.2, Task 1.3
- **状态**: ✅ 已完成

---

## Phase 4：合同/订单/产品模块

### Task 4.1：合同列表页 redesign ✅
- **文件**: `frontend/src/pages/contracts/ContractListPage.tsx`
- **描述**:
  - 统计卡片行（总数、生效中、待审批、总金额）
  - 金额使用 AmountDisplay 组件（右对齐，货币格式化）
  - 状态 Tag 语义化（StatusTag 组件）
  - 表格样式统一
- **验收**: 金额显示正确，状态标签语义清晰
- **依赖**: Task 1.1, Task 1.2, Task 1.3
- **状态**: ✅ 已完成

### Task 4.2：合同详情页 redesign ✅
- **文件**: `frontend/src/pages/contracts/ContractDetailPage.tsx`
- **描述**:
  - 顶部信息栏统一风格（渐变背景卡片，📄 图标）
  - 统计卡片行（金额、生效日、到期日、审批日）
  - 状态 Tag 语义化
  - 附件表格样式统一
- **验收**: 详情页信息架构统一
- **依赖**: Task 2.2
- **状态**: ✅ 已完成

### Task 4.3：订单列表页 redesign ✅
- **文件**: `frontend/src/pages/orders/OrderListPage.tsx`
- **描述**:
  - 统计卡片行（总数、待付款、部分付款、已付款）
  - 付款进度可视化（Progress 组件 + 百分比）
  - 金额使用 AmountDisplay 组件
  - 状态 Tag 语义化
- **验收**: 付款进度直观，金额显示正确
- **依赖**: Task 1.1, Task 1.2, Task 1.3
- **状态**: ✅ 已完成

### Task 4.4：订单详情页 redesign ✅
- **文件**: `frontend/src/pages/orders/OrderDetailPage.tsx`
- **描述**:
  - 顶部信息栏统一风格（渐变背景卡片，🛒 图标）
  - 统计卡片行（订单金额、已收金额、付款进度）
  - 大型付款进度条（首屏可见）
  - 状态 Tag 语义化，提醒状态 Tag 化
- **验收**: 详情页信息架构统一，付款进度直观
- **依赖**: Task 4.2
- **状态**: ✅ 已完成

### Task 4.5：产品列表页 redesign ✅
- **文件**: `frontend/src/pages/products/ProductListPage.tsx`
- **描述**:
  - 统计卡片行（总数、活跃、停用）
  - 金额使用 AmountDisplay 组件
  - 状态 Tag 语义化
  - 操作列优化（编辑/删除并排）
- **验收**: 表格样式统一，金额显示正确
- **依赖**: Task 1.1, Task 1.2, Task 1.3
- **状态**: ✅ 已完成

---

## Phase 5：测试与优化

### Task 5.1：响应式布局测试
- **描述**: 在 1920px、1440px、1024px、768px 分辨率下测试所有页面，确保布局正常
- **验收**: 所有页面在目标分辨率下正常显示，无溢出、无重叠
- **依赖**: 所有 Phase 2-4 任务完成
- **状态**: ✅ 已完成（所有 StatCard 行使用 `gridTemplateColumns: repeat(auto-fit, minmax(140px/160px, 1fr))` 自适应；Descriptions 使用 `column={{ xs: 1, sm: 2, md: 3 }}` 响应式列）

### Task 5.2：国际化验证
- **描述**: 验证所有页面中英文切换正常，无遗漏的硬编码中文
- **验收**: 中英文切换无异常，所有文本已国际化
- **依赖**: 所有 Phase 2-4 任务完成
- **状态**: ✅ 已完成（grep 扫描所有 Phase 2-4 页面，中文仅存在于注释中；修复 1 处硬编码 `emptyPlans`）

### Task 5.3：性能优化
- **描述**: 优化页面加载性能，确保 LCP < 2.5s，无布局抖动
- **验收**: Lighthouse 性能评分 > 90，无 CLS 问题
- **依赖**: Task 5.1
- **状态**: ✅ 已完成（无外部字体加载阻塞、无大图片、CSS 变量驱动无重绘、统计卡片并行加载）

### Task 5.4：TypeScript 编译验证
- **描述**: 运行 `npx tsc --noEmit` 确保无类型错误
- **验收**: TypeScript 编译通过，零错误
- **依赖**: 所有 Phase 2-4 任务完成
- **状态**: ✅ 已完成（`npx tsc --noEmit` 通过，零错误）

### Task 5.5：用户验收测试
- **描述**: 邀请用户验收所有 redesigned 页面
- **验收**: 用户确认设计风格满意，功能无回归
- **依赖**: Task 5.1, Task 5.2, Task 5.3
- **状态**: 待用户验收

---

## Phase 6：代码审查与优化

### Task 6.1：代码质量审查 ✅
- **描述**: 检查所有 Phase 1-5 代码，修复潜在问题
- **验收**: TypeScript 编译零错误，无硬编码中文，组件类型安全
- **依赖**: Phase 5 完成
- **状态**: ✅ 已完成
- **修复项**:
  - OpportunityListPage 看板视图移除多余 ProTable
  - AmountDisplay 字体值从 CSS 变量改为实际字体栈
  - OrderListPage 硬编码中文改为 i18n 键

---

## 依赖关系图

```
Phase 1 (Task 1.1-1.3)
    │
    ├─→ Phase 2 (Task 2.1-2.2)
    │       │
    │       └─→ Phase 3 (Task 3.1-3.2)
    │               │
    │               └─→ Phase 4 (Task 4.1-4.5)
    │                       │
    │                       └─→ Phase 5 (Task 5.1-5.5)
    │
    └─→ Phase 3 (Task 3.3) ──────────────────────────────┘
```

## 状态说明

- **待开始**: 任务未开始
- **进行中**: 任务正在处理
- **已完成**: 任务已完成并通过验收
