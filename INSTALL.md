# CRM 项目安装指南

前后端分离的客户关系管理系统（CRM），采用规范驱动开发（Spec Kit 工作流）。

## 目录

- [技术栈](#技术栈)
- [方式一：Docker 一键部署（推荐）](#方式一docker-一键部署推荐)
- [方式二：本地开发环境](#方式二本地开发环境)
  - [前置条件](#前置条件)
  - [1. 初始化数据库](#1初始化数据库)
  - [2. 启动后端服务](#2启动后端服务)
  - [3. 启动前端服务](#3启动前端服务)
- [方式三：生产环境部署](#方式三生产环境部署)
- [服务地址](#服务地址)
- [默认账号](#默认账号)
- [常用命令](#常用命令)
- [故障排查](#故障排查)
- [项目结构](#项目结构)

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 17 + Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 + Redis 7 + Spring Security (JWT) + Flyway + OpenAPI (Swagger) |
| **前端** | React 18 + TypeScript + Vite 5 + React Router + React Query + Zustand + Ant Design v5 + @ant-design/pro-components |
| **测试** | JUnit 5 / Spring Boot Test（后端）、Vitest + React Testing Library（前端）、Playwright（端到端） |
| **包管理** | pnpm（前端）、Maven（后端） |

---

## 方式一：Docker 一键部署（推荐）

### 前置条件

- Docker 20.10+
- Docker Compose v2+（或 Docker Desktop 内置 Compose）

### 快速启动

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. （可选）修改 .env 中的密码和 JWT_SECRET
#    生产环境必须修改 JWT_SECRET！生成命令：
#    openssl rand -hex 32

# 3. 一键启动所有服务
docker-compose up -d
```

### 验证部署

```bash
# 查看服务状态
docker-compose ps

# 查看后端日志（Flyway 自动建表，首次启动约需 10-30 秒）
docker-compose logs -f crm-backend

# 看到 "Started CrmApplication" 后即表示初始化完成
# 访问前端 http://localhost
# 访问后端 API http://localhost:8081
# 访问 Swagger UI http://localhost:8081/swagger-ui.html
```

### 停止服务

```bash
# 停止所有服务（保留数据）
docker-compose down

# 停止并删除数据卷（⚠️ 数据不可恢复）
docker-compose down -v
```

---

## 方式二：本地开发环境

### 前置条件

| 工具 | 版本要求 | 说明 |
|------|----------|------|
| JDK | 17+ | 推荐 Eclipse Temurin 17 |
| Maven | 3.8+ | 后端构建 |
| Node.js | 18+ | 前端运行 |
| pnpm | 8+ | 前端包管理（`npm i -g pnpm`） |
| MySQL | 8.0+ | 数据库 |
| Redis | 7.0+ | 缓存 |

### 1. 初始化数据库

> ⚠️ **只需创建空数据库，无需手动导入 SQL 文件。** 项目使用 Flyway 自动迁移，后端启动时会自动创建全部表结构（V1~V75，共 75 个迁移脚本）。

```sql
-- 创建空数据库（字符集必须为 utf8mb4）
CREATE DATABASE crm_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户并授权
CREATE USER 'crm_user'@'localhost' IDENTIFIED BY 'crm123456';
GRANT ALL PRIVILEGES ON crm_db.* TO 'crm_user'@'localhost';
FLUSH PRIVILEGES;
```

启动 Redis 服务：

```bash
# Windows（如已安装 Redis）
redis-server

# macOS (Homebrew)
brew services start redis

# Linux (systemd)
sudo systemctl start redis
```

### 2. 启动后端服务

```bash
# 进入后端目录
cd backend

# 方式 A：使用 Spring Boot DevTools 热启动（推荐开发）
mvn spring-boot:run

# 方式 B：完整打包后运行
mvn clean package -DskipTests
java -jar target/crm-backend-0.1.0-SNAPSHOT.jar
```

后端启动时 Flyway 会自动执行数据库迁移并创建初始管理员账号。

#### 后端环境变量（可选）

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `DB_HOST` | localhost | 数据库地址 |
| `DB_PORT` | 3306 | 数据库端口 |
| `DB_NAME` | crm_db | 数据库名 |
| `DB_USER` | crm_user | 数据库用户 |
| `DB_PASSWORD` | crm123456 | 数据库密码 |
| `REDIS_HOST` | localhost | Redis 地址 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_PASSWORD` | （空） | Redis 密码 |
| `JWT_SECRET` | （开发默认） | JWT 密钥，生产环境必须修改 |
| `SERVER_PORT` | 8081 | 后端服务端口 |
| `CORS_ALLOWED_ORIGINS` | http://localhost:5173 | 允许的前端来源 |

### 3. 启动前端服务

```bash
# 进入前端目录
cd frontend

# 安装依赖
pnpm install

# 启动开发服务器（含热更新）
pnpm run dev
```

前端开发服务器默认运行在 `http://localhost:5173`。

---

## 方式三：生产环境部署

### 1. 安全配置

```bash
# 生成强 JWT 密钥
openssl rand -hex 32

# 编辑 .env 文件，修改以下项：
# JWT_SECRET=<上面生成的密钥>
# DB_PASSWORD=<强密码>
# MYSQL_ROOT_PASSWORD=<强密码>
# REDIS_PASSWORD=<强密码>
```

### 2. 构建前端

```bash
cd frontend
pnpm install
pnpm run build    # 输出到 frontend/dist/
```

### 3. 构建后端

```bash
cd backend
mvn clean package -DskipTests -Dspotless.check.skip=true
# 输出：target/crm-backend-0.1.0-SNAPSHOT.jar
```

### 4. 部署配置

| 配置项 | 建议值 |
|--------|--------|
| JVM 内存 | `-Xms1g -Xmx2g` |
| 反向代理 | Nginx / Caddy + HTTPS |
| 数据库 | 专用 MySQL 实例，调整 `max-connections` |
| Redis | 启用持久化 `appendonly yes` |
| 日志 | 配置 Logback 输出到文件/ELK |

### 5. Docker 生产构建

```bash
# 使用项目根目录 Dockerfile 构建镜像
docker build -t crm-backend:latest .

# 或使用 docker-compose（已配置多阶段构建）
docker-compose up -d --build
```

---

## 数据库迁移说明

本项目使用 **Flyway** 进行数据库版本管理，无需手动执行 SQL 文件。

### 工作机制

1. 后端启动时自动检测 `db/migration/` 目录下的迁移脚本
2. 按版本号（V1、V2、V3...）顺序执行未应用的迁移
3. 迁移记录存储在 `flyway_schema_history` 表中
4. 首次启动会自动创建全部 **75 张表**并初始化管理员账号

### 迁移脚本列表

| 范围 | 说明 |
|------|------|
| V1~V10 | 用户、客户、商机、跟进、联系人基础表 |
| V11~V20 | 销售目标、产品、报价、合同、订单、付款 |
| V21~V30 | 任务、部门、数据权限、客户共享、工作流 |
| V31~V40 | 营销活动、工单、知识库、SLA、自定义字段、导出任务 |
| V41~V50 | 通知、健康评分、线索评分、报表模板、角色权限、标签细分 |
| V51~V60 | 邮件营销、审批流、外勤拜访、在线表单、公告、发票、搜索索引 |
| V61~V70 | SLA 日历、落地页、开放平台、字段权限、多币种、集成渠道、自定义对象、通话记录、邮件同步 |
| V71~V75 | 销售配额、定时导出、数据保留、角色权限更新 |

### 手动触发迁移

如需重新执行迁移（⚠️ 会清空数据）：

```bash
# Docker 环境
docker-compose down -v
docker-compose up -d

# 本地开发：删除 flyway_schema_history 表后重启后端
```

---

## 服务地址

| 服务 | 地址 | 说明 |
|------|------|------|
| **前端** | http://localhost | 主应用 |
| **后端 API** | http://localhost:8081 | REST API |
| **Swagger UI** | http://localhost:8081/swagger-ui.html | API 文档 |
| **Actuator 健康检查** | http://localhost:8081/actuator/health | 健康探针 |
| **MySQL** | localhost:3306 | 数据库 |
| **Redis** | localhost:6379 | 缓存 |

---

## 默认账号

| 角色 | 用户名 | 密码 |
|------|--------|------|
| 管理员 | `admin` | `admin123` |

> ⚠️ 首次登录后请立即修改默认密码。

---

## 常用命令

### Docker 管理

```bash
# 查看日志
docker-compose logs -f

# 查看后端日志
docker-compose logs -f crm-backend

# 重启后端
docker-compose restart crm-backend

# 进入容器
docker exec -it crm-backend sh
docker exec -it crm-mysql mysql -ucrm_user -pcrm123456 crm_db
docker exec -it crm-redis redis-cli
```

### 后端测试

```bash
cd backend

# 运行所有测试
mvn test

# 生成覆盖率报告
mvn test jacoco:report

# 代码格式化检查
mvn spotless:check

# 完整构建（含测试和覆盖率门禁）
mvn verify
```

### 前端测试

```bash
cd frontend

# 运行单元测试
pnpm run test

# 运行测试并生成覆盖率
pnpm run test:coverage

# TypeScript 类型检查
pnpm run typecheck

# ESLint 检查
pnpm run lint

# 端到端测试（需先启动应用）
pnpm run test:e2e
```

---

## 故障排查

### 后端启动失败

| 症状 | 排查步骤 |
|------|----------|
| 数据库连接失败 | 检查 MySQL 是否运行、`DB_HOST`/`DB_PASSWORD` 是否正确 |
| Flyway 迁移失败 | 检查数据库字符集是否为 `utf8mb4` |
| Redis 连接失败 | 检查 Redis 是否运行、`REDIS_PASSWORD` 是否正确 |
| JWT 警告 | 开发环境可忽略，生产环境必须设置 `JWT_SECRET` |

### 前端启动失败

| 症状 | 排查步骤 |
|------|----------|
| `pnpm install` 失败 | 检查 Node.js 版本 >= 18，清除缓存：`pnpm store prune` |
| 端口 5173 被占用 | 修改 `vite.config.ts` 中的 `server.port` |
| API 请求跨域 | 检查后端 `CORS_ALLOWED_ORIGINS` 是否包含前端地址 |
| 代理不生效 | 检查 `vite.config.ts` 中 `server.proxy` 配置 |

### Docker 常见问题

| 症状 | 排查步骤 |
|------|----------|
| `docker-compose up` 失败 | 检查 `.env` 文件是否存在，端口是否被占用 |
| MySQL 启动慢 | 首次启动需初始化数据库，等待 30-60 秒 |
| 数据卷过大 | 使用 `docker-compose down -v` 清理（⚠️ 数据丢失） |

---

## 项目结构

```
crm/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/crm/ # 源代码（controller/service/repository/entity/dto）
│   ├── src/main/resources/    # 配置文件（application.yml、db/migration）
│   ├── pom.xml                # Maven 依赖配置
│   └── target/                # 构建输出
├── frontend/                   # React 前端
│   ├── src/                   # 源代码（pages/components/services/types/store/hooks）
│   ├── public/                # 静态资源
│   ├── tests/                 # 单元测试
│   ├── e2e/                   # 端到端测试
│   ├── package.json           # NPM 依赖配置
│   ├── vite.config.ts         # Vite 构建配置
│   └── dist/                  # 构建输出（生产部署）
├── specs/                      # Spec Kit 设计文档
│   └── 001-crm-core/          # 示例：spec.md, plan.md, tasks.md, data-model.md...
├── .specify/                   # Spec Kit 项目配置与模板
├── docker-compose.yml          # Docker Compose 编排配置
├── Dockerfile                  # 后端 Docker 镜像（多阶段构建）
├── nginx.conf                  # Nginx 反向代理配置
├── .env.example                # 环境变量模板
└── INSTALL.md                  # 本安装指南
```

---

## 规范驱动开发流程

本项目采用 Spec Kit 工作流，设计文档位于 `specs/` 目录：

```
spec.md → plan.md → research.md → data-model.md → contracts/ → tasks.md → quickstart.md
```

项目治理原则见 `.specify/memory/constitution.md`。
