# 契约：产品 /products

**Base**: `/api/v1/products`（查看：ADMIN + SALES + SUPPORT；写操作：仅 ADMIN）

## GET /products

产品分页列表（FR-P01）。

**Query**: `keyword`（名称/编码）、`status`（ACTIVE/INACTIVE）、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "code": "CRM-STD", "name": "CRM 标准版", "spec": "10 用户", "unit": "套",
      "standardPrice": 980000, "status": "ACTIVE", "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /products（ADMIN）

**Body**: `{ "code": "CRM-STD", "name": "CRM 标准版", "spec": "10 用户", "unit": "套", "standardPrice": 980000, "status": "ACTIVE" }`

**Response 201**: 同 GET item 结构。

**校验**: code/name 必填；code 唯一（409 PRODUCT_DUPLICATE）；standardPrice ≥ 0。

## PUT /products/{id}（ADMIN）

**Body**: 同 POST（含 `version`）。

**Response 200**: 更新后结构。

**错误**: 404 PRODUCT_NOT_FOUND / 409 VERSION_CONFLICT。

## DELETE /products/{id}（ADMIN）

**Response 200**（逻辑删除）。

---

# 契约：报价单 /quotes

**Base**: `/api/v1/quotes`（查看/创建/编辑：ADMIN + SALES；审批：仅 ADMIN）

## GET /quotes

报价单分页列表（FR-P05 衍生）。

**Query**: `keyword`（报价单号/客户名）、`status`、`customerId`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "quoteNo": "Q-20260822-0001", "customerId": 2, "customerName": "Acme 科技",
      "opportunityId": null, "validUntil": "2026-09-22", "status": "DRAFT",
      "totalAmount": 1470000, "remark": null, "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /quotes/{id}

报价单详情：header + 行明细（FR-P05 衍生）。

**Response 200**

```json
{
  "id": 1, "quoteNo": "Q-20260822-0001", "customerId": 2, "customerName": "Acme 科技",
  "opportunityId": null, "validUntil": "2026-09-22", "status": "DRAFT",
  "totalAmount": 1470000, "remark": null,
  "approverId": null, "approvedAt": null, "rejectReason": null,
  "items": [
    { "id": 10, "productId": 1, "productName": "CRM 标准版", "unitPrice": 980000,
      "quantity": 2, "discount": 0.75, "lineTotal": 1470000 }
  ],
  "version": 0, "createdAt": "2026-08-22T10:00:00"
}
```

## POST /quotes

创建报价单（草稿，FR-P05/P06）。

**Body**:

```json
{
  "customerId": 2, "opportunityId": null, "validUntil": "2026-09-22", "remark": null,
  "items": [
    { "productId": 1, "quantity": 2, "discount": 0.75 }
  ]
}
```

**Response 201**: 详情结构（行内回填产品名/单价快照与 lineTotal；总额重算）。

**校验**: customerId 必填且客户存在（404 CUSTOMER_NOT_FOUND）；opportunityId 若填须存在且属于该客户（400 OPPORTUNITY_CUSTOMER_MISMATCH）；items 非空；quantity ≥ 1；discount ∈ [0,1]（实付比例，1 全价）。

## PUT /quotes/{id}

编辑草稿或被拒报价单（FR-P08）。

**Body**: 同 POST（含 `version`）。

**Response 200**: 详情结构。

**错误**: 404 QUOTE_NOT_FOUND / 409 QUOTE_INVALID_STATE（非 DRAFT/REJECTED）/ 409 VERSION_CONFLICT。

## POST /quotes/{id}/submit

提交审批（FR-P07）。

**Body**: 无（或空 JSON）。

**Response 200**: 详情结构（status=PENDING_APPROVAL）。

**错误**: 404 / 409 QUOTE_INVALID_STATE（非 DRAFT/REJECTED）。

## POST /quotes/{id}/approve

审批通过（FR-P10，ADMIN）。

**Body**: `{}` 或空。

**Response 200**: 详情结构（status=APPROVED，approverId/approvedAt 记录）。

**错误**: 404 / 403（非 ADMIN）/ 409 QUOTE_INVALID_STATE（非 PENDING_APPROVAL）。

## POST /quotes/{id}/reject

审批拒绝（FR-P10，ADMIN）。

**Body**: `{ "reason": "价格过高，建议下调 10%" }`（reason 必填）。

**Response 200**: 详情结构（status=REJECTED，rejectReason 记录）。

**错误**: 404 / 403 / 400（reason 为空）/ 409 QUOTE_INVALID_STATE。

## GET /quotes/{id}/pdf

导出报价单 PDF（FR-P11）。

**Response 200**: `application/pdf`，Content-Disposition 附件 `quote-{quoteNo}.pdf`。

**错误**: 404 / 500（生成失败）。

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| PRODUCT_DUPLICATE | 409 | 产品编码重复 |
| PRODUCT_NOT_FOUND | 404 | 产品不存在 |
| QUOTE_NOT_FOUND | 404 | 报价单不存在 |
| QUOTE_INVALID_STATE | 409 | 状态机非法流转 |
| OPPORTUNITY_CUSTOMER_MISMATCH | 400 | 商机与客户不匹配 |
| VERSION_CONFLICT | 409 | 乐观锁冲突 |

## 权限矩阵

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /products | ✅ | ✅ | ✅ |
| POST/PUT/DELETE /products | ✅ | ❌ | ❌ |
| GET /quotes、GET /quotes/{id} | ✅ | ✅ | ❌ |
| POST/PUT /quotes、POST /quotes/{id}/submit | ✅ | ✅ | ❌ |
| POST /quotes/{id}/approve、/reject | ✅ | ❌ | ❌ |
| GET /quotes/{id}/pdf | ✅ | ✅ | ❌ |
