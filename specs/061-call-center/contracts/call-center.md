# 契约：通话记录 /api/v1/call-records

**Base**: `/api/v1/call-records`（ADMIN + SALES + SUPPORT）

## GET /call-records

通话记录列表。**Query**: `keyword`（客户名）、`direction`、`customerId`、`from`、`to`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "customerId": 3, "customerName": "客户A", "contactId": 5, "contactName": "张三",
      "direction": "OUTBOUND", "durationSeconds": 300, "result": "CONNECTED",
      "remark": "确认续约意向", "recordedBy": 1, "recordedAt": "2026-08-25T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /call-records

录入通话记录（CTI 自动创建也走此端点）。**Body**

```json
{ "customerId": 3, "contactId": 5, "direction": "OUTBOUND", "durationSeconds": 300,
  "result": "CONNECTED", "remark": "确认续约意向" }
```

**Response 201**: 记录结构。**校验**: direction/result 枚举（422 CALL_FIELD_INVALID）；联系人归属客户（422 CALL_CONTACT_MISMATCH）。

## GET /call-records/{id} / PUT /{id} / DELETE /{id}

详情 / 编辑 / 删除。

## GET /call-records/stats

通话统计。**Query**: `from`、`to`、`direction`（可选）。

**Response 200**

```json
{ "totalCount": 10, "totalDurationSeconds": 5400, "avgDurationSeconds": 540,
  "byDirection": [ { "direction": "OUTBOUND", "count": 6 }, { "direction": "INBOUND", "count": 4 } ] }
```

## 错误码

| code | status | 含义 |
|---|---|---|
| CALL_FIELD_INVALID | 422 | 方向/结果枚举不合法 |
| CALL_CONTACT_MISMATCH | 422 | 联系人不属于所选客户 |
| CALL_RECORD_NOT_FOUND | 404 | 通话记录不存在 |
