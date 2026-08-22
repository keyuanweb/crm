# 契约：联系人管理 /contacts

**Base**: `/api/v1/contacts`（写：登录用户；查看：登录用户）

## GET /contacts

联系人分页列表（FR-C01）。

**Query**: `keyword`（姓名/电话/邮箱）、`customerId`、`role`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "customerId": 1, "customerName": "Acme 科技",
      "name": "张三", "title": "采购总监", "phone": "13800000000",
      "email": "zhangsan@example.com", "role": "DECISION_MAKER",
      "remark": null, "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /contacts/{id}

联系人详情（含所属客户名称）。

## POST /customers/{customerId}/contacts

在指定客户下创建联系人（FR-C03）。**Body**: `{ "name": "张三", "title": "...", "phone": "...", "email": "...", "role": "DECISION_MAKER", "remark": "..." }`。

**Response 201**: 联系人结构。同客户（姓名+电话）重复 → 409 CONTACT_DUPLICATE。

## PUT /contacts/{id}

编辑联系人（FR-C04，含 version）。

## DELETE /contacts/{id}

逻辑删除（FR-C05）。

## 错误码

| code | status | 含义 |
|---|---|---|
| CONTACT_NOT_FOUND | 404 | 联系人不存在 |
| CONTACT_DUPLICATE | 409 | 同客户下相同姓名+电话已存在 |
| CUSTOMER_NOT_FOUND | 404 | 关联客户不存在 |
