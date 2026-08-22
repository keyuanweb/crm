# Spring-Boot-React-CRM

前后端分离的客户关系管理系统（CRM），采用规范驱动开发（Spec Kit 工作流）。

- **后端**: Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 + Redis 7 + Spring Security (JWT) + Flyway + OpenAPI (Swagger)
- **前端**: React 18 + TypeScript + Vite 5 + React Router + React Query + Zustand + Tailwind CSS
- **测试**: JUnit 5 / Spring Boot Test（后端）、Vitest + React Testing Library（前端）、Playwright（端到端）
- **规范文档**: `specs/001-crm-core/`（spec/plan/research/data-model/contracts/quickstart/tasks，由 Spec Kit 流程生成）

## 功能范围

- 客户管理：分页/搜索/筛选、CRUD（逻辑删除）、Excel 导入导出
- 商机管理（父实体）与销售机会（子实体）：阶段管道、赢单/输单关闭
- 跟进记录：客户/商机时间线
- 商机管道统计报表
- 基于角色的访问控制（ADMIN / SALES / SUPPORT）

## 快速开始

### 前置条件

- JDK 17、Maven 3.8+、MySQL 8.0、Redis 7.0、Node.js 18+

### 1. 初始化数据库

```sql
CREATE DATABASE crm_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'crm_user'@'localhost' IDENTIFIED BY 'crm123456';
GRANT ALL PRIVILEGES ON crm_db.* TO 'crm_user'@'localhost';
```

启动 Redis（`redis-server`）。后端启动时 Flyway 自动建表并种子管理员账号。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

- API 文档（OpenAPI/Swagger）：http://localhost:8081/swagger-ui.html
- 连接参数可通过环境变量覆盖：`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` / `REDIS_HOST` / `REDIS_PORT` / `JWT_SECRET`

### 3. 启动前端

```bash
cd frontend
pnpm install
pnpm run dev
```

访问 http://localhost:5173 ，默认账号 `admin / admin123`。

## 测试

```bash
# 后端（单元 + 集成 + 契约 + 覆盖率门禁）
cd backend && mvn test

# 前端（单元/组件、类型检查、Lint）
cd frontend && pnpm run test && pnpm run typecheck && pnpm run lint

# 端到端
cd frontend && pnpm run test:e2e
```

## 项目结构

```text
backend/    Spring Boot 后端（controller/service/repository/entity/dto/config/exception/common + db/migration）
frontend/   React 前端（pages/components/services/types/store/hooks）
specs/      Spec Kit 设计文档（spec/plan/research/data-model/contracts/quickstart/tasks）
.specify/   Spec Kit 项目配置与模板
```

## 规范驱动开发流程

见 `specs/001-crm-core/`：`spec.md` → `plan.md` → `research.md` → `data-model.md` → `contracts/` → `tasks.md` → `quickstart.md`，项目治理原则见 `.specify/memory/constitution.md`。
