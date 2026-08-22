# 契约：系统增强 - 数据导出 /exports

**Base**: `/api/v1/exports`

## POST /exports

创建导出任务（后台执行）。

**Body**:

```json
{ "exportType": "LEAD", "filter": { "status": "WORKING", "keyword": "张" } }
```

**Response 201**: 任务结构（status=PENDING）。

## GET /exports

导出任务历史（分页，本人）。

**Query**: `page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "exportType": "LEAD", "status": "DONE", "rowCount": 120,
      "fileName": "leads_20260822_100000.xlsx", "createdAt": "2026-08-22T10:00:00",
      "completedAt": "2026-08-22T10:00:03" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /exports/{id}/download

下载导出文件（仅创建人/ADMIN）。**Response 200**: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet 附件。

## 错误码

| code | status | 含义 |
|---|---|---|
| EXPORT_NOT_FOUND | 404 | 导出任务不存在 |
| EXPORT_NOT_READY | 409 | 任务未完成（PENDING/RUNNING/FAILED） |
| EXPORT_FORBIDDEN | 403 | 非创建人/ADMIN |
