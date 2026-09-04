# API Contract: 定时导出订阅（Scheduled Export Subscription）

**创建日期**: 2026-08-27

## 基础信息

- **Base URL**: `/api/v1/scheduled-exports`
- **认证**: JWT Bearer Token
- **内容类型**: `application/json`
- **分页**: 列表端点支持 `page`、`size`、`sort` 参数

## 端点列表

### 1. 创建定时导出任务

**POST** `/api/v1/scheduled-exports`

**请求体**:
```json
{
  "entityType": "CUSTOMER",
  "filterConditions": {
    "status": "ACTIVE",
    "createdAtFrom": "2026-01-01",
    "createdAtTo": "2026-12-31"
  },
  "exportFormat": "XLSX",
  "executionCron": "0 0 9 * * MON"
}
```

**响应 201**:
```json
{
  "id": 1,
  "createdBy": {
    "id": 1,
    "name": "admin"
  },
  "entityType": "CUSTOMER",
  "filterConditions": {
    "status": "ACTIVE",
    "createdAtFrom": "2026-01-01",
    "createdAtTo": "2026-12-31"
  },
  "exportFormat": "XLSX",
  "executionCron": "0 0 9 * * MON",
  "nextExecutionTime": "2026-09-01T09:00:00Z",
  "status": "ACTIVE",
  "createdAt": "2026-08-27T10:00:00Z"
}
```

**错误响应**:
- `400`: 参数校验失败（Cron 表达式不合法、筛选条件字段不存在）
- `409`: 用户活跃任务数已达上限（10 个）

---

### 2. 获取定时导出任务列表

**GET** `/api/v1/scheduled-exports`

**查询参数**:
- `status`: 状态 ACTIVE/SUSPENDED/DELETED（可选）
- `entityType`: 实体类型（可选）
- `page`: 页码（默认 0）
- `size`: 每页大小（默认 20，最大 100）
- `sort`: 排序字段（默认 nextExecutionTime）

**响应 200**:
```json
{
  "content": [
    {
      "id": 1,
      "createdBy": {
        "id": 1,
        "name": "admin"
      },
      "entityType": "CUSTOMER",
      "exportFormat": "XLSX",
      "executionCron": "0 0 9 * * MON",
      "nextExecutionTime": "2026-09-01T09:00:00Z",
      "status": "ACTIVE",
      "createdAt": "2026-08-27T10:00:00Z"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### 3. 获取定时导出任务详情

**GET** `/api/v1/scheduled-exports/{id}`

**响应 200**: 同创建响应结构

**错误响应**:
- `404`: 任务不存在

---

### 4. 暂停/恢复定时导出任务

**PUT** `/api/v1/scheduled-exports/{id}/status`

**请求体**:
```json
{
  "status": "SUSPENDED"
}
```

**响应 200**: 更新后的任务

**错误响应**:
- `400`: 状态值不合法（ACTIVE/SUSPENDED/DELETED）
- `404`: 任务不存在

---

### 5. 删除定时导出任务

**DELETE** `/api/v1/scheduled-exports/{id}`

**响应 204**: 删除成功

**错误响应**:
- `404`: 任务不存在

---

### 6. 手动立即执行

**POST** `/api/v1/scheduled-exports/{id}/execute-now`

**响应 202**:
```json
{
  "executionId": 1,
  "message": "任务已加入执行队列"
}
```

**错误响应**:
- `404`: 任务不存在
- `409`: 任务正在执行中

---

### 7. 获取执行历史

**GET** `/api/v1/scheduled-exports/{id}/executions`

**查询参数**:
- `page`: 页码（默认 0）
- `size`: 每页大小（默认 20，最大 100）
- `sort`: 排序字段（默认 executedAt）

**响应 200**:
```json
{
  "content": [
    {
      "id": 1,
      "exportId": 1,
      "executedAt": "2026-09-01T09:00:00Z",
      "status": "EMAIL_SENT",
      "filePath": "/tmp/exports/customer_export_20260901.xlsx",
      "fileSizeBytes": 102400,
      "emailSent": true,
      "emailSentAt": "2026-09-01T09:01:00Z",
      "errorMessage": null
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```

---

### 8. 获取执行历史详情

**GET** `/api/v1/scheduled-exports/{id}/executions/{executionId}`

**响应 200**: 同执行历史列表项结构

**错误响应**:
- `404`: 执行记录不存在
