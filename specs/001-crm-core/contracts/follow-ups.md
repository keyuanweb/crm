# 契约：跟进记录 /follow-ups

**Base**: `/api/v1/follow-ups`（权限：全员，见 README 矩阵）

## GET /follow-ups

按客户或商机查询跟进记录（时间线，FR-015）。

**Query**: `customerId`（必填其一）或 `opportunityId`, `page`, `pageSize`

**Response 200**（分页信封）

```json
{
  "items": [
    { "id": 100, "customerId": 1, "opportunityId": 10, "method": "PHONE",
      "content": "沟通续约意向", "nextFollowUpAt": "2026-08-28T10:00:00",
      "followUpBy": 2, "followUpByName": "李四", "createdAt": "2026-08-21T11:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /follow-ups

添加跟进记录（FR-015）。

**Request**

```json
{
  "customerId": 1, "opportunityId": 10,
  "method": "PHONE", "content": "沟通续约意向", "nextFollowUpAt": "2026-08-28T10:00:00"
}
```

**Response**: 201；**错误**: 404 `CUSTOMER_NOT_FOUND` / `OPPORTUNITY_NOT_FOUND`；422 `OPPORTUNITY_CUSTOMER_MISMATCH`（opportunity 与 customer 不匹配）；400 校验（method 枚举、content 必填）。

## PUT /follow-ups/{id}

编辑跟进记录（FR-015）。

**Request**: 同 POST，另含 `version`。

**规则**: 仅记录本人（follow_up_by = 当前用户）或 ADMIN 可编辑 → 403 `FORBIDDEN`。

**Response**: 200；**错误**: 409 `VERSION_CONFLICT` / 404。
