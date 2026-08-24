# 契约：客户查重合并

**Base**: `/api/v1/customers/duplicates`、`/api/v1/customers/merge`（customer:merge 权限，ADMIN/SALES 数据范围）

## GET /customers/duplicates?page=&pageSize=

查重扫描结果（分批自动扫描全部可见客户）。

**Response 200**:
```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1,
        "primaryId": 1,
        "primaryName": "Acme 科技",
        "duplicates": [
          { "customerId": 2, "name": "Acme 科技", "company": "Acme Inc.", "similarity": 100, "relatedCount": 3 }
        ]
      }
    ],
    "total": n
  },
  "error": null
}
```

## POST /customers/merge

合并（主记录保留 + 从记录关联数据转移 + 从记录回收站）。

**Body**: `{ "primaryId": 1, "duplicateId": 2 }`

**Response 200**: `{ "success": true, "data": { "primaryId": 1, "movedOrders": 2, "movedOpportunities": 1, "movedContacts": 3, "movedFollowUps": 5, "movedTickets": 0 } }`

## 查重规则

- 名称归一化（去空格/大小写/全半角）完全一致 + 公司一致 → 100
- 名称包含 + 公司一致 → 90
- 电话/邮箱精确一致 → 85

## 备注

- 合并单事务：转移 → 冲突主优先 → 从记录逻辑删除 + 回收站 + 审计。
- 从记录恢复（回收站）后仅自身，关联数据已在主记录。
