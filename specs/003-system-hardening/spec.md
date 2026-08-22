# 功能规格：系统加固与优化

**功能分支**: `003-system-hardening`

**创建日期**: 2026-08-22

**状态**: 草稿

**输入**: 代码审查发现的改进点——数据完整性缺口、缓存序列化风险、未使用依赖、认证过滤器性能、跨域配置缺失。由 `/speckit-analyze` 深度审查驱动。

## 用户场景与测试（必填）

### 用户故事 1 - 数据完整性保障（优先级：P0）

系统在并发场景下必须保证客户数据的唯一性，不依赖应用层校验。

**优先级理由**: 当前 `customer` 表的 `name+company` 唯一性仅在 Service 层通过 `selectCount` 校验，高并发下存在竞态条件，可能插入重复数据。数据库唯一索引是最终防线，列为 P0。

**独立测试**: 并发创建相同 name+company 的客户，仅一条成功。

**验收场景**:

1. **Given** 数据库已有客户（name="Acme", company="Acme Inc"），**When** 并发插入相同 name+company 的客户，**Then** 仅一条成功，其余收到唯一约束冲突错误。
2. **Given** 迁移执行前，**When** 表中已存在重复数据，**Then** 迁移应先清理重复数据再创建索引（或迁移失败并提示人工处理）。

---

### 用户故事 2 - 缓存可靠性（优先级：P0）

Redis 缓存必须使用可靠的序列化方式，确保对象缓存可正确读写。

**优先级理由**: 当前 `RedisTemplate<String, Object>` 使用默认 JDK 序列化，`OpportunityStatsService` 缓存 `PipelineStats` 对象时可能因类版本变化或序列化兼容性导致反序列化失败。改为 JSON 序列化可提升可观测性和兼容性，列为 P0。

**独立测试**: 写入 PipelineStats 到 Redis，读取后数据完整。

**验收场景**:

1. **Given** Redis 中已缓存 PipelineStats，**When** 调用 `getPipeline()`，**Then** 命中缓存并返回正确反序列化的对象。
2. **Given** Redis 中缓存的是 JSON 格式，**When** 用 `redis-cli` 查看，**Then** 值为可读的 JSON 字符串。

---

### 用户故事 3 - 依赖清理（优先级：P1）

项目不应包含未使用的依赖，遵循 YAGNI 原则。

**优先级理由**: `@tanstack/react-query` 已在 `package.json` 中声明但代码中从未使用，增加安装时间和安全攻击面。列为 P1。

**独立测试**: 移除依赖后 `pnpm install` + `pnpm run build` + `pnpm run test` 全部通过。

**验收场景**:

1. **Given** 已移除 `@tanstack/react-query`，**When** 执行 `pnpm run build`，**Then** 构建成功无类型错误。
2. **Given** 已移除依赖，**When** 全局搜索 `react-query` / `useQuery` / `useMutation`，**Then** 无匹配结果。

---

### 用户故事 4 - 认证性能优化（优先级：P1）

JWT 认证过滤器不应在每次请求时查询数据库获取用户状态。

**优先级理由**: 当前 `JwtAuthFilter` 每个请求都执行 `userMapper.selectById(userId)`，在高并发下成为数据库瓶颈。用户状态（enabled/tokenVersion）变化频率低，适合短 TTL Redis 缓存。列为 P1。

**独立测试**: 连续请求同一用户，第二次命中缓存不查 DB；停用用户后缓存失效即时拒绝。

**验收场景**:

1. **Given** 用户已登录且缓存已建立，**When** 连续发起 10 次请求，**Then** 仅第一次查 DB，其余命中 Redis 缓存。
2. **Given** 用户已被停用（tokenVersion 递增或 enabled=false），**When** 缓存 TTL 内发起请求，**Then** 写操作时主动失效缓存，读请求在缓存 TTL（≤30s）后失效。
3. **Given** Redis 不可用，**When** 发起请求，**Then** 降级为直接查 DB，不影响认证。

---

### 用户故事 5 - 跨域配置（优先级：P2）

系统应支持前后端分离部署，正确配置 CORS。

**优先级理由**: 当前 `SecurityConfig` 中 `cors.disable()`，前后端部署在不同域名/端口时浏览器将拦截请求。列为 P2（开发环境可通过 Vite proxy 绕过，但生产部署需要）。

**独立测试**: 从不同源的前端页面发起 API 请求，浏览器不拦截。

**验收场景**:

1. **Given** 前端运行在 `http://localhost:5173`，后端在 `http://localhost:8081`，**When** 前端发起 API 请求，**Then** 响应包含正确的 CORS 头且浏览器不拦截。
2. **Given** 未授权的 Origin，**When** 发起请求，**Then** 响应不包含 CORS 头（被拒绝）。
3. **Given** 预检请求（OPTIONS），**When** 发起，**Then** 返回 200 且包含允许的方法和头。

### 边界情况

- 客户唯一索引迁移前需检查并清理已有重复数据。
- Redis 缓存用户状态时，写操作（停用/改密/重置密码）必须主动失效缓存。
- Redis 不可用时所有缓存操作必须降级，不影响主流程。
- CORS 允许的 Origin 应通过环境变量配置，不可硬编码。
- 移除依赖前必须确认无任何代码引用。

## 需求（必填）

### 功能需求

#### 数据完整性

- **FR-H01**: `customer` 表必须在数据库层创建 `(name, company)` 唯一索引，迁移前清理重复数据。

#### 缓存可靠性

- **FR-H02**: `RedisTemplate` 必须配置 JSON 序列化（Jackson2JsonRedisSerializer），键使用 String 序列化，值使用 JSON 序列化。
- **FR-H03**: 刷新令牌存储（String）和登录失败计数器（Integer）必须兼容新的序列化配置。

#### 依赖清理

- **FR-H04**: 移除 `@tanstack/react-query` 依赖，确认无代码引用。

#### 认证性能

- **FR-H05**: `JwtAuthFilter` 必须使用 Redis 缓存用户状态（enabled/tokenVersion），TTL 不超过 30 秒。
- **FR-H06**: 用户状态变更（停用/启用/改密/重置密码）时必须主动删除对应缓存。
- **FR-H07**: Redis 不可用时必须降级为直接查 DB，不抛出异常。

#### 跨域配置

- **FR-H08**: 后端必须配置 CORS，允许的 Origin 通过环境变量 `CORS_ALLOWED_ORIGINS` 配置（逗号分隔），默认包含 `http://localhost:5173`。
- **FR-H09**: CORS 必须允许 `Authorization` 和 `Content-Type` 头，允许 `GET/POST/PUT/DELETE/OPTIONS` 方法，允许凭证。

### 关键实体（涉及数据）

- **客户（Customer）**: 新增数据库唯一约束 `uk_customer_name_company (name, company)`。
- **用户（User）**: 无 schema 变更，新增 Redis 缓存层 `auth:user-state:<userId>`。

## 成功标准（必填）

### 可度量结果

- **SC-H01**: 并发创建相同 name+company 客户时，重复插入被数据库拒绝（100% 拦截）。
- **SC-H02**: Redis 中缓存的值为可读 JSON 格式，`getPipeline()` 缓存命中率 ≥95%（5 分钟 TTL 内）。
- **SC-H03**: 移除依赖后前端构建、测试、Lint、类型检查全部通过。
- **SC-H04**: 认证过滤器在缓存命中时不查 DB，缓存 TTL ≤30 秒；用户停用后缓存被主动失效。
- **SC-H05**: 前后端分离部署时跨域请求成功，未授权 Origin 被拒绝。

## 假设

- 客户表中当前不存在 `name+company` 重复数据（迁移前需验证，如有重复需先清理）。
- Redis 在生产环境可用且稳定；缓存仅用于性能优化，不作为唯一数据源。
- 前端开发环境继续使用 Vite proxy（`/api` → `http://localhost:8081`），CORS 主要服务于生产分离部署。
- `@tanstack/react-query` 确实未被任何源码引用（需全局搜索确认）。
