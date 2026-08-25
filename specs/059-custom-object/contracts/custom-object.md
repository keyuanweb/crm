# 契约：自定义对象 /api/v1/custom-objects

## 对象定义（仅 ADMIN）

### GET /custom-objects

对象列表分页。**Query**: `keyword`、`page`、`pageSize`。

### POST /custom-objects

创建对象。**Body**

```json
{
  "name": "项目", "code": "PROJECT",
  "fields": [
    { "field": "name", "label": "项目名称", "type": "TEXT", "required": true },
    { "field": "status", "label": "状态", "type": "SELECT", "options": "进行中,已完成" }
  ],
  "enabled": true
}
```

**Response 201**: 对象结构（含 id/version）。

### PUT /custom-objects/{id} / DELETE /custom-objects/{id} / POST /{id}/toggle

编辑（含 version）/ 逻辑删除 / 启停。

## 记录管理（ADMIN + SALES）

### GET /custom-objects/{id}/records?keyword&page&pageSize

对象记录列表（搜索按字段值）。**Response 200**

```json
{
  "items": [
    { "id": 1, "objectId": 1, "values": { "name": "CRM 重构", "status": "进行中" }, "createdAt": "..." }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

### POST /custom-objects/{id}/records

创建记录。**Body**: `{ "values": { "name": "CRM 重构", "status": "进行中" } }`。**Response 201**。

**校验**: 必填字段缺失 → 422 OBJECT_FIELD_REQUIRED；对象停用 → 422 OBJECT_DISABLED。

### PUT /custom-objects/{id}/records/{recordId} / DELETE ...

编辑（含 version）/ 删除。

### GET /custom-objects/{id}/records/{recordId}

记录详情。

## 错误码

| code | status | 含义 |
|---|---|---|
| OBJECT_CODE_DUPLICATE | 409 | 对象编码已存在 |
| OBJECT_FIELD_INVALID | 422 | 字段定义不合法 |
| OBJECT_NOT_FOUND | 404 | 对象不存在 |
| OBJECT_DISABLED | 422 | 对象已停用，不可新建记录 |
| OBJECT_FIELD_REQUIRED | 422 | 必填字段缺失 |
