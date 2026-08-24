# 契约：合同续约 /contracts/renewal-overview

**Base**: `/api/v1/contracts`（ADMIN + SALES）

## GET /contracts/renewal-overview

续约漏斗视图：按 即将到期/已到期未续/已续约 分组，含搜索与分页。

**Query**: `group`（EXPIRING_SOON / EXPIRED_UNRENEWED / RENEWED，必填）、`keyword`（合同号/标题）、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "contractNo": "HT20260801", "title": "年度服务合同", "customerId": 3,
      "customerName": "客户A", "amount": 1200000, "startDate": "2025-08-01", "endDate": "2026-08-01",
      "status": "EFFECTIVE", "renewedFromId": null, "renewedFromNo": null,
      "renewedBy": [ { "id": 5, "contractNo": "HT20260802", "title": "续约合同" } ],
      "daysToExpire": 12, "version": 0, "createdAt": "2025-08-01T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

- `daysToExpire`：距离到期天数（负数=已过期天数）。
- `renewedBy`：续约去向列表（新合同引用本合同的记录）。
- `renewedFromNo`：来源合同号（可跳转）。

## 合同 CRUD 扩展（沿用 /contracts）

- **POST /contracts** Body 增加可选 `renewedFromId`（引用旧合同 id）。
- **GET /contracts/{id}** Response 增加 `renewedFromId`/`renewedFromNo`/`renewedBy`。

## 错误码

| code | status | 含义 |
|---|---|---|
| CONTRACT_RENEWAL_GROUP_INVALID | 422 | 续约分组参数不合法 |
| CONTRACT_NOT_FOUND | 404 | 续约来源合同不存在（沿用） |
