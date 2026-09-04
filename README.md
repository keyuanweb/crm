# Spring-Boot-React-CRM

前后端分离的客户关系管理系统（CRM），采用规范驱动开发（Spec Kit 工作流）。

- **后端**: Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 + Redis 7 + Spring Security (JWT) + Flyway + OpenAPI (Swagger)
- **前端**: React 18 + TypeScript + Vite 5 + React Router + React Query + Zustand + **Ant Design v5 + @ant-design/pro-components**（ProLayout/ProTable/ProForm 等）
- **测试**: JUnit 5 / Spring Boot Test（后端）、Vitest + React Testing Library（前端）、Playwright（端到端）
- **包管理**: pnpm（前端）
- **规范文档**: `specs/001-crm-core/`、`specs/002-user-management/`（spec/plan/research/data-model/contracts/quickstart/tasks，由 Spec Kit 流程生成）

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

## Docker 一键部署

### 前置条件

- Docker 20.10+
- Docker Compose v2+

### 快速启动

```bash
# 1. 复制环境变量模板
cp .env.example .env
# 2. 修改 .env 中的 JWT_SECRET（生产环境必须修改）
# 3. 一键启动
docker-compose up -d
```

### 服务地址

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost |
| 后端 API | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

### 常用命令

```bash
# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down

# 停止并删除数据卷（谨慎使用）
docker-compose down -v

# 重启后端
docker-compose restart crm-backend
```

### 生产环境配置

1. 修改 `.env` 中的密码和 JWT_SECRET
2. 生成强 JWT 密钥：`openssl rand -hex 32`
3. 配置反向代理（Nginx/Caddy）+ HTTPS
4. 调整 JVM 参数：`-Xms1g -Xmx2g`
