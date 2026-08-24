# 契约：全局搜索

**Base**: `/api/v1/search`（需认证）

## GET /search?keyword=acme

下拉即时结果（分组 Top 5/实体）。

**Response 200**:
```json
{
  "success": true,
  "data": {
    "keyword": "acme",
    "groups": [
      { "type": "CUSTOMER", "label": "客户", "items": [ { "id": 1, "title": "Acme 科技", "subtitle": "Acme Inc.", "path": "/customers/1" } ] },
      { "type": "LEAD", "label": "线索", "items": [ { "id": 2, "title": "张三", "subtitle": "Acme 科技", "path": "/leads/2" } ] }
    ]
  },
  "error": null
}
```

## GET /search/full?keyword=acme&type=CUSTOMER&page=1&pageSize=20

结果页（单实体分页，type 可选 ALL）。

**Response**: `{ "data": { "groups": [...], "total": n, "page": 1, "pageSize": 20 } }`

## 搜索字段

| 实体 | 字段 |
|---|---|
| CUSTOMER | name/company/contact_person/phone |
| LEAD | name/company |
| CONTACT | name/phone |
| OPPORTUNITY | name |
| TICKET | title |
| PRODUCT | name/code |

## 备注

- 多关键字空格 AND；LIKE %词%。
- 数据权限：ADMIN 全量/其他按创建人。
- 下拉 Top 5/实体；结果页 20/页。
