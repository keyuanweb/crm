# P0-P1 功能路线 SDD 方案总览

**Date**: 2026-08-23 | **Status**: 方案评审稿（spec 已完成，plan/tasks 实现时按 SDD 补齐）

基于业界 CRM（Salesforce/HubSpot/Zoho/纷享销客/销售易）对比的差距分析，P0（6 项）+ P1（3 项）共 9 个功能已完成 spec 方案。

## 功能清单与实施顺序

| # | Feature | 优先级 | 目录 | 核心价值 | 依赖 |
|---|---|---|---|---|---|
| 1 | 邮件营销触达 | P0-首批 | [030-email-marketing](./030-email-marketing/spec.md) | 营销闭环最后一块（模板/群发/追踪） | 031 标签（收件人来源）、026 通知 |
| 2 | 客户标签与细分 | P0-首批 | [031-customer-tags](./031-customer-tags/spec.md) | 标签 + 动态细分，支撑精准营销 | 无 |
| 3 | 全局搜索 | P0-首批 | [032-global-search](./032-global-search/spec.md) | 顶栏跨实体统一搜索 | 无 |
| 4 | 通用多级审批流 | P0-二批 | [033-approval-flow](./033-approval-flow/spec.md) | 可配置多级条件审批，替代单级 | 026 通知、017 工作流 |
| 5 | 客户查重合并 | P0-二批 | [034-customer-merge](./034-customer-merge/spec.md) | 批量查重 + 合并，清理重复数据 | 025 回收站 |
| 6 | 外勤拜访管理 | P0-二批 | [035-field-visit](./035-field-visit/spec.md) | 拜访计划/签到，外勤闭环 | 027 PWA、026 通知 |
| 7 | 在线表单线索收集 | P1 | [036-online-forms](./036-online-forms/spec.md) | 官网表单自动进线索池 | 030 邮件（通知）、031 标签 |
| 8 | 公告与内部协作 | P1 | [037-announcements](./037-announcements/spec.md) | 团队公告 + 评论 @提及 | 026 通知 |
| 9 | 发票管理 | P1 | [038-invoice](./038-invoice/spec.md) | 订单开票 + 状态跟踪 | 013 回款、订单 |

## 章程门禁（共同声明）

各功能 plan 阶段需逐条核对，均满足：
- 原则一 契约优先：contracts/ 定义端点契约（公共提交端点/搜索聚合/审批流接口等）。
- 原则二 分层：Controller→Service→Repository；审批引擎/搜索/追踪独立服务。
- 原则三 安全校验：新增权限点（email:manage/tag:manage/form:manage/invoice:manage 等）入 028 角色权限字典 + @RequirePermission；公开表单端点防注入/频控。
- 原则四 测试优先：每功能含单元 + 集成 + 前端测试（TDD 红→绿）。
- 原则五 简洁：不引重型依赖（无 ES/无营销平台/自研追踪）。

## 实施批次建议

- **批次 1**（P0-首批，营销闭环）：031 标签 → 030 邮件 → 032 全局搜索（030 依赖 031 收件人细分）
- **批次 2**（P0-二批，管理纵深）：033 审批流 → 034 查重合并 → 035 外勤
- **批次 3**（P1，扩展）：036 在线表单 → 037 公告协作 → 038 发票

## 说明

- 每个 feature 实现时走完整 SDD：setup-plan（章程门禁）→ contracts → setup-tasks（红→绿）→ implement → 验证。
- 权限点统一入 028 角色权限字典（RoleConstants），前端按钮显隐 + 后端切面校验。
- 通知统一复用 026 WebSocket + 通知中心。
