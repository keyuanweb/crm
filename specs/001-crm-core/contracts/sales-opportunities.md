# 契约：销售机会（子实体） /sales-opportunities

**Base**: `/api/v1/sales-opportunities`（权限：ADMIN + SALES，见 README 矩阵）

## GET /sales-opportunities

销售机会列表查询，按阶段筛选、分页（FR-012）。

**Query**: `page`, `pageSize`, `stage`（INITIAL_CONTACT / NEGOTIATING / CLOSED_WON / CLOSED_LOST，可选）, `opportunityId`, `customerId`

**Response 200**（分页信封）

```json
{
  "items": [
    { "id": 20, "opportunityId": 10, "opportunityName": "年度合作", "customerName": "张三",
      "amount": 300000, "stage": "NEGOTIATING", "expectedCloseDate": "2026-09-30",
      "closeResult": null, "closedAt": null, "createdAt": "2026-08-21T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /sales-opportunities/{id}

销售机会详情（FR-013）：所属商机、金额、阶段、预计成交、关闭结果。

**Response 200**

```json
{
  "id": 20, "opportunityId": 10, "opportunityName": "年度合作", "customerName": "张三",
  "amount": 300000, "stage": "NEGOTIATING", "expectedCloseDate": "2026-09-30",
  "closeResult": null, "closedAt": null
}
```

**错误**: 404 `SALES_OPPORTUNITY_NOT_FOUND`。

## POST /sales-opportunities

创建销售机会（FR-012/FR-009）：必须归属于一个未删除的商机。

**Request**

```json
{
  "opportunityId": 10, "amount": 300000,
  "stage": "INITIAL_CONTACT", "expectedCloseDate": "2026-09-30"
}
```

**Response**: 201；**错误**: 404 `OPPORTUNITY_NOT_FOUND`；422 `AMOUNT_INVALID` / `STAGE_INVALID`。

## PUT /sales-opportunities/{id}

编辑销售机会（阶段流转，未关闭前可修改，FR-010）。

**Request**: 同 POST，另含 `version`。

**规则**: 终态（CLOSED_WON/CLOSED_LOST）不可编辑 → 422 `ALREADY_CLOSED`；仅允许相邻阶段流转（data-model 状态机）。

**Response**: 200；**错误**: 409 `VERSION_CONFLICT` / 404 / 422。

## POST /sales-opportunities/{id}/close

关闭销售机会（FR-014）：标记赢单或输单，关闭后移出活动管道。

**Request**

```json
{ "closeResult": "WON", "version": 3 }
```

**Response 200**

```json
{ "id": 20, "stage": "CLOSED_WON", "closeResult": "WON", "closedAt": "2026-08-21T12:00:00" }
```

**错误**: 422 `CLOSE_RESULT_REQUIRED` / `ALREADY_CLOSED`；409 `VERSION_CONFLICT`。
