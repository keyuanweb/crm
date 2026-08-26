# Implementation Plan: 更多页面国际化模块

**Branch**: `066-i18n-pages` | **Date**: 2026-08-26 | **Spec**: [spec.md](./spec.md)

## Summary

将 7+ 个核心业务页面的用户可见文案替换为 i18n key（t()），每个页面独立命名空间，通用文案提取到公共命名空间。

## Technical Context

**Language/Version**: TypeScript 5 / React 18（沿用）

**Primary Dependencies**: react-i18next（060 已安装初始化）、zh-CN.ts/en.ts 资源文件

**Storage**: 无后端变更；前端新增 i18n 资源文件

**Testing**: 前端 typecheck/lint/build；手动语言切换验证

**Target Platform**: Web

**Project Type**: 前端文案迁移

**Performance Goals**: 零运行时开销（i18n key 编译时静态分析）

**Constraints**: 不改变中文默认行为；金额格式化不翻译

**Scale/Scope**: 7+ 核心页面 × ~30 文案 = ~210 个 key

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 每页独立命名空间 | ✅ 满足 |
| 原则四：测试优先与质量门禁 | build 通过 | ✅ 满足 |
| 原则五：简洁、可维护与可观测 | 公共文案提取 | ✅ 满足 |

**结论**: 无门禁违规。

## Project Structure

```text
frontend/src/i18n/
├── resources/
│   ├── common.json          # 公共按钮/状态/消息
│   ├── customer.json        # 客户管理页面
│   ├── product.json         # 产品目录页面
│   ├── opportunity.json     # 商机管理页面
│   ├── contract.json        # 合同管理页面
│   ├── ticket.json          # 工单管理页面
│   ├── lead.json            # 线索管理页面
│   └── contact.json         # 联系人管理页面
└── zh-CN.ts / en.ts（注册新命名空间）

frontend/src/pages/*/
├── CustomerListPage.tsx     # 列标题/按钮/消息 → t()
├── CustomerDetailPage.tsx   # 基本信息标签/操作按钮 → t()
├── ProductListPage.tsx      # 同上
├── OpportunityListPage.tsx  # 阶段标签/金额说明 → t()
├── ContractListPage.tsx     # 状态文本/操作按钮 → t()
├── TicketListPage.tsx       # 优先级/Sla 标签 → t()
├── LeadListPage.tsx         # 列标题/认领按钮 → t()
└── ContactListPage.tsx      # 列标题/共享按钮 → t()
```

**Structure Decision**: 每页一个 JSON 资源文件（key 按 `page.section.field` 命名），注册到 zh-CN/en.ts 的对应命名空间。

## Complexity Tracking

无违规，本表留空。
