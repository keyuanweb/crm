# Implementation Plan: 系统加固与优化

**Branch**: `003-system-hardening` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-system-hardening/spec.md`

## Summary

对 CRM 系统进行五项加固与优化：(1) 客户 name+company 唯一性提升到数据库层；(2) RedisTemplate 改为 JSON 序列化；(3) 移除未使用的 React Query 依赖；(4) JWT 过滤器用户状态 Redis 缓存（≤30s TTL）；(5) 环境变量驱动的 CORS 配置。所有变更遵循既有分层架构与章程约束。

## Technical Context

**Language/Version**: Java 17 / TypeScript 5（沿用既有技术栈）

**Primary Dependencies**: Spring Data Redis（既有）、MyBatis-Plus（既有）、Spring Security（既有）、Flyway（既有）

**Storage**: MySQL `customer` 表新增唯一索引（Flyway V6 迁移）；Redis 新增 `auth:user-state:<userId>` 缓存键

**Testing**: JUnit 5 + Spring Boot Test（后端）、Vitest（前端）、H2 内存数据库（集成测试）

**Target Platform**: Web（前后端分离）

**Project Type**: 现有 Web 应用的非功能性增强

**Performance Goals**: JWT 认证过滤器在缓存命中时零 DB 查询；客户唯一性在并发下 100% 拦截

**Constraints**: 
- Redis 不可用时所有缓存操作必须降级（fail-open）
- CORS Origin 必须通过环境变量配置，不可硬编码
- 移除依赖前必须全局搜索确认无引用
- 迁移前必须检查 customer 表是否有重复数据

**Scale/Scope**: 非功能性增强，不改变业务功能与 API 契约

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 章程条款 | 门禁要求 | 状态 |
|---|---|---|
| 原则一：契约优先 | API 契约不变，无新增/修改端点 | ✅ 满足（纯内部增强，无 API 变更） |
| 原则二：分层架构 | 业务逻辑在 Service，缓存配置在 Config | ✅ 满足（RedisConfig/SecurityConfig 配置层，JwtAuthFilter 安全层） |
| 原则三：数据完整性、安全与校验 | 数据库唯一约束、服务端授权、缓存失效 | ✅ 满足（DB 唯一索引 + 写操作主动失效缓存） |
| 原则四：测试优先与质量门禁 | 测试先于实现、合并门禁 | ✅ 满足（补充单元/集成测试 + 既有门禁） |
| 原则五：简洁、可维护与可观测 | YAGNI、结构化日志、无 N+1 | ✅ 满足（移除未使用依赖、缓存操作 try-catch + warn 日志） |
| 技术与架构约束 | 复用既有技术栈，无新增框架 | ✅ 满足（仅配置变更 + 迁移脚本） |

**结论**: 无门禁违规，无需豁免。

## Project Structure

### Documentation (this feature)

```text
specs/003-system-hardening/
├── spec.md            # 功能规格
├── plan.md            # 本文件
└── tasks.md           # 任务分解
```

### Source Code Changes

```text
backend/src/main/java/com/crm/
├── config/
│   ├── RedisConfig.java          # 修改：JSON 序列化配置 + 用户状态缓存 Bean
│   └── SecurityConfig.java       # 修改：CORS 配置（替换 cors.disable()）
├── security/
│   ├── JwtAuthFilter.java        # 修改：用户状态 Redis 缓存
│   └── UserStateCache.java       # 新增：用户状态缓存封装（TTL 30s，fail-open）
├── service/
│   ├── UserService.java          # 修改：状态变更时失效用户状态缓存
│   └── AuthService.java          # 修改：登录成功后预热用户状态缓存（可选）
└── resources/
    ├── application.yml           # 修改：新增 cors.allowed-origins 配置
    └── db/migration/
        └── V6__customer_unique_constraint.sql  # 新增：清理重复 + 创建唯一索引

backend/src/test/java/com/crm/
├── config/RedisConfigTest.java   # 新增：JSON 序列化验证
├── security/JwtAuthFilterCacheTest.java  # 新增：缓存命中/失效/降级测试
└── integration/CustomerUniqueConstraintIT.java  # 新增：并发唯一约束测试

frontend/
├── package.json                   # 修改：移除 @tanstack/react-query
└── pnpm-lock.yaml                 # 自动更新（pnpm install）
```

**Structure Decision**: 
- 用户状态缓存封装为独立类 `UserStateCache`，避免在 Filter 中散落 Redis 操作逻辑
- CORS 配置通过 `CorsConfigurationSource` Bean 注入，Origin 从 `@Value` 读取
- V6 迁移先执行重复数据清理（保留 id 最小的记录），再创建唯一索引

## Complexity Tracking

| 复杂度项 | 说明 | 缓解措施 |
|---|---|---|
| Redis 序列化变更影响现有键 | 刷新令牌 `auth:refresh:<id>` 和登录失败计数器 `auth:fail:<username>` 已有数据为 JDK 序列化 | 迁移后首次读取旧键会失败，但刷新令牌有 7 天 TTL，失败计数器有 15 分钟 TTL；配置 `RedisTemplate` 后新写入为 JSON，旧键自然过期。在 `RedisConfig` 中为 String 类型操作使用独立的 `StringRedisTemplate` 避免兼容问题 |
| 缓存与 DB 一致性窗口 | 用户状态缓存 TTL 30s，停用后最多 30s 内旧令牌仍可能通过 | 写操作（停用/改密/重置密码）主动调用 `evict(userId)` 立即失效缓存，30s TTL 仅为兜底 |
| customer 重复数据清理 | 迁移前若存在重复数据，创建索引会失败 | V6 迁移先执行 `DELETE c1 FROM customer c1 INNER JOIN customer c2 WHERE c1.id > c2.id AND c1.name = c2.name AND c1.company = c2.company AND c1.deleted = 0` 保留最小 id，再建索引 |

无违规，以上为已知风险及缓解措施。
