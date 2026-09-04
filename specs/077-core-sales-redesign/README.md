# 核心销售链路页面重设计

## 概述

将核心销售链路（客户、线索、商机、合同、订单、产品）的 10 个页面重设计为现代化企业级风格，参考 Linear/Vercel/Airbnb 的设计语言。

## 设计方向

- **风格**: 现代化企业级（Linear/Vercel/Airbnb）
- **主色**: Indigo 500 (`#6366f1`)
- **圆角**: 8px（组件）、12px（弹窗）
- **字体**: Inter（英文）、PingFang SC（中文）

## 文件结构

```
specs/077-core-sales-redesign/
├── spec.md          # 功能规格文档
├── tasks.md         # 任务清单
└── README.md        # 本文件
```

## 第一批范围（10 个页面）

| 模块 | 页面 | 文件 |
|---|---|---|
| 客户 | 列表页 | `frontend/src/pages/customers/CustomerListPage.tsx` |
| 客户 | 详情页 | `frontend/src/pages/customers/CustomerDetailPage.tsx` |
| 线索 | 列表页 | `frontend/src/pages/leads/LeadListPage.tsx` |
| 线索 | 详情页 | `frontend/src/pages/leads/LeadDetailPage.tsx` |
| 商机 | 列表/看板页 | `frontend/src/pages/opportunities/OpportunityListPage.tsx` |
| 合同 | 列表页 | `frontend/src/pages/contracts/ContractListPage.tsx` |
| 合同 | 详情页 | `frontend/src/pages/contracts/ContractDetailPage.tsx` |
| 订单 | 列表页 | `frontend/src/pages/orders/OrderListPage.tsx` |
| 订单 | 详情页 | `frontend/src/pages/orders/OrderDetailPage.tsx` |
| 产品 | 列表页 | `frontend/src/pages/products/ProductListPage.tsx` |

## 实施阶段

| 阶段 | 内容 | 工时 | 状态 |
|---|---|---|---|
| Phase 1 | 基础样式系统 | 1 天 | 待开始 |
| Phase 2 | 客户模块 | 2 天 | 待开始 |
| Phase 3 | 线索/商机模块 | 2 天 | 待开始 |
| Phase 4 | 合同/订单/产品模块 | 2 天 | 待开始 |
| Phase 5 | 测试与优化 | 1 天 | 待开始 |

## 相关规格

- [072 后台布局重设计](../072-backend-layout-redesign/spec.md) - 基础布局框架（已完成）
- [075 页面国际化](../075-page-i18n/spec.md) - 国际化基础（进行中）
- [060 i18n 国际化](../060-i18n/spec.md) - 国际化基础（已完成）
