# Implementation Plan: 用户管理模块

**Branch**: `002-user-management` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-user-management/spec.md`

## Summary

为 CRM 增加用户管理：管理员可创建/编辑/检索用户（ADMIN/SALES/SUPPORT），停用/启用账号，重置密码；登录用户可修改自身密码。密码变更/重置通过令牌版本（token_version）机制使旧访问令牌立即失效；停用账号即时生效（认证过滤器逐请求校验账号状态与令牌版本）。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用 001-crm-core 技术栈）

**Primary Dependencies**: Spring Security + JWT（既有）、MyBatis-Plus、Redis（刷新令牌失效）、BCrypt

**Storage**: MySQL `user` 表新增 `last_login_at`、`token_version` 列（Flyway V4 迁移）

**Testing**: JUnit 5 + Spring Boot Test（UserServiceTest 单元、UserIT 集成）

**Target Platform**: Web（前端 React 页面，仅 ADMIN 可见）

**Project Type**: 现有 Web 应用（前后端分离）新增功能模块

**Performance Goals**: 用户列表分页查询 ≤1s（用户量级小，无特殊要求）

**Constraints**: 用户名不可修改；不提供物理删除（停用代替）；系统至少保留一个启用 ADMIN；不能停用当前登录账号；密码须 8~64 位且含字母与数字

**Scale/Scope**: 用户量级为内部团队规模（≤ 数百）

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则三：数据完整性、安全与校验 | 密码 bcrypt、服务端授权、令牌失效机制 | ✅ 满足（bcrypt + token_version 失效 + Redis 刷新令牌清理） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（UserServiceTest/UserIT + 既有门禁） |
| 原则五：简洁、可维护与可观测 | YAGNI、日志 | ✅ 满足（复用既有分层与审计服务） |
| 技术与架构约束 | 复用 Spring Security JWT 方案 | ✅ 满足（无新增技术） |

**结论**: 无门禁违规，无需豁免。

## Project Structure

### Documentation (this feature)

```text
specs/002-user-management/
├── spec.md            # 功能规格
├── plan.md            # 本文件
└── tasks.md           # 任务分解（/speckit-tasks 输出）
```

### Source Code（沿用 001 既有结构）

```text
backend/src/main/java/com/crm/
├── controller/UserController.java      # 用户管理接口
├── service/UserService.java            # 业务逻辑与防护规则
├── dto/user/                           # UserCreate/Update/Reset/ChangePassword/Response
├── entity/User.java                    # +lastLoginAt/tokenVersion
├── security/JwtUtil.java               # 令牌携带 tokenVersion
├── security/JwtAuthFilter.java         # 逐请求校验账号状态与令牌版本
└── resources/db/migration/V4__user_auth_fields.sql

frontend/src/
├── pages/users/UserManagementPage.tsx  # 用户列表/创建/编辑/重置密码
├── services/userService.ts
├── types/user.ts
└── App.tsx                             # 路由 + 管理员导航入口
```

**Structure Decision**: 完全复用 001-crm-core 的分层与认证机制；不引入新框架。

## Complexity Tracking

无违规，本表留空。
