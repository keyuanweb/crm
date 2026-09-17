# CRM 客户关系管理系统

前后端分离的企业级客户关系管理系统（CRM），采用规范驱动开发（Spec Kit 工作流）。

> 📖 **详细安装指南请查看 [INSTALL.md](INSTALL.md)**

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端** | Java 21 + Spring Boot 3.2 + MyBatis-Plus 3.5 + MySQL 8 + Redis 7 + Spring Security (JWT) + Flyway + OpenAPI (Swagger) |
| **前端** | React 18 + TypeScript + Vite 5 + React Router + React Query + Zustand + Ant Design v5 + @ant-design/pro-components |
| **测试** | JUnit 5 / Spring Boot Test（后端）、Vitest + React Testing Library（前端）、Playwright（端到端） |
| **包管理** | pnpm（前端）、Maven（后端） |

## 核心功能

### 销售管理
- **客户管理**：分页/搜索/筛选、CRUD（逻辑删除）、Excel 导入导出、查重合并、公海池
- **线索管理**：线索评分、转化客户/商机/联系人
- **商机管理**：阶段管道、赢单/输单关闭、销售机会（子实体）、销售 Playbook
- **销售配额**：逐层分解、达成率跟踪
- **报价单/合同/订单/发票**：完整交易链路、多币种支持
- **产品目录**：标准售价、多币种价格自动折算

### 营销与服务
- **营销活动**：Campaign 管理、归因分析
- **邮件营销**：邮件模板、发送追踪、退订管理
- **在线表单/落地页**：线索收集、自定义表单
- **客户服务**：工单管理、知识库、SLA 策略、满意度调查
- **客户门户**：客户自助服务

### 协作与效率
- **跟进记录**：客户/商机时间线、通话记录
- **任务管理**：任务分配、审批流
- **工作流自动化**：规则引擎、执行日志、通知
- **公告管理**：团队公告
- **外勤拜访**：拜访记录、轨迹
- **邮件同步**：邮箱账户与同步记录管理（收信源未接入：同步端点默认拒绝并**不产生记录**；仅演示开关下可生成标注为「模拟」的示例记录）

### 数据与分析
- **数据大屏**：KPI 实时看板、ECharts 可视化
- **仪表盘**：KPI 指标、销售漏斗、业绩达成、销售预测、客户分析、跟进活动
- **自定义报表**：报表模板、多维分析、灵活配置查询维度
- **团队排行**：销售业绩排行榜
- **智能建议**：规则型智能建议、健康评分、流失预警、停滞商机预警

### 系统管理
- **用户/角色/部门**：完整的组织架构管理
- **权限控制**：RBAC + 字段级权限 + 数据权限
- **双因素认证（2FA）**：TOTP 动态码绑定与二次验证、恢复码、管理员重置
- **审计日志**：操作追踪、合规导出
- **回收站**：批量恢复、彻底删除
- **自定义字段/对象**：动态表单、扩展实体
- **标签与细分**：客户分群、标签管理
- **多币种**：汇率管理、自动折算
- **开放平台**：REST API + Swagger + API Key + Webhook
- **集成中心**：第三方系统集成
- **数据保留策略**：归档、合规导出（GDPR）
- **定时导出**：Cron 调度、邮件通知

### 体验增强
- **国际化**：中文 / English（1000+ 翻译键）
- **全局搜索**：顶栏跨实体统一搜索（客户/线索/商机/工单/知识库）
- **PWA**：Service Worker、离线缓存、安装提示
- **使用地图**：G6 状态机、功能依赖可视化
- **个人设置**：修改密码、个人中心

## 快速开始

> 💡 如需完整安装步骤（Docker / 本地开发 / 生产部署），请查看 [INSTALL.md](INSTALL.md)

### 前置条件

- JDK 21、Maven 3.8+、MySQL 8.0、Redis 7.0、Node.js 18+

### 1. 初始化数据库

```sql
CREATE DATABASE crm_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'crm_user'@'localhost' IDENTIFIED BY 'crm123456';
GRANT ALL PRIVILEGES ON crm_db.* TO 'crm_user'@'localhost';
```

> ⚠️ 只需创建空数据库，无需手动导入 SQL。Flyway 会在后端启动时自动创建全部 86 张表。

启动 Redis（`redis-server`）。

### 2. 启动后端

```bash
cd backend
mvn spring-boot:run
```

- API 文档：http://localhost:8081/swagger-ui.html
- 环境变量覆盖：`DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` / `REDIS_HOST` / `REDIS_PORT` / `JWT_SECRET`

### 3. 启动前端

```bash
cd frontend
pnpm install
pnpm run dev
```

访问 http://localhost:5173，默认账号 `admin / admin123`。

## Docker 一键部署

```bash
# 1. 复制环境变量模板
cp .env.example .env

# 2. （可选）修改 .env 中的 JWT_SECRET 和密码

# 3. 一键启动
docker-compose up -d
```

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost |
| 后端 API | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui.html |
| MySQL | localhost:3306 |
| Redis | localhost:6379 |

常用命令：

```bash
docker-compose logs -f        # 查看日志
docker-compose down           # 停止服务
docker-compose restart crm-backend  # 重启后端
```

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

```
crm/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/crm/ # controller/service/repository/entity/dto
│   ├── src/main/resources/    # application.yml、db/migration（V1~V91，缺 V72；V91 = 102 内置字段权限）
│   └── pom.xml
├── frontend/                   # React 前端
│   ├── src/                   # pages/components/services/types/store/hooks
│   ├── tests/                 # 单元测试
│   ├── e2e/                   # 端到端测试
│   └── package.json
├── specs/                      # Spec Kit 设计文档（101 个功能模块，001~102，缺 069）
├── .specify/                   # Spec Kit 配置与模板
├── docker-compose.yml          # Docker 编排
├── Dockerfile                  # 后端镜像（多阶段构建）
├── nginx.conf                  # Nginx 反向代理
├── .env.example                # 环境变量模板
├── INSTALL.md                  # 详细安装指南
├── PROJECT_FEATURES.md         # 代码核对版功能全景整理
├── CRM_FEATURE_COMPARISON.md   # 功能对比分析
└── README.md                   # 项目说明（本文件）
```

> ⚠️ **2026-09-16 订正（由 099 执行）**：上面目录树里 `specs/` 那一行原写
> **「97 个功能模块，001~098，缺 069」**（098 交付时刚订正过），现为 **「98 个功能模块，001~099，缺 069」**
> —— 099（配额创建改成列表页内弹窗）入了列，按 `ls -d specs/[0-9]* | wc -l` 实测 **98**。
> **旧值逐字保留在上句引号内**（不是静默改写）；**订正落在目录树的本行内**（这一行历来就地改数、
> 本文件不另留旧值段落，历次做法见 `git log -L163,163:README.md`）。

> ⚠️ **2026-09-17 订正（由 100 = 100-rate-limit-consolidation 执行）**：上面目录树里 `specs/` 那一行，
> 上段（099）改到的是 **「98 个功能模块，001~099，缺 069」**（那句在 099 时点为真、**原文保留在上**），
> 现为 **「99 个功能模块，001~100，缺 069」** —— 100（全局限流收口）入了列，
> 按 `ls -d specs/[0-9]* | wc -l` 实测 **99**（本项**零前端改动**，与 099 正相反：那次动的是前端 5 行）。
> **旧值逐字保留在上句引号内**（不是静默改写）；**订正仍落在目录树的本行内**
> （这一行历来就地改数、本文件不另留旧值段落，历次做法见 `git log -L163,163:README.md`）。
> ⚠️ **`git log -L163,163:README.md` 这条复算命令本身在本段落地后会漂**：本段新增了 7 行，
> 该行**已不是 163 行**（行号引用会腐坏 ⇒ 判「行号不符」前先确认双方看的是同一版本）。

> ⚠️ **2026-09-17 订正（由 102 = 102-builtin-field-permission 执行）**：上面目录树里 `specs/` 那一行，
> 上段（100）改到的是 **「99 个功能模块，001~100，缺 069」**（那句在 100 时点为真、**原文保留在上**），
> 现为 **「101 个功能模块，001~102，缺 069」** —— 按 `ls -d specs/[0-9]* | wc -l` 实测 **101**。
> **旧值逐字保留在上句引号内**（不是静默改写）；**订正仍落在目录树的本行内**（这一行历来就地改数、
> 本文件不另留旧值段落）。
> ⚠️ **本行这次跨了两格，必须写明，否则会被读成「102 一次加了两个模块」**：101（邮件同步收信侧诚实化，2026-09-17 交付）
> **自己那一格没有落在这里**——它把规格数落点记在 `PROJECT_FEATURES.md`（`99 → 100`），**本行当时停在 `99 个 / 001~100`**
> ⇒ 102 交付前本行**已经落后一格**（`100（001–101，缺 069）` 才是 101 交付后的真值）。这是**既有的一处漏改**（101 批造成，不是 102 的），
> 本批一并补齐：**102 一次把 `99 个 / 001~100` → `101 个 / 001~102`**，中间那一格（`100 个 / 001~101`）**只在这句话里留痕、不落进目录树**。
> ⚠️ **为什么中间那一格不单独留一行**：目录树是**当前值**、不是台账；旧值留痕的落点是**本 ⚠️ 段**（本文件历来如此）。
> 分母口径与依据：`specs/NNN-*` 的**目录数**（`069` 未创建 ⇒ 101 个目录对应编号 001–102）。

## 规范驱动开发流程

本项目采用 Spec Kit 工作流，设计文档位于 `specs/` 目录：

```
spec.md → plan.md → research.md → data-model.md → contracts/ → tasks.md → quickstart.md
```

项目治理原则见 `.specify/memory/constitution.md`。

## 功能对比

本项目功能覆盖与 Salesforce、HubSpot、Zoho/Dynamics 365、纷享销客/销售易、SuiteCRM/EspoCRM/Odoo 等平台的对比分析，详见 [CRM_FEATURE_COMPARISON.md](CRM_FEATURE_COMPARISON.md)。

## 许可证

MIT
