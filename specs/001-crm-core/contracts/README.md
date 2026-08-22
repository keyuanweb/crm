# REST API 契约（v1）

**Branch**: `001-crm-core` | **Date**: 2026-08-21 | **Spec**: [spec.md](../spec.md) | **Data Model**: [data-model.md](../data-model.md)

> 本目录是契约的权威文档源（章程原则一：契约先于实现）。
> 后端以 springdoc-openapi（Swagger 3.0）暴露同一契约；前端 `types/` 与 `services/` 按此对齐。
> 破坏性变更必须引入 v2 契约并保留 v1 弃用路径，禁止静默修改。

## 通用约定

- **Base URL**: `/api/v1`（版本前缀即契约版本）
- **认证**: 除登录/刷新外，所有接口要求 `Authorization: Bearer <accessToken>`
- **内容类型**: 请求/响应均为 `application/json; charset=utf-8`；文件接口为 `multipart/form-data` 或 `application/octet-stream`
- **分页信封**: 列表接口统一返回 `{ "items": [...], "total": <int>, "page": <int>, "pageSize": <int> }`
  - 请求参数：`page`（默认 1，≥1）、`pageSize`（默认 20，1~100）

### 错误格式

```json
{
  "code": "CUSTOMER_NOT_FOUND",
  "message": "客户不存在",
  "fieldErrors": [
    { "field": "phone", "message": "电话号码格式不正确" }
  ]
}
```

### HTTP 状态码

| 状态码 | 含义 |
|---|---|
| 200 | 成功 |
| 201 | 创建成功 |
| 400 | 参数校验失败（fieldErrors 非空） |
| 401 | 未认证 / 令牌无效或过期 |
| 403 | 无权限（角色不满足） |
| 404 | 资源不存在或已被删除 |
| 409 | 并发冲突（乐观锁版本不符）或唯一性冲突 |
| 422 | 业务规则拒绝（如关闭销售机会缺少结果） |
| 500 | 服务端错误（统一日志记录） |

### 角色权限矩阵

| 接口组 | ADMIN | SALES | SUPPORT |
|---|:-:|:-:|:-:|
| 认证 /auth | ✅ | ✅ | ✅ |
| 客户 /customers（CRUD+详情） | ✅ | ✅ | ✅ |
| 客户导入/导出 | ✅ | ❌ | ❌ |
| 商机 /opportunities | ✅ | ✅ | ❌ |
| 销售机会 /sales-opportunities | ✅ | ✅ | ❌ |
| 跟进 /follow-ups | ✅ | ✅ | ✅ |
| 统计 /stats/opportunity-pipeline | ✅ | ✅ | ❌ |
| 用户管理 /users（CRUD/重置密码，见 002 契约） | ✅ | ❌ | ❌ |
| 审计日志 /audit-logs（见 002 契约 README） | ✅ | ❌ | ❌ |

> 用户管理（002-user-management）端点契约见 `specs/002-user-management/contracts/`。

## 契约清单

| 文件 | 覆盖 |
|---|---|
| [auth.md](./auth.md) | 登录、刷新、登出、当前用户 |
| [customers.md](./customers.md) | 客户 CRUD、搜索分页、导入导出、模板 |
| [opportunities.md](./opportunities.md) | 商机（父）CRUD 与列表 |
| [sales-opportunities.md](./sales-opportunities.md) | 销售机会（子）CRUD、阶段列表、关闭 |
| [follow-ups.md](./follow-ups.md) | 跟进记录查询与维护 |
| [stats.md](./stats.md) | 商机管道统计 |
