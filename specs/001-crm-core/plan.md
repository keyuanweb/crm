# Implementation Plan: CRM 客户关系管理系统（初始版本）

**Branch**: `001-crm-core` | **Date**: 2026-08-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-crm-core/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

构建单租户内部 CRM 的初始版本，覆盖 4 大模块 15 项功能：客户管理（增删改查/逻辑删除/搜索分页/Excel 导入导出）、商机管理（父实体 CRUD 与统计）、销售机会管理（子实体阶段管道与关闭）、跟进记录管理。技术形态为前后端分离的 Web 应用：Java 17 + Spring Boot 3.2 后端（REST + JSON），React 18 + TypeScript 前端；关系型存储 MySQL 8 + Redis 7 缓存；基于角色的 JWT 认证授权。设计严格遵循项目章程（契约优先、分层架构、数据安全校验、测试优先、简洁可观测）。

## Technical Context

**Language/Version**: Java 17 (LTS) / TypeScript 5.0+；构建 Maven（后端）、Vite 5（前端）

**Primary Dependencies**:
- 后端：Spring Boot 3.2.0、Spring Security 6.2.0、MyBatis-Plus 3.5.5、Lombok 1.18.30、springdoc-openapi (Swagger 3.0)、Apache POI（Excel 导入导出，新增）、Flyway（版本化迁移，按章程要求新增）
- 前端：React 18.2.0、React Router 6.20.0、React Query 5.0+、Zustand 4.5.0、Tailwind CSS 3.4.0、Axios 1.6.0

**Storage**: MySQL 8.0（关系型主存储，utf8mb4，逻辑删除字段 + 乐观锁 version 字段）；Redis 7.0（JWT 刷新令牌黑名单/登出、商机统计与客户详情热点缓存）

**Testing**: 后端 JUnit 5 + Spring Boot Test（单元 + 集成）；前端 Jest + React Testing Library；端到端 Playwright（quickstart 验证场景）；契约测试基于 OpenAPI 契约（章程"契约优先"要求）

**Target Platform**: 浏览器（Web 应用）+ REST API 服务

**Project Type**: Web application（前后端分离，frontend + backend）

**Performance Goals**: 10 万条客户数据规模下搜索/筛选/分页查询用户感知 ≤ 2 秒（对应 SC-002）；商机统计查询 ≤ 2 秒

**Constraints**: 所有列表接口分页、禁止 N+1 查询（章程原则五）；逻辑删除（客户/商机/销售机会）；服务端强制校验与授权（章程原则三）；单一状态管理方案（Zustand）；密钥绝不硬编码（环境 profile + 环境变量）

**Scale/Scope**: 单租户内部 CRM，默认账号 admin/admin123；角色：管理员、销售、客服；15 项功能 / 4 大模块 / 5 个核心业务实体

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先的 API 设计 | 每个 REST 端点先定义 OpenAPI 契约；前后端 DTO/类型同源；破坏性变更走版本化 | ✅ 设计满足（Phase 1 产出 contracts/，标注 API 版本 v1） |
| 原则二：分层架构与关注点分离 | Controller→Service→持久层；前端组件不含业务规则 | ✅ 设计满足（backend 分层 + frontend services 层） |
| 原则三：数据完整性、安全与校验 | DTO 层 Jakarta Bean Validation；服务端授权（Spring Security + 角色）；bcrypt 密码；事务限定 Service 层 | ✅ 设计满足 |
| 原则四：测试优先与质量门禁 | 测试先于实现；合并门禁（构建/单测/集成/Lint/类型检查/覆盖率） | ✅ 设计满足（CI 门禁列入 tasks 阶段） |
| 原则五：简洁、可维护与可观测 | YAGNI、SLF4J 结构化日志、分页、无 N+1 | ✅ 设计满足 |
| 技术与架构约束 | Java LTS + Spring Boot、React + TS、关系库 + 版本化迁移、Spring Security JWT、环境 profile | ✅ 设计满足（Flyway 迁移纳入） |

**结论**: 无门禁违规，无需 Complexity Tracking 豁免项。

## Project Structure

### Documentation (this feature)

```text
specs/001-crm-core/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
backend/
├── pom.xml
├── application.yml
├── src/main/java/com/crm/
│   ├── CrmApplication.java
│   ├── config/             # Security、Redis、MyBatis-Plus、OpenAPI、全局异常
│   ├── controller/         # auth/customer/opportunity/sales-opportunity/follow-up/stats
│   ├── service/            # 业务逻辑（事务边界）
│   ├── repository/         # MyBatis-Plus Mapper 持久化层
│   ├── entity/             # 数据库实体（逻辑删除、乐观锁字段）
│   ├── dto/                # 请求/响应 DTO（Bean Validation 注解）
│   ├── exception/          # 统一异常与错误码
│   └── common/             # 分页/结果封装、常量、工具
└── src/main/resources/db/migration/   # Flyway 版本化迁移脚本
└── src/test/java/          # 单元 + 集成测试

frontend/
├── package.json
├── vite.config.ts
├── src/
│   ├── main.tsx
│   ├── App.tsx
│   ├── pages/              # 登录/客户/商机/销售机会/跟进/统计
│   ├── components/         # 纯展示组件
│   ├── services/           # API 客户端（基于契约）
│   ├── types/              # 与契约对齐的类型
│   ├── store/              # Zustand（单一状态管理）
│   └── hooks/              # React Query 数据获取封装
└── tests/                  # Jest + RTL；e2e/ Playwright
```

**Structure Decision**: 采用 Option 2（Web 应用，前后端分离）——检测到 frontend + backend。`backend/` 与 `frontend/` 置于仓库根目录（本仓库即项目根，与背景文档的 crm-system/ 等价）；后端按章程原则二强制 Controller→Service→Repository 分层；前端组件保持表现层纯净，数据访问收敛到 `services/` 层。

### 布局与导航设计（US7 / FR-018~019）

- 外层 `Layout` 固定 `height: 100vh` 且 `overflow: hidden`；顶部 `Header`（56px）位于滚动区域之外，内容区 `Content` 独立 `overflow: auto` 滚动 → 顶栏滚动时始终固定可见。
- 左侧 `Sider + Menu` 使用 antd Menu `type: 'group'` 分组：客户管理（线索/客户/联系人）、销售管理（商机/销售机会/报价单）、交易管理（合同/订单）、基础资料（产品/任务）、营销与服务（营销/客户服务/知识库）、数据分析（统计/导出中心），以及仅 ADMIN 可见的系统管理（用户管理/部门/工作流/SLA 策略/自定义字段/合同模板/审计日志）；`selectedKeys` 按当前路由前缀匹配高亮，移动端横向菜单拍平为普通项。
- 该布局改动仅涉及前端 `App.tsx` 外壳组件，不改变任何后端契约。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

无违规，本表留空。
