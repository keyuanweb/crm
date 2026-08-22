# 数据模型：系统加固与优化模块

**Branch**: `003-system-hardening` | **Date**: 2026-08-22

## 1. customer（增强，Flyway V6）

| 列 | 类型 | 约束 | 说明 |
|---|---|---|---|
| active_key | VARCHAR(201) GENERATED | NULL（删除行）| 生成列：非删除时为 `name||company`，删除时为 NULL |

- 唯一索引 `uk_customer_active_name_company`（active_key）：仅约束未删除数据的 name+company 唯一。

## 2. Redis 缓存结构（UserStateCache）

- Key: `user:state:{userId}`，TTL 30s。
- Value: `{ "enabled": bool, "tokenVersion": int }`（JSON 序列化）。
- 写路径：登录预热（AuthService）、状态变更 evict（UserService）。
- 降级：Redis 异常时 get 返回 null（查 DB）、put/evict 静默忽略。

## 3. 配置项

| 配置 | 默认 | 说明 |
|---|---|---|
| `cors.allowed-origins` | `http://localhost:5173` | 允许跨域 Origin（逗号分隔，`CORS_ALLOWED_ORIGINS` 覆盖） |

## 4. 不变式

- 未删除客户 (name, company) 全局唯一（DB 强制）。
- 缓存 TTL ≤30s；所有写操作主动失效，一致性窗口 0。
- Redis 不可用不阻塞认证主流程（fail-open）。
