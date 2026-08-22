# 契约：客户公海 /customers/pool

**Base**: `/api/v1/customers`（公海列表/领取：所有登录用户；扫描/批量转移：仅 ADMIN）

## GET /customers/pool

公海客户分页列表（FR-PL02，owner 为空）。

**Query**: `keyword`、`status`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "name": "公海客户", "company": "公海公司", "status": "ACTIVE",
      "ownerId": null, "ownerName": null, "version": 0, "createdAt": "2026-08-01T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /customers/pool/{id}/claim

从公海领取客户（FR-PL03，owner 空 → 本人）。

**Body**: 无。

**Response 200**: 更新后客户结构（ownerId=本人）。

**错误**: 404 CUSTOMER_NOT_FOUND / 409 CUSTOMER_ALREADY_OWNED（已被他人领取）。

## GET /customers/my

我的客户分页列表（FR-PL04，owner=本人）。

**Query**: `keyword`、`status`、`page`、`pageSize`。

**Response 200**: 同公海列表结构（ownerName=本人）。

## POST /customers/pool/scan（ADMIN）

执行公海扫描（FR-PL06）：超 N 天未跟进/创建且已归属的客户退回公海。

**Body**: 无。

**Response 200**

```json
{ "returnedCount": 3 }
```

## POST /customers/batch-transfer（ADMIN）

批量转移/分配客户（FR-PL07）。

**Body**:

```json
{ "customerIds": [1, 2, 3], "targetOwnerId": 5 }
```

**Response 200**

```json
{ "updatedCount": 3 }
```

**校验**: customerIds 非空且 ≤100；targetOwnerId 对应用户须存在（404 USER_NOT_FOUND）。

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| CUSTOMER_ALREADY_OWNED | 409 | 客户已被领取/已有归属 |
| USER_NOT_FOUND | 404 | 目标用户不存在 |
| CUSTOMER_NOT_FOUND | 404 | 客户不存在 |

## 权限矩阵

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /customers/pool、GET /customers/my | ✅ | ✅ | ✅ |
| POST /customers/pool/{id}/claim | ✅ | ✅ | ✅ |
| POST /customers/pool/scan | ✅ | ❌ | ❌ |
| POST /customers/batch-transfer | ✅ | ❌ | ❌ |
