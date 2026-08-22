# REST API 契约（002-user-management）目录

**Branch**: `002-user-management` | **Date**: 2026-08-22 | **Spec**: [spec.md](../spec.md)

> 本目录为 002 新增端点（用户管理、审计日志查询）的契约权威源；
> 通用约定（Base URL `/api/v1`、Bearer 认证、分页信封、错误格式、HTTP 状态码）沿用
> [001 契约 README](../../001-crm-core/contracts/README.md)，此处不重复。

## 契约清单

| 文件 | 覆盖 |
|---|---|
| [users.md](./users.md) | 用户 CRUD/列表/详情、管理员重置密码、本人改密、审计日志查询（含权限矩阵与错误约定） |

## 与 001 的关系

- 002 复用了 001 的认证基础设施（JWT、BCrypt、刷新令牌 Redis 存储）与审计写入（AuditService/audit_log 表）。
- 001 契约 README 的权限矩阵已补充"用户管理 /users"与"审计日志 /audit-logs"两行。
- 契约测试：`backend/src/test/java/com/crm/contract/UserContractTest.java` 校验本目录端点的请求/响应形状与状态码约定。
