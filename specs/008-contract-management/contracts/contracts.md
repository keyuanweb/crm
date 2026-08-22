# 契约：合同 /contracts

**Base**: `/api/v1/contracts`（查看/创建/编辑：ADMIN + SALES；审批：仅 ADMIN）

## GET /contracts

合同分页列表（FR-CT01）。

**Query**: `keyword`（编号/标题）、`status`、`customerId`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "contractNo": "HT-20260822-0001", "title": "CRM 采购合同", "customerId": 2,
      "customerName": "Acme 科技", "quoteId": 5, "amount": 1470000,
      "startDate": "2026-09-01", "endDate": "2027-08-31", "status": "DRAFT",
      "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /contracts/{id}

合同详情（含正文、审批信息、附件列表）。

**Response 200**

```json
{
  "id": 1, "contractNo": "HT-20260822-0001", "title": "CRM 采购合同",
  "customerId": 2, "customerName": "Acme 科技", "quoteId": 5,
  "amount": 1470000, "startDate": "2026-09-01", "endDate": "2027-08-31",
  "content": "甲方：Acme 科技 ...", "status": "PENDING_APPROVAL",
  "approverId": null, "approvedAt": null, "rejectReason": null,
  "effectiveAt": null, "terminatedReason": null, "remark": null,
  "attachments": [ { "id": 10, "fileName": "扫描件.pdf", "fileSize": 204800, "uploadedBy": 1, "createdAt": "2026-08-22T11:00:00" } ],
  "version": 0, "createdAt": "2026-08-22T10:00:00"
}
```

## POST /contracts

创建合同（FR-CT02）。基于报价（可选）：`quoteId` 存在且 APPROVED、客户一致，自动带入金额。

**Body**:

```json
{
  "title": "CRM 采购合同", "customerId": 2, "quoteId": 5,
  "amount": 1470000, "startDate": "2026-09-01", "endDate": "2027-08-31",
  "templateId": 1, "content": null, "remark": null
}
```

**Response 201**: 详情结构（编号自动生成；templateId 提供时正文=模板替换占位符）。

**校验**: title 必填；customerId 必填且客户存在；quoteId 若填须存在、状态 APPROVED、客户一致（否则 409 CONTRACT_INVALID_STATE / 400 QUOTE_NOT_APPROVED）；startDate ≤ endDate；amount ≥0。

## PUT /contracts/{id}

编辑草稿/被拒合同（FR-CT03）。

**Body**: 同 POST（含 `version`）。

**Response 200**。

**错误**: 404 CONTRACT_NOT_FOUND / 409 CONTRACT_INVALID_STATE（非 DRAFT/REJECTED）/ 409 VERSION_CONFLICT。

## POST /contracts/{id}/submit

提交审批（FR-CT04）。

**Response 200**: status=PENDING_APPROVAL。

## POST /contracts/{id}/approve（ADMIN）

审批通过（FR-CT05）。

**Response 200**: status=APPROVED，approverId/approvedAt 记录。

## POST /contracts/{id}/reject（ADMIN）

审批拒绝（FR-CT05）。

**Body**: `{ "reason": "条款需修改" }`（必填）。

**Response 200**: status=REJECTED，rejectReason 记录。

## POST /contracts/{id}/effective

标记生效（FR-CT06）。

**Response 200**: status=EFFECTIVE，effectiveAt 记录。

## POST /contracts/{id}/complete

标记完成（FR-CT06）。

**Response 200**: status=COMPLETED。

## POST /contracts/{id}/terminate

标记终止（FR-CT06）。

**Body**: `{ "reason": "客户违约" }`（必填）。

**Response 200**: status=TERMINATED，terminatedReason 记录。

---

# 契约：合同附件 /contracts/{id}/attachments

**Base**: `/api/v1/contracts`（ADMIN + SALES）

## POST /contracts/{id}/attachments

上传附件（FR-CT07，multipart）。

**Form**: `file`（≤20MB，白名单 pdf/jpg/png/doc/docx/xlsx）。

**Response 201**: 附件响应结构。

**错误**: 400（类型不允许/超限）/ 404 CONTRACT_NOT_FOUND。

## GET /contracts/{id}/attachments/{attachmentId}/download

下载附件（FR-CT07）。

**Response 200**: 文件流 + Content-Disposition。

## DELETE /contracts/{id}/attachments/{attachmentId}

删除附件（物理删除文件+记录，FR-CT07）。

**Response 200**。

---

# 契约：合同模板 /contract-templates

**Base**: `/api/v1/contract-templates`（查看：ADMIN+SALES；写：仅 ADMIN）

## GET /contract-templates

模板分页列表（FR-CT08）。

**Query**: `keyword`、`status`、`page`、`pageSize`。

## POST /contract-templates（ADMIN）

**Body**: `{ "name": "标准采购合同", "content": "甲方：{customerName}，合同号 {contractNo}，金额 {amount} 元" }`

**Response 201**。

## PUT /contract-templates/{id}（ADMIN）

编辑模板（含 version）。

## DELETE /contract-templates/{id}（ADMIN）

停用模板（逻辑删除）。

## 错误码汇总

| code | status | 含义 |
|---|---|---|
| CONTRACT_NOT_FOUND | 404 | 合同不存在 |
| CONTRACT_INVALID_STATE | 409 | 状态机非法流转 |
| QUOTE_NOT_APPROVED | 400 | 关联报价未通过审批 |
| ATTACHMENT_NOT_FOUND | 404 | 附件不存在 |
| ATTACHMENT_INVALID | 400 | 附件类型/大小不合法 |
| TEMPLATE_NOT_FOUND | 404 | 模板不存在 |
| VERSION_CONFLICT | 409 | 乐观锁冲突 |

## 权限矩阵

| 端点 | ADMIN | SALES | SUPPORT |
|---|---|---|---|
| GET /contracts、GET /contracts/{id} | ✅ | ✅ | ❌ |
| POST/PUT /contracts、submit/effective/complete/terminate | ✅ | ✅ | ❌ |
| POST /contracts/{id}/approve、/reject | ✅ | ❌ | ❌ |
| 附件 上传/下载/删除 | ✅ | ✅ | ❌ |
| GET /contract-templates | ✅ | ✅ | ❌ |
| POST/PUT/DELETE /contract-templates | ✅ | ❌ | ❌ |
