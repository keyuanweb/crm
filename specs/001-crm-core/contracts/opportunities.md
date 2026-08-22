# 契约：商机（父实体） /opportunities

**Base**: `/api/v1/opportunities`（权限：ADMIN + SALES，见 README 矩阵）

## GET /opportunities

分页查询商机列表（FR-007）。

**Query**: `page`, `pageSize`, `keyword`（对 name 模糊匹配）, `customerId`, `status`（ACTIVE/ARCHIVED）

**Response 200**（分页信封）

```json
{
  "items": [
    { "id": 10, "name": "年度合作", "customerId": 1, "customerName": "张三",
      "expectedAmountMin": 100000, "expectedAmountMax": 500000,
      "remark": "", "status": "ACTIVE", "salesOpportunityCount": 2, "createdAt": "2026-08-21T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /opportunities/{id}

商机详情：基本信息 + 关联客户 + 下属销售机会列表（FR-008）。

**Response 200**

```json
{
  "id": 10, "name": "年度合作", "customerId": 1, "customerName": "张三",
  "expectedAmountMin": 100000, "expectedAmountMax": 500000, "remark": "", "status": "ACTIVE",
  "salesOpportunities": [
    { "id": 20, "amount": 300000, "stage": "NEGOTIATING", "expectedCloseDate": "2026-09-30", "closeResult": null }
  ]
}
```

**错误**: 404 `OPPORTUNITY_NOT_FOUND`。

## POST /opportunities

创建商机（FR-009）：必须关联一个未删除的客户。

**Request**

```json
{
  "customerId": 1, "name": "年度合作",
  "expectedAmountMin": 100000, "expectedAmountMax": 500000, "remark": ""
}
```

**Response**: 201；**错误**: 404 `CUSTOMER_NOT_FOUND`；422 `AMOUNT_RANGE_INVALID`（min>max 或负数）。

## PUT /opportunities/{id}

编辑商机（FR-010）。

**Request**: 同 POST，另含 `version`；**错误**: 409 `VERSION_CONFLICT` / 404。

## DELETE /opportunities/{id}

逻辑删除商机（级联逻辑删除下属销售机会）。**Response**: 200 `{ "success": true }`。
