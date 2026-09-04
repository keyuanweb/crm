# API Contract: 数据保留策略（Data Retention Policy）

**创建日期**: 2026-08-27

## 基础信息

- **Base URL**: `/api/v1/data-retention`
- **认证**: JWT Bearer Token（管理员权限）
- **内容类型**: `application/json`
- **分页**: 列表端点支持 `page`、`size`、`sort` 参数

## 端点列表

### 1. 创建数据保留策略

**POST** `/api/v1/data-retention/policies`

**请求体**:
```json
{
  "entityType": "CUSTOMER",
  "retentionPeriodYears": 5,
  "retentionPeriodMonths": 0,
  "retentionPeriodDays": 0,
  "archiveAction": "ARCHIVE"
}
```

**响应 201**:
```json
{
  "id": 1,
  "entityType": "CUSTOMER",
  "retentionPeriodYears": 5,
  "retentionPeriodMonths": 0,
  "retentionPeriodDays": 0,
  "archiveAction": "ARCHIVE",
  "status": "ACTIVE",
  "createdBy": {
    "id": 1,
    "name": "admin"
  },
  "createdAt": "2026-08-27T10:00:00Z"
}
```

**错误响应**:
- `400`: 参数校验失败（保留期限为 0、归档方式不合法）
- `409`: 同一实体类型已存在活跃策略

---

### 2. 获取数据保留策略列表

**GET** `/api/v1/data-retention/policies`

**查询参数**:
- `status`: 状态 ACTIVE/SUSPENDED（可选）
- `entityType`: 实体类型（可选）
- `page`: 页码（默认 0）
- `size`: 每页大小（默认 20，最大 100）
- `sort`: 排序字段（默认 createdAt）

**响应 200**:
```json
{
  "content": [
    {
      "id": 1,
      "entityType": "CUSTOMER",
      "retentionPeriodYears": 5,
      "retentionPeriodMonths": 0,
      "retentionPeriodDays": 0,
      "archiveAction": "ARCHIVE",
      "status": "ACTIVE",
      "createdBy": {
        "id": 1,
        "name": "admin"
      },
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

### 3. 获取数据保留策略详情

**GET** `/api/v1/data-retention/policies/{id}`

**响应 200**: 同创建响应结构

**错误响应**:
- `404`: 策略不存在

---

### 4. 更新数据保留策略

**PUT** `/api/v1/data-retention/policies/{id}`

**请求体**:
```json
{
  "retentionPeriodYears": 3,
  "archiveAction": "ARCHIVE"
}
```

**响应 200**: 更新后的策略

**错误响应**:
- `400`: 参数校验失败
- `404`: 策略不存在

---

### 5. 暂停/恢复数据保留策略

**PUT** `/api/v1/data-retention/policies/{id}/status`

**请求体**:
```json
{
  "status": "SUSPENDED"
}
```

**响应 200**: 更新后的策略

**错误响应**:
- `400`: 状态值不合法（ACTIVE/SUSPENDED）
- `404`: 策略不存在

---

### 6. 删除数据保留策略

**DELETE** `/api/v1/data-retention/policies/{id}`

**响应 204**: 删除成功

**错误响应**:
- `404`: 策略不存在

---

### 7. 手动执行归档

**POST** `/api/v1/data-retention/policies/{id}/execute-now`

**响应 202**:
```json
{
  "executionId": 1,
  "message": "归档任务已加入执行队列"
}
```

**错误响应**:
- `404`: 策略不存在
- `409`: 策略正在执行中

---

### 8. 获取执行历史

**GET** `/api/v1/data-retention/policies/{id}/executions`

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
      "policyId": 1,
      "executedAt": "2026-09-01T02:00:00Z",
      "status": "SUCCESS",
      "recordsProcessed": 150,
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

### 9. 合规导出

**POST** `/api/v1/data-retention/export`

**请求体**:
```json
{
  "entityType": "CUSTOMER",
  "periodStart": "2024-01-01",
  "periodEnd": "2024-12-31",
  "format": "CSV"
}
```

**响应 202**:
```json
{
  "exportId": "export-123",
  "message": "导出任务已创建，文件生成后可下载"
}
```

**错误响应**:
- `400`: 参数校验失败（期间不合法、格式不合法）

---

### 10. 获取导出文件

**GET** `/api/v1/data-retention/export/{exportId}/download`

**响应 200**: 文件下载（`Content-Disposition: attachment`）

**错误响应**:
- `404`: 导出文件不存在或已过期

---

### 11. 获取归档日志

**GET** `/api/v1/data-retention/archive-logs`

**查询参数**:
- `policyId`: 策略 ID（可选）
- `sourceTable`: 源表名（可选）
- `page`: 页码（默认 0）
- `size`: 每页大小（默认 20，最大 100）
- `sort`: 排序字段（默认 archivedAt）

**响应 200**:
```json
{
  "content": [
    {
      "id": 1,
      "policyId": 1,
      "sourceTable": "customer",
      "sourceRecordId": 123,
      "archiveTable": "customer_archive",
      "archivedAt": "2026-09-01T02:00:00Z",
      "archivedBy": {
        "id": 1,
        "name": "admin"
      }
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "size": 20,
  "number": 0
}
```
