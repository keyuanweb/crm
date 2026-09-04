# API Contract: 销售配额分解（Sales Quota Decomposition）

**创建日期**: 2026-08-27

## 基础信息

- **Base URL**: `/api/v1/sales-quota`
- **认证**: JWT Bearer Token
- **内容类型**: `application/json`
- **分页**: 列表端点支持 `page`、`size`、`sort` 参数

## 端点列表

### 1. 创建配额

**POST** `/api/v1/sales-quota`

**请求体**:
```json
{
  "year": 2026,
  "quarter": null,
  "teamId": 1,
  "userId": null,
  "amount": 10000000.00,
  "periodStart": "2026-01-01",
  "periodEnd": "2026-12-31"
}
```

**响应 201**:
```json
{
  "id": 1,
  "parentId": null,
  "quarter": null,
  "year": 2026,
  "teamId": 1,
  "userId": null,
  "amount": 10000000.00,
  "status": "DRAFT",
  "periodStart": "2026-01-01",
  "periodEnd": "2026-12-31",
  "createdAt": "2026-08-27T10:00:00Z"
}
```

**错误响应**:
- `400`: 参数校验失败（amount ≤ 0、期间不合法）
- `409`: 同一用户同一期间已存在配额

---

### 2. 获取配额列表

**GET** `/api/v1/sales-quota`

**查询参数**:
- `year`: 年份（可选）
- `teamId`: 团队 ID（可选）
- `userId`: 用户 ID（可选）
- `status`: 状态 ACTIVE/DRAFT/CLOSED（可选）
- `page`: 页码（默认 0）
- `size`: 每页大小（默认 20，最大 100）
- `sort`: 排序字段（默认 createdAt）

**响应 200**:
```json
{
  "content": [
    {
      "id": 1,
      "parentId": null,
      "quarter": null,
      "year": 2026,
      "teamId": 1,
      "userId": null,
      "amount": 10000000.00,
      "status": "ACTIVE",
      "periodStart": "2026-01-01",
      "periodEnd": "2026-12-31",
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

### 3. 获取配额详情

**GET** `/api/v1/sales-quota/{id}`

**响应 200**: 同创建响应结构

**错误响应**:
- `404`: 配额不存在

---

### 4. 更新配额

**PUT** `/api/v1/sales-quota/{id}`

**请求体**:
```json
{
  "amount": 12000000.00,
  "changeReason": "Q1 业绩超预期，追加目标"
}
```

**响应 200**: 更新后的配额

**错误响应**:
- `400`: 参数校验失败
- `403`: 配额已关闭，禁止调整
- `404`: 配额不存在

---

### 5. 分解配额

**POST** `/api/v1/sales-quota/{id}/breakdown`

**请求体**:
```json
{
  "breakdowns": [
    {
      "quarter": 1,
      "teamId": 1,
      "amount": 2500000.00
    },
    {
      "quarter": 1,
      "teamId": 2,
      "amount": 2500000.00
    }
  ]
}
```

**响应 201**: 创建的分解关系列表

**错误响应**:
- `400`: 分解总和与上级配额不一致（误差 > 0.01）
- `404`: 配额不存在

---

### 6. 获取配额分解

**GET** `/api/v1/sales-quota/{id}/breakdown`

**响应 200**:
```json
[
  {
    "id": 1,
    "parentQuotaId": 1,
    "childQuotaId": 2,
    "amount": 2500000.00,
    "childQuota": {
      "id": 2,
      "quarter": 1,
      "teamId": 1,
      "amount": 2500000.00
    }
  }
]
```

---

### 7. 获取配额达成率

**GET** `/api/v1/sales-quota/{id}/achievement`

**响应 200**:
```json
{
  "quotaId": 1,
  "quotaAmount": 10000000.00,
  "actualAmount": 6000000.00,
  "achievementRate": 60.00,
  "calculatedAt": "2026-08-27T10:00:00Z",
  "status": "ON_TRACK"
}
```

**status 值**:
- `ON_TRACK`: 达成率 ≥ 80%
- `AT_RISK`: 达成率 60-80%
- `BELOW_TARGET`: 达成率 < 60%

---

### 8. 获取配额版本历史

**GET** `/api/v1/sales-quota/{id}/versions`

**响应 200**:
```json
[
  {
    "id": 1,
    "quotaId": 1,
    "oldAmount": 10000000.00,
    "newAmount": 12000000.00,
    "changedBy": {
      "id": 1,
      "name": "admin"
    },
    "changedAt": "2026-08-27T10:00:00Z",
    "changeReason": "Q1 业绩超预期，追加目标",
    "versionNumber": 1
  }
]
```

---

### 9. 导出配额报表

**GET** `/api/v1/sales-quota/export`

**查询参数**:
- `year`: 年份
- `teamId`: 团队 ID（可选）
- `format`: csv/excel（默认 csv）

**响应 200**: 文件下载（`Content-Disposition: attachment`）

---

### 10. 配额转移（销售离职）

**POST** `/api/v1/sales-quota/{id}/transfer`

**请求体**:
```json
{
  "targetUserId": 5
}
```

**响应 200**: 转移后的配额

**错误响应**:
- `400`: 目标用户已有关联配额
- `404`: 配额不存在
