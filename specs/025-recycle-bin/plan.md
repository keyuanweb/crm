# Implementation Plan: 回收站与批量恢复

**Branch**: `025-recycle-bin` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

## Summary

新增回收站：`RecycleBinService` 覆盖核心 4 实体（客户/线索/联系人/商机），各 Mapper 加注解 SQL（@Select 查 deleted=1 绕过逻辑删除、@Update 恢复、@Delete 物理删）；`RecycleBinController` 提供列表/批量恢复/彻底删除；前端回收站页（类型筛选 + 搜索 + 勾选恢复/彻底删除）。

## Technical Context

**Language/Version**: Java 17（后端）+ TypeScript/React 18（前端）

**Primary Dependencies**: Spring Boot 3.2、MyBatis-Plus（@Select/@Update/@Delete 注解 SQL 绕过 @TableLogic）、antd 5（Table/Modal）

**Storage**: 无新表；查 deleted=1 记录 + 逆向更新 deleted=0

**Testing**: JUnit 5 + Mockito（单元）、Spring Boot Test + MockMvc（集成）、Vitest + RTL（前端）

**Target Platform**: Web

**Project Type**: Web 应用（Spring Boot 后端 + React 前端）

**Performance Goals**: 回收站列表 ≤ 1s（4 实体各查一次，内存合并分页）

**Constraints**: 恢复/物理删通过自定义注解 SQL 绕过逻辑删除过滤；SALES 仅本人删除记录（created_by）；恢复唯一性冲突跳过

**Scale/Scope**: 4 个 Mapper 加方法 + 1 个服务 + 1 个控制器 + 前端回收站页

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 端点契约先于实现 | ✅ 满足（contracts/recycle-bin.md 定义列表/恢复/删除契约） |
| 原则二：分层架构与关注点分离 | Controller→Service→Repository | ✅ 满足（RecycleBinService 分发到 Mapper） |
| 原则三：数据完整性、安全与校验 | 服务端权限 | ✅ 满足（回收站按数据权限过滤；恢复冲突校验） |
| 原则四：测试优先与质量门禁 | 测试先于实现 | ✅ 满足（RecycleBinServiceTest + RecycleBinIT + 前端渲染测试） |
| 原则五：简洁、可维护与可观测 | 结构化日志 | ✅ 满足（恢复/删除记审计） |

**结论**: 无门禁违规。

## Project Structure

### Documentation (this feature)

```text
specs/025-recycle-bin/
├── plan.md / spec.md / research.md / data-model.md / quickstart.md
├── contracts/（recycle-bin 契约）
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/crm/
├── repository/CustomerMapper.java              # 修改：selectDeletedByUser/restoreById/purgeById（@Select/@Update/@Delete）
├── repository/LeadMapper.java                  # 修改：同上
├── repository/ContactMapper.java               # 修改：同上
├── repository/OpportunityMapper.java           # 修改：同上
├── service/RecycleBinService.java              # 新增：跨实体列表/恢复/彻底删除（数据权限过滤 + 冲突跳过 + 审计）
├── controller/RecycleBinController.java        # 新增：GET /recycle-bin、POST /recycle-bin/restore、POST /recycle-bin/purge
└── dto/recycle/RecycleItem.java                # 新增：type/id/name/deletedAt/deletedBy

backend/src/test/java/com/crm/
├── service/RecycleBinServiceTest.java          # 新增：列表/恢复/冲突/权限 单元测试
└── integration/RecycleBinIT.java               # 新增：删除→回收站→恢复 集成测试

frontend/src/
├── types/recycle.ts                            # 新增：RecycleItem 类型
├── services/recycleService.ts                  # 新增：fetchRecycleBin/restore/purge
├── pages/recycle/RecycleBinPage.tsx            # 新增：回收站页（类型筛选 + 搜索 + 勾选操作）
└── App.tsx                                     # 修改：注册回收站路由（系统管理分组）
```

**Structure Decision**: 沿用既有分层。用 MyBatis-Plus 注解 SQL（@Select/@Update/@Delete）在 Mapper 层绕过 @TableLogic 逻辑删除过滤（注解方法不受通用方法逻辑删除影响）。恢复/物理删分发到对应 Mapper；SALES 仅本人（created_by=当前用户），ADMIN 全量。

## Complexity Tracking

> 无违规，本表留空。
