# Implementation Plan: 线索管理模块

**Branch**: `004-lead-management` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-lead-management/spec.md`

## Summary

为 CRM 增加线索管理：线索录入/编辑/删除、线索池、分配/领取、跟进记录、一键转化为客户+联系人+商机、Excel 导入导出。线索是销售链路入口，转化功能打通线索→客户→商机的完整链路。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: Spring Boot 3.2 + MyBatis-Plus + Flyway + Apache POI（既有）

**Storage**:
- 新增 `lead` 表（Flyway V7 迁移）
- `follow_up` 表新增 `lead_id` 字段（Flyway V8 迁移），支持线索维度跟进记录

**Testing**: JUnit 5 + Spring Boot Test（LeadServiceTest 单元 + LeadIT 集成 + LeadContractTest 契约）

**Target Platform**: Web（前端 React 页面，ProTable 列表 + Modal 表单 + 详情页）

**Performance Goals**: 线索列表分页查询 ≤1s；线索转化 ≤3s（单事务）

**Constraints**:
- 已转化（QUALIFIED）线索不可删除、不可再次转化
- 线索转化时公司名重复则关联已有客户，联系人重复则不重复创建
- 跟进记录 follow_up 的 customer_id 和 lead_id 二选一（不可同时为空，不可同时非空）
- 线索状态枚举：NEW/WORKING/QUALIFIED/DISQUALIFIED
- 线索来源枚举：WEBSITE/AD/EXHIBITION/REFERRAL/COLD_CALL/OTHER

**Scale/Scope**: 线索量级可达数千至数万条，列表必须分页

## Constitution Check

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先 | API 契约先于实现 | ✅ contracts/leads.md 先定义 |
| 原则二：分层架构 | Controller→Service→Repository，前端表现层纯净 | ✅ 复用既有分层 |
| 原则三：数据完整性、安全与校验 | DTO Bean Validation、服务端授权、事务 | ✅ 转化用 @Transactional，状态校验在 Service 层 |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ LeadServiceTest + LeadIT + 契约测试 |
| 原则五：简洁、可维护与可观测 | YAGNI、结构化日志、分页、无 N+1 | ✅ 列表分页，转化用单查询查重 |
| 技术与架构约束 | 复用 Spring Boot + MyBatis-Plus + Flyway | ✅ 无新增技术栈 |

**结论**: 无门禁违规。

## Project Structure

### Documentation

```text
specs/004-lead-management/
├── spec.md
├── plan.md
├── tasks.md
└── contracts/
    ├── README.md
    └── leads.md
```

### Source Code

```text
backend/src/main/java/com/crm/
├── entity/Lead.java                     # 新增
├── repository/LeadMapper.java           # 新增
├── dto/lead/                            # 新增（LeadRequest/LeadResponse/LeadDetailResponse/ConvertRequest/ImportResult）
├── service/LeadService.java             # 新增（CRUD/分配/领取/转化/导入导出）
├── controller/LeadController.java       # 新增
├── entity/FollowUp.java                 # 修改（+leadId）
├── repository/FollowUpMapper.java       # 无需修改（MyBatis-Plus 自动）
├── service/FollowUpService.java         # 修改（支持 lead_id 查询）
└── resources/db/migration/
    ├── V7__lead.sql                     # 新增
    └── V8__follow_up_lead_id.sql        # 新增

frontend/src/
├── pages/leads/
│   ├── LeadListPage.tsx                 # 新增（列表+搜索+筛选+线索池Tab）
│   └── LeadDetailPage.tsx               # 新增（详情+跟进时间线+转化）
├── services/leadService.ts              # 新增
├── types/lead.ts                         # 新增
├── components/LeadConvertModal.tsx      # 新增（转化弹窗）
└── App.tsx                               # 修改（路由+菜单）
```

**Structure Decision**: 完全复用既有分层和组件模式（ProTable + Modal + 详情页），跟进记录复用 FollowUpTimeline 组件。

## Complexity Tracking

| 复杂度项 | 说明 | 缓解措施 |
|---|---|---|
| 线索转化的幂等性 | 公司名/联系人可能已存在，需查重关联 | 转化时先按公司名查客户，存在则关联；联系人按姓名+电话查重 |
| follow_up 双关联 | customer_id 和 lead_id 二选一 | 应用层校验，DB 层用 CHECK 约束（MySQL 8.0.16+ 支持）或应用层保证 |
| 线索池与列表的关系 | 线索池是未分配线索的子集 | 线索列表页加 Tab 切换（全部/线索池），后端按 owner_id is null 筛选 |
| 转化后线索不可编辑 | QUALIFIED 为终态 | Service 层校验状态，编辑/删除接口拒绝 QUALIFIED/DISQUALIFIED 线索 |

无违规，以上为已知复杂度及缓解措施。
