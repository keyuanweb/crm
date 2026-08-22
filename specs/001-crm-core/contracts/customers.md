# 契约：客户 /customers

**Base**: `/api/v1/customers`（权限：CRUD 全员；导入/导出仅 ADMIN，见 README 矩阵）

## GET /customers

分页查询客户列表，支持关键字搜索与筛选（FR-001）。

**Query**: `page`, `pageSize`, `keyword`（对 name/company/contact_person/phone 模糊匹配）, `status`（ACTIVE/INACTIVE，可选）

**Response 200**（分页信封）

```json
{
  "items": [
    { "id": 1, "name": "张三", "company": "XX 科技", "contactPerson": "张三",
      "phone": "138****0000", "email": "z***@example.com", "address": "北京",
      "remark": "", "status": "ACTIVE", "createdAt": "2026-08-21T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

> **FR-016 脱敏约定**：列表/搜索响应中 `phone`、`email` 必须脱敏（如 138****0000、z***@example.com）；
> 详情接口（GET /customers/{id}）返回完整值。脱敏由后端统一执行，前端仅展示。
> **FR-017 审计约定**：客户创建/编辑/删除、导入/导出等关键操作写入 `audit_log`（操作人/动作/对象/时间，不含敏感明文），详见 data-model.md。

## GET /customers/{id}

客户详情：基本信息 + 关联商机列表 + 跟进记录时间线（FR-002）。

**Response 200**

```json
{
  "id": 1, "name": "张三", "company": "XX 科技", "contactPerson": "张三",
  "phone": "13800000000", "email": "z@example.com", "address": "北京",
  "remark": "", "status": "ACTIVE", "createdAt": "2026-08-21T10:00:00",
  "opportunities": [ { "id": 10, "name": "年度合作", "status": "ACTIVE" } ],
  "followUps": [ { "id": 100, "method": "PHONE", "content": "沟通续约", "followUpAt": "2026-08-21T11:00:00" } ]
}
```

**错误**: 404 `CUSTOMER_NOT_FOUND`。

## POST /customers

创建客户（FR-003）。

**Request**

```json
{
  "name": "张三", "company": "XX 科技", "contactPerson": "张三",
  "phone": "13800000000", "email": "z@example.com", "address": "北京", "remark": ""
}
```

**Response**: 201，返回创建的客户对象（含 id）。

**校验**（data-model §2）: name/company 必填；phone/email 格式；name+company 同范围唯一 → 409 `CUSTOMER_DUPLICATE`。

## PUT /customers/{id}

编辑客户（FR-004）。

**Request**: 同 POST /customers，另含 `version`（乐观锁）。

**Response**: 200，返回更新后对象；**错误**: 409 `VERSION_CONFLICT` / 404。

## DELETE /customers/{id}

逻辑删除客户（FR-005）。

**Request**: `{ "version": <int> }`（可选，建议携带）。

**Response**: 200 `{ "success": true }`；删除后不出现在任何列表/搜索/统计（SC-005）。

## POST /customers/import

Excel 批量导入（FR-006，仅 ADMIN）。

**Request**: `multipart/form-data`，字段 `file`（.xlsx，模板见下）。

**Response 200**

```json
{
  "successCount": 50, "failureCount": 2,
  "failures": [ { "row": 7, "message": "公司不能为空" } ]
}
```

## GET /customers/export

按当前筛选条件导出 Excel（FR-006，仅 ADMIN）。

**Query**: 同 GET /customers 的筛选参数。

**Response**: 200 `application/octet-stream`，`Content-Disposition: attachment; filename=customers-<yyyyMMdd>.xlsx`。

## GET /customers/import-template

下载导入模板（表头与必填/格式提示）。

**Response**: 200 `.xlsx` 文件流。
