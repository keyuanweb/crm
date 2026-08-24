# 契约：回收站 recycle-bin

**Base**: `/api/v1/recycle-bin`（ADMIN 全量；SALES 仅本人，系统管理功能）

## GET /recycle-bin

回收站列表（跨实体，合并分页）。

**Query**: `type`（可选：CUSTOMER/LEAD/CONTACT/OPPORTUNITY）、`keyword`（可选）、`page`、`pageSize`。

**Response 200**

```json
{
  "success": true,
  "data": {
    "items": [
      { "type": "CUSTOMER", "id": 3, "name": "Acme 科技", "deletedAt": "2026-08-23T10:00:00", "deletedBy": 1 }
    ],
    "total": 1, "page": 1, "pageSize": 20
  },
  "error": null
}
```

## POST /recycle-bin/restore

批量恢复（deleted=0）。

**Body**: `{ "items": [ { "type": "CUSTOMER", "id": 3 }, ... ] }`

**Response 200**: `{ "success": true, "data": { "restoredCount": 1, "failures": [ { "type": "CUSTOMER", "id": 4, "message": "客户名称与现有记录重复" } ] } }`

## POST /recycle-bin/purge

彻底删除（物理删）。

**Body**: `{ "items": [ { "type": "CUSTOMER", "id": 3 }, ... ] }`

**Response 200**: `{ "success": true, "data": { "purgedCount": 1 } }`

## 备注

- 数据权限：SALES 仅本人删除记录，ADMIN 全量。
- 恢复唯一性冲突（客户 name+company）跳过并提示。
- 审计：恢复/彻底删除记录审计日志。
