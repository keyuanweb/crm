# 契约：自定义报表 custom-reports

**Base**: `/api/v1/reports`（仅 ADMIN；或 ADMIN + 本人数据 SALES）

## POST /reports/query

按维度/指标/时间范围聚合查询。

**Body**:

```json
{
  "dimension": "SALES",
  "metric": "AMOUNT",
  "granularity": "MONTH",
  "startDate": "2026-08-01",
  "endDate": "2026-08-31",
  "stageFilter": "CLOSED_WON"
}
```

- `dimension`: SALES / PRODUCT / SOURCE / STAGE / TIME（必填）
- `metric`: COUNT / AMOUNT（必填）
- `granularity`: DAY / MONTH（dimension=TIME 时必填）
- `startDate` / `endDate`: 时间范围（默认近 30 天；end < start → 422）
- `stageFilter`: 可选（维度=SALES/PRODUCT/TIME 时按阶段过滤）

**Response 200**

```json
{
  "success": true,
  "data": {
    "rows": [ { "dimensionValue": "张三", "count": 12, "amount": 8000000, "ratio": 0.8 } ],
    "totalCount": 15,
    "totalAmount": 10000000,
    "dimension": "SALES", "metric": "AMOUNT", "granularity": "MONTH"
  },
  "error": null
}
```

**校验**: 非法维度/指标组合 → 422 REPORT_INVALID；时间颠倒 → 422。

## GET /reports/export

导出当前报表为 xlsx（Query 参数同 POST body 字段）。

**Response 200**: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，Content-Disposition 附件。

## 模板（P3，仅 ADMIN）

- `GET /reports/templates`：模板列表。
- `POST /reports/templates`：保存（name/dimension/metric/granularity/startDate/endDate）。
- `DELETE /reports/templates/{id}`：删除。
- `GET /reports/templates/{id}/run`：按模板执行查询。

## 备注

- 金额单位为分。
- 数据权限：SALES 仅聚合本人（created_by=当前用户）数据；ADMIN 全量。
