# 契约：系统增强 - 自定义字段 /custom-fields

**Base**: `/api/v1/custom-fields`（仅 ADMIN 配置；值读写随实体接口）

## GET /custom-fields

字段定义列表（按实体筛选）。

**Query**: `entityType`（必填：LEAD/CUSTOMER/OPPORTUNITY/TICKET）、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "entityType": "LEAD", "name": "预算规模", "fieldType": "NUMBER",
      "required": true, "options": null, "enabled": true, "sortOrder": 0,
      "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /custom-fields/definitions

某实体启用的字段定义（业务用户读取，供表单渲染；全员可读）。

**Query**: `entityType`（必填）。

**Response 200**: `data` 为字段定义数组（仅 enabled=1，按 sortOrder 排序）。

## POST /custom-fields

创建字段定义。

**Body**:

```json
{ "entityType": "LEAD", "name": "预算规模", "fieldType": "NUMBER", "required": true }
```

**Response 201**: 字段定义结构。

**校验**: entityType/name/fieldType 必填；name ≤50 且实体内唯一（409 CUSTOM_FIELD_DUPLICATE）；SELECT 必有 options 其余必须为空（422 CUSTOM_FIELD_INVALID）。

## PUT /custom-fields/{id}

编辑字段定义（含 version）。

## DELETE /custom-fields/{id}

删除字段定义（物理删除关联值）。

## 值读写（随实体接口）

- 创建/编辑实体：请求体含 `customFieldValues: [{ "fieldId": 1, "value": "500万" }]`；必填字段缺失 → 422 CUSTOM_FIELD_REQUIRED。
- 实体详情/列表响应：`customFieldValues: [{ "fieldId": 1, "fieldName": "预算规模", "value": "500万" }]`。
- 列表筛选：`cf_<fieldId>=值` 参数（文本 LIKE / 下拉 EQ）。

## 错误码

| code | status | 含义 |
|---|---|---|
| CUSTOM_FIELD_NOT_FOUND | 404 | 字段定义不存在 |
| CUSTOM_FIELD_DUPLICATE | 409 | 实体内字段名已存在 |
| CUSTOM_FIELD_INVALID | 422 | 类型与选项不匹配 |
| CUSTOM_FIELD_REQUIRED | 422 | 必填字段缺失 |
