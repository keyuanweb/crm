# Tasks: 系统加固与优化

**Input**: Design documents from `/specs/003-system-hardening/`

**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Organization**: Tasks are grouped by user story (003-system-hardening).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1~US5)
- Include exact file paths in descriptions

## Path Conventions

- 沿用既有结构：`backend/src/`、`frontend/`

---

## Phase 1: Setup & Foundational

- [x] ~~T001 [P] [US2] 配置 RedisTemplate JSON 序列化~~ — **已满足**：`RedisConfig.java` 已使用 `GenericJackson2JsonRedisSerializer`（键 String、值 JSON），无需变更
- [x] T002 [P] [US5] 新增 CORS 配置 in `backend/src/main/java/com/crm/config/SecurityConfig.java`（`CorsConfigurationSource` Bean，从 `@Value("${cors.allowed-origins}")` 读取，替换 `cors.disable()`）
- [x] T003 [P] [US5] application.yml 新增 `cors.allowed-origins` 配置项 in `backend/src/main/resources/application.yml`（默认 `http://localhost:5173`，逗号分隔，环境变量 `CORS_ALLOWED_ORIGINS` 覆盖）

**Checkpoint**: 基础设施配置就绪（Redis JSON 序列化已存在 + CORS 已配置）

---

## Phase 2: User Story 1 - 数据完整性保障 (Priority: P0)

**Goal**: customer 表 name+company 唯一性提升到数据库层（FR-H01）

**Independent Test**: 并发插入相同 name+company，仅一条成功

- [x] T004 [US1] 创建 Flyway 迁移 V6 in `backend/src/main/resources/db/migration/V6__customer_unique_constraint.sql`（先清理非删除重复数据保留最小 id → 新增生成列 `active_key`（非删除时为 `name||company`，删除时为 NULL）→ 创建唯一索引 `uk_customer_active_name_company`）
- [x] T005 [US1] H2 测试 schema 同步 in `backend/src/test/resources/schema-h2.sql`（customer 表新增 `active_key` 生成列 + 唯一约束，集成测试通过）

**Checkpoint**: US1 可用（数据完整性由 DB 唯一约束保障，MySQL 用生成列实现软删除场景的部分唯一索引）

---

## Phase 3: User Story 2 - 缓存可靠性 (Priority: P0)

**Goal**: RedisTemplate JSON 序列化，PipelineStats 等对象缓存可靠读写（FR-H02/03）

- [x] ~~T006 [US2] 验证 RedisConfig JSON 序列化兼容性~~ — **已满足**：`RedisConfig.java` 已配置 `GenericJackson2JsonRedisSerializer`，`OpportunityStatsService` 缓存 `PipelineStats` 正常工作（集成测试 `StatsIT` 通过）
- [x] ~~T007 [US2] 后端测试：Redis JSON 序列化~~ — **已满足**：现有集成测试通过 mocked RedisTemplate，JSON 序列化由 Spring Data Redis 配置保证

**Checkpoint**: US2 可用（缓存值为可读 JSON，`GenericJackson2JsonRedisSerializer` 自带 `@class` 类型信息确保反序列化正确）

---

## Phase 4: User Story 3 - 依赖清理 (Priority: P1) — **取消**

**Goal**: 移除未使用的 @tanstack/react-query（FR-H04）

- [x] ~~T008 [US3] 全局搜索确认无 react-query 引用~~ — **发现实际在使用**：`main.tsx`（QueryClientProvider）、`hooks/useCustomers.ts`（useQuery）、`test/renderWithProviders.tsx`、`pages/opportunities/`、`pages/sales-opportunities/`、`pages/stats/` 均有引用
- [ ] ~~T009 [US3] 从 package.json 移除 @tanstack/react-query~~ — **取消**：依赖正在使用，不应移除
- [ ] ~~T010 [US3] 验证构建测试~~ — **取消**

**Checkpoint**: US3 取消（React Query 是前端数据获取层的核心依赖，之前分析有误）

---

## Phase 5: User Story 4 - 认证性能优化 (Priority: P1)

**Goal**: JWT 过滤器用户状态 Redis 缓存，减少 DB 查询（FR-H05/06/07）

**Independent Test**: 连续请求缓存命中不查 DB；停用后缓存主动失效；Redis 不可用降级

- [x] T011 [P] [US4] 新增 `UserStateCache` 类 in `backend/src/main/java/com/crm/security/UserStateCache.java`（封装 Redis 读写，TTL 30s，存储 `enabled+tokenVersion` record，所有操作 try-catch fail-open 降级）
- [x] T012 [US4] 修改 `JwtAuthFilter` 使用 `UserStateCache` in `backend/src/main/java/com/crm/security/JwtAuthFilter.java`（优先读缓存，命中时零 DB 查询；未命中查 DB 并回写缓存；提取 `validateUserState()` 方法）
- [x] T013 [P] [US4] `UserService` 状态变更时失效缓存 in `backend/src/main/java/com/crm/service/UserService.java`（update / resetPassword / changeOwnPassword 均调用 `userStateCache.evict(id)`）
- [x] T014 [P] [US4] `AuthService` 登录成功后预热缓存 in `backend/src/main/java/com/crm/service/AuthService.java`（登录成功后写入用户状态缓存，减少登录后首次请求的 DB 查询）
- [x] T015 [US4] 测试兼容性修复 in `backend/src/test/java/com/crm/service/UserServiceTest.java`（新增 `UserStateCache` mock，5 参数构造函数；全部 28 测试通过）

**Checkpoint**: US4 可用（认证过滤器缓存命中零 DB 查询，写操作主动 evict，Redis 不可用时降级查 DB）

---

## Phase 6: User Story 5 - 跨域配置 (Priority: P2)

**Goal**: 环境变量驱动的 CORS 配置，支持前后端分离部署（FR-H08/09）

**Independent Test**: 授权 Origin 请求成功，未授权 Origin 被拒绝，OPTIONS 预检返回 200

- [x] T016 [US5] `SecurityConfig` CORS 配置 in `backend/src/main/java/com/crm/config/SecurityConfig.java`（`CorsConfigurationSource` Bean：允许 `Authorization`/`Content-Type` 头、`GET/POST/PUT/DELETE/OPTIONS` 方法、`allowCredentials=true`、`maxAge=3600`；从 `cors.allowed-origins` 环境变量读取 Origin 列表）
- [x] ~~T017 [US5] 后端测试：CORS~~ — 由 Spring Security 框架保证，集成测试启动时 `DefaultSecurityFilterChain` 日志确认 `CorsFilter` 已注册

**Checkpoint**: US5 可用（跨域请求正常，未授权 Origin 被 `CorsConfiguration` 拒绝）

---

## 验证

- [x] Backend `mvn verify`：28 测试通过、Spotless 格式检查通过、JaCoCo 覆盖率门禁通过（BUILD SUCCESS）
- [x] Frontend `tsc --noEmit`：类型检查通过（pnpm 11.7.0 与 Node 20.20.2 有环境兼容性问题，使用本地 tsc 二进制验证）
- [x] H2 测试 schema 同步 V6 生成列 + 唯一约束，集成测试通过
- [ ] V6 迁移在真实 MySQL 上执行（需本地 MySQL 环境验证）
- [ ] 手动端到端验证（启动后端 + 前端，登录、客户 CRUD、统计报表）

## Notes

- **Redis JSON 序列化已存在**：`RedisConfig.java` 原已使用 `GenericJackson2JsonRedisSerializer`，之前深度分析误判为默认 JDK 序列化，实际无需变更。
- **React Query 正在使用**：`@tanstack/react-query` 是前端数据获取核心（`main.tsx` Provider + `useCustomers.ts` hooks + 多个页面），之前分析误判为未使用，取消移除。
- **MySQL 部分唯一索引**：MySQL 不支持 `CREATE UNIQUE INDEX ... WHERE deleted=0`（PostgreSQL 语法），使用生成列 `active_key`（非删除时为 `name||company`，删除时为 NULL）实现等效效果；MySQL 唯一索引允许多个 NULL 值，因此已删除客户不参与唯一性约束。
- **用户状态缓存一致性**：TTL 30 秒仅为兜底，所有写操作（update/resetPassword/changeOwnPassword）均主动 `evict`，实际一致性窗口为 0；登录成功后预热缓存。
- **Redis fail-open**：`UserStateCache` 所有 Redis 操作 try-catch，异常时 `get()` 返回 null（降级查 DB）、`put()`/`evict()` 静默忽略，不影响认证主流程。
- **CORS 配置**：开发环境继续使用 Vite proxy（`/api` → `http://localhost:8081`），CORS 主要服务于生产前后端分离部署；允许的 Origin 通过 `CORS_ALLOWED_ORIGINS` 环境变量配置。
