# 契约：数据权限 /departments /customer-shares

**Base**: `/api/v1/departments`（仅 ADMIN）、`/api/v1/customer-shares`（归属者+ADMIN 管理，登录用户查看）

## GET /departments/tree（ADMIN）

部门树（含层级）。

**Response 200**

```json
{
  "departments": [
    { "id": 1, "name": "销售一部", "parentId": null, "children": [
        { "id": 2, "name": "华东组", "parentId": 1, "children": [] }
    ] }
  ]
}
```

## POST /departments（ADMIN）

**Body**: `{ "name": "华东组", "parentId": 1 }`

**Response 201**: 部门结构。

## PUT /departments/{id}（ADMIN）

编辑部门（含 version）。

## DELETE /departments/{id}（ADMIN）

逻辑删除部门；有成员或子部门 → 409 DEPARTMENT_HAS_CHILDREN_OR_MEMBERS。

## PUT /users/{id}/data-permission（ADMIN）

设置用户部门与数据权限范围。

**Body**: `{ "departmentId": 2, "dataScope": "DEPT_AND_CHILD" }`

**Response 200**: 更新后用户信息。

---

# 契约：客户共享 /customer-shares

## POST /customer-shares

共享客户给用户（归属者或 ADMIN）。

**Body**: `{ "customerId": 1, "sharedToUserId": 5 }`

**Response 201**: 共享记录。

**错误**: 403（非归属者非 ADMIN）/ 409 SHARE_EXISTS（已共享）。

## DELETE /customer-shares/{id}

取消共享（归属者或 ADMIN）。

## GET /customer-shares/shared-to-me

共享给我的客户列表（登录用户，只读）。

**Query**: `page`、`pageSize`。

**Response 200**

```json
{ "items": [ { "customerId": 1, "customerName": "共享客户", "company": "共享公司",
               "sharedBy": 3, "sharedAt": "2026-08-22T10:00:00" } ], "total": 1, "page": 1, "pageSize": 20 }
```

## 权限与数据隔离（客户列表）

- `GET /customers`：按当前用户 data_scope 过滤（SELF/DEPT/DEPT_AND_CHILD/ALL），共享客户可见（详情只读）。
- 编辑/删除：owner=本人（或 ADMIN ALL）；共享用户 403。

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| DEPARTMENT_NOT_FOUND | 404 | 部门不存在 |
| DEPARTMENT_HAS_CHILDREN_OR_MEMBERS | 409 | 部门存在子部门或成员 |
| SHARE_EXISTS | 409 | 已共享给该用户 |
| SHARE_NOT_FOUND | 404 | 共享记录不存在 |

## 权限矩阵

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /departments/tree、POST/PUT/DELETE /departments | ✅ | ❌ | ❌ |
| PUT /users/{id}/data-permission | ✅ | ❌ | ❌ |
| POST/DELETE /customer-shares | ✅（任意） | ✅（仅归属者） | ❌ |
| GET /customer-shares/shared-to-me | ✅ | ✅ | ✅ |
