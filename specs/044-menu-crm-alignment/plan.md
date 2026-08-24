# Implementation Plan: 菜单对标成熟 CRM 调整与规划模块

**Branch**: `044-menu-crm-alignment` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

## Summary

按 043 差距分析对标成熟 CRM 调整菜单：统一命名与分类（商机/销售机会合并为"商机"、外勤入销售、客户成功域独立等），并将 17 个规划模块以 disabled"规划中"占位项纳入对应分组，形成完整功能地图。

## Technical Context

**Language/Version**: TypeScript 5 / React 18（沿用）

**Primary Dependencies**: antd Menu（disabled 占位项）

**Storage**: 无（纯前端）

**Testing**: 前端 typecheck / lint / build；人工验收（命名/占位项/移动端）

**Target Platform**: Web（桌面分组 + 移动 <768px 拍平）

**Project Type**: 既有 Web 应用前端调整

**Constraints**: 不改后端/契约/迁移；已实现模块入口不丢；占位项必须 disabled 防 404

**Scale/Scope**: 单文件 `frontend/src/App.tsx`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则二：分层架构与关注点分离 | 表现层纯净 | ✅ 满足（纯导航配置） |
| 原则五：简洁、可维护与可观测 | 结构清晰 | ✅ 满足（占位项统一 planned 标志，不建路由） |

**结论**: 无门禁违规。

## Project Structure

```text
specs/044-menu-crm-alignment/
├── spec.md / plan.md / tasks.md / research.md / quickstart.md
└── checklists/requirements.md

frontend/src/App.tsx  # 路由分组重命名/重分类 + 占位项（planned: true → disabled + "规划中"）
```

## 菜单结构（最终方案）

| 分组 | 已实现项 | 占位项（规划中） |
|---|---|---|
| 首页（置顶） | 统计仪表盘 | — |
| 客户管理 | 线索 / 客户 / 联系人 / 查重合并 / 流失预警 / 客户360 | 续约管理(044)/客户健康报告(018 已并入 360) |
| 销售管理 | 商机 / 报价单 / 外勤拜访 / 产品 | 销售Playbook(043)/电子签署(045) |
| 交易管理 | 合同 / 订单 / 发票 | — |
| 营销中心 | 营销活动 / 邮件营销 / 在线表单 / 标签与细分 | 营销自动化(046)/落地页(048)/邮件高级(047) |
| 服务协作 | 客户服务 / 知识库 / 公告管理 / 我的审批 | 客户门户(049)/满意度调查(050)/SLA日历(051) |
| 客户成功 | 数据大屏 / 智能建议（并入工作台） | 续约漏斗(044)（并入客户管理） |
| 工作台 | 任务 / 智能建议 / 数据大屏 | — |
| 数据分析 | 自定义报表 / 团队排行 / 导出中心 | — |
| 系统管理 | 用户管理 / 角色权限 / 部门 / 标签与细分 | 字段权限(054)/多币种(055)/开放平台(052)/集成中心(053) |
| 审计与维护 | 审计日志 / 回收站 | — |

> 注：最终分组以实现为准，原则 = 语义聚合、已实现入口全保留、占位项标注规划中。

## Complexity Tracking

无违规，本表留空。
