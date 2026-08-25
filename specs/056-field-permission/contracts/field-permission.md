# 契约：字段级读写权限 /api/v1/field-permissions

**Base**: `/api/v1/field-permissions`（仅 ADMIN 配置）

## GET /field-permissions

配置列表（按角色/实体筛选）。**Query**: `roleId`、`entityType`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "roleId": 2, "roleName": "销售", "entityType": "CUSTOMER",
      "fieldId": 5, "fieldName": "手机号", "permission": "READ_ONLY", "createdAt": "..." }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /field-permissions

创建/更新（覆盖式 upsert：角色+实体+字段 唯一）。**Body**: `{ "roleId": 2, "entityType": "CUSTOMER", "fieldId": 5, "permission": "READ_ONLY" }`。**Response 201**。

## PUT /field-permissions/{id} / DELETE /field-permissions/{id}

编辑 / 删除。

## 应用（字段列表 + 保存）

### GET /api/v1/custom-fields?entityType=CUSTOMER（扩展）

字段列表响应每项增加：

```json
"permission": { "hidden": false, "readOnly": true }
```

（基于当前登录角色计算；ADMIN 恒 editable。）

### 保存拦截（CustomFieldService.saveValues）

- 提交 HIDDEN 字段值 → 422 `FIELD_HIDDEN`。
- 提交 READ_ONLY 字段（修改已有值）→ 422 `FIELD_READ_ONLY`。

## 错误码

| code | status | 含义 |
|---|---|---|
| FIELD_PERMISSION_INVALID | 422 | 权限值不合法（须 HIDDEN/READ_ONLY/EDITABLE） |
| FIELD_HIDDEN | 422 | 该字段对当前角色隐藏，不可写入 |
| FIELD_READ_ONLY | 422 | 该字段对当前角色只读，不可修改 |
| FIELD_PERMISSION_NOT_FOUND | 404 | 权限配置不存在 |
