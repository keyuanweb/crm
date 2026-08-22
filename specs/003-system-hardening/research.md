# Research: 系统加固与优化模块

**Branch**: `003-system-hardening` | **Date**: 2026-08-22

## 1. 数据库唯一约束（软删除场景）

**Decision**: `customer` 表新增生成列 `active_key`（非删除时为 `name||company`，删除时为 NULL），创建唯一索引 `uk_customer_active_name_company`。MySQL 唯一索引允许多个 NULL，因此已删除客户不参与唯一性约束，实现"仅对未删除数据唯一"。

**Rationale**: MySQL 不支持 `CREATE UNIQUE INDEX ... WHERE deleted=0`（PostgreSQL 语法）；生成列是等效实现。DB 唯一索引是并发唯一性的最终防线（Service 层 selectCount 校验存在竞态）。

**Alternatives considered**: 应用层分布式锁 —— 复杂且仍可能漏过；物理删除 —— 破坏审计。生成列方案最简洁。

## 2. Redis JSON 序列化

**Decision**: `RedisConfig` 使用 `GenericJackson2JsonRedisSerializer`（键 String、值 JSON），`OpportunityStatsService` 缓存的 `PipelineStats` 等对象可靠读写。

**Rationale**: 审计发现序列化配置已正确（非默认 JDK 序列化），无需变更；JSON 可观测且自带 `@class` 类型信息。

## 3. 认证过滤器性能（UserStateCache）

**Decision**: 新增 `UserStateCache`（Redis，TTL 30s，存 `enabled+tokenVersion` record），`JwtAuthFilter` 优先读缓存，命中零 DB 查询；写操作（update/resetPassword/changeOwnPassword）主动 evict；Redis 不可用时 fail-open 降级查 DB。

**Rationale**: 用户状态变化频率低、一致性窗口由写操作 evict 保证为 0；TTL 仅兜底。

## 4. CORS 配置

**Decision**: `SecurityConfig` 提供 `CorsConfigurationSource` Bean，从 `cors.allowed-origins`（环境变量 `CORS_ALLOWED_ORIGINS`）读取允许 Origin，支持前后端分离部署；开发环境继续用 Vite proxy。

**Rationale**: 生产部署需要；开发环境无影响。

## 5. 依赖清理结论

**Decision**: `@tanstack/react-query` 确认正在使用（Provider + hooks + 多页面），**不移除**；原"未使用依赖"分析有误。

**Rationale**: YAGNI 原则反向应用——依赖在用即保留。
