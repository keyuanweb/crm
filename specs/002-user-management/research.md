# Research: 用户管理模块

**Branch**: `002-user-management` | **Date**: 2026-08-22

## 1. 令牌失效机制

**Decision**: `user` 表新增 `token_version` 列，JWT 携带 `tv`（tokenVersion）claim；`JwtAuthFilter` 每请求校验令牌内 `tv` 与数据库当前值一致。密码修改/重置时 `token_version+1`，使该用户全部已签发访问令牌立即失效。刷新令牌存 Redis，登出/重置时删除。

**Rationale**: 满足 FR-005/FR-006"重置/修改密码后旧令牌立即失效"；无需黑名单即可全局撤销（章程原则三安全要求）。

**Alternatives considered**: 全局 JWT 黑名单（每令牌 id 存 Redis）——撤销粒度细但需逐请求查 Redis 且令牌存量管理复杂；按用户版本号更简单可靠。

## 2. 账号启停即时生效

**Decision**: 认证过滤器每请求读取用户状态（enabled + tokenVersion），停用账号后其现有令牌立即失效；不依赖前端隐藏（服务端强制，章程原则三）。

**Rationale**: FR-004 要求角色/启停变更即时生效；003 模块进一步用 Redis 缓存用户状态降低 DB 压力。

## 3. 防护规则

**Decision**: 禁止停用/删除当前登录账号；系统至少保留一个启用 ADMIN（校验时排除当前操作者后计数）；用户名唯一且创建后不可改；密码 8~64 位含字母与数字（bcrypt 存储）。

**Rationale**: spec 边界情况明确；防止锁死系统。

## 4. 删除策略

**Decision**: 不提供物理删除，用"停用"代替，避免破坏审计与历史数据关联（FR-017 审计一致性）。

**Rationale**: 与既有实体逻辑删除惯例一致。

## 5. 契约与权限

**Decision**: 契约写入 `contracts/users.md`（用户 CRUD/列表/详情/重置密码/改密/审计查询）。用户管理仅 ADMIN；登录用户仅可改自己密码与查看自己信息。

**Rationale**: 复用 001 认证基础（JWT/BCrypt/Redis 刷新令牌）与审计写入；服务端强制授权。
