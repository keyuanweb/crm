# Implementation Plan: 部门管理页面优化

**Branch**: `068-department-management-optimization` | **Date**: 2026-08-29 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/068-department-management-optimization/spec.md`

## Summary

部门管理页面优化：在现有基础 CRUD 功能上，增加搜索筛选、分页、详情查看、拖拽排序等 UX 优化，提升管理员使用效率。纯前端 UI 优化，无后端 API 变更，无新增依赖。

## Technical Context

**Language/Version**: TypeScript 5.0+, Java 17

**Primary Dependencies**: React 18.2.0, Ant Design 5.22.0, Vite 5.4.21, Spring Boot 3.2.0, MyBatis-Plus 3.5.5

**Storage**: MySQL 8.0 (department 表已存在)

**Testing**: Vitest + @testing-library/react (前端), JUnit 5 + Mockito (后端)

**Target Platform**: Web 浏览器 (Chrome 90+, Edge 90+, Firefox 88+)

**Project Type**: Web 应用 (前后端分离)

**Performance Goals**: 部门搜索响应时间 < 200ms (1000 个部门数据量下)

**Constraints**: 无新增后端 API，复用现有接口；无新增前端依赖；纯前端 UI 优化

**Scale/Scope**: 1000 个部门以内，树形结构最多 5 层嵌套

**Constraints**: 部门管理仅 ADMIN 角色可见；保持现有 API 兼容性

**Scale/Scope**: 适用于 1000 个部门以内的场景

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### 原则 1：代码质量优先
- [x] 所有公共方法必须有完整的 Javadoc (后端)
- [x] Service 层方法必须有单元测试（覆盖率 ≥ 80%）
- [x] Controller 层方法必须有集成测试
- [x] 严禁裸 try-catch，异常必须由 GlobalExceptionHandler 统一处理
- [x] 方法长度不超过 50 行，类长度不超过 500 行

### 原则 2：RESTful API 设计规范
- [x] 所有 API 必须遵循 RESTful 风格 (复用现有接口)
- [x] 统一响应格式：`{"code": 200, "message": "success", "data": {}}`
- [x] 统一错误响应格式：`{"code": 400, "message": "参数错误", "detail": "详细信息"}`
- [x] 所有请求体使用 DTO，禁止直接使用 Entity
- [x] Controller 层使用 @Valid 注解进行参数校验

### 原则 3：数据库设计规范
- [x] 表名使用小写 + 下划线 (department 表已存在)
- [x] 所有表必须有 created_at 和 updated_at 字段 (已存在)
- [x] 所有表必须有逻辑删除字段 deleted (已存在)
- [x] 主键统一使用 id (类型：BIGINT，自增或雪花算法) (已存在)

### 原则 4：Redis 缓存策略
- [x] 部门树缓存（TTL 30 分钟）：key 格式 `crm:department:tree`
- [x] 缓存更新策略：先更新数据库，再删除缓存

### 原则 5：前端开发规范
- [x] TypeScript 严格模式
- [x] 所有组件必须有 TypeScript 类型定义
- [x] 状态管理使用 React Query + Zustand
- [x] 样式使用 Ant Design (项目实际使用 Ant Design，非 Tailwind CSS)
- [x] API 请求使用 Axios，统一封装在 services/api.ts
- [x] 路由使用 React Router v6

## Project Structure

### Documentation (this feature)

```text
specs/068-department-management-optimization/
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
├── src/main/java/com/crm/
│   ├── controller/
│   │   └── DepartmentController.java  # 复用，无需修改
│   ├── service/
│   │   └── DepartmentService.java     # 可能需要增加缓存
│   └── repository/
│       └── DepartmentMapper.java      # 复用，无需修改
└── src/test/java/com/crm/
    ├── controller/
    │   └── DepartmentControllerTest.java
    └── service/
        └── DepartmentServiceTest.java

frontend/
├── src/
│   ├── pages/
│   │   └── departments/
│   │       ├── DepartmentListPage.tsx    # 主要修改
│   │       └── DepartmentListPage.test.tsx
│   ├── services/
│   │   └── departmentService.ts          # 可能增加搜索接口
│   └── types/
│       └── department.ts                 # 可能需要扩展
└── tests/
    └── integration/
        └── department.spec.ts
```

**Structure Decision**: 前后端分离架构，前端优化为主，后端仅需增加缓存支持（可选）

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 无 | 无违规 | 无 |
