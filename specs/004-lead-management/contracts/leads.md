# 契约：线索管理 /leads

**Base**: `/api/v1/leads`（写：登录用户；分配：ADMIN；查看：登录用户）

## GET /leads

线索分页列表（FR-L01）。

**Query**: `keyword`（姓名/公司/职位/电话/邮箱）、`status`、`source`、`ownerId`、`poolOnly`（仅未分配 NEW/WORKING）、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "name": "张三", "company": "Acme 科技", "title": "采购经理",
      "phone": "13800000000", "email": "zhangsan@example.com", "source": "EXHIBITION",
      "status": "WORKING", "score": 80, "ownerId": 1, "ownerName": "admin",
      "convertedCustomerId": null, "convertedAt": null, "campaignId": null,
      "customFieldValues": [], "version": 0, "createdAt": "2026-08-22T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## GET /leads/{id}

线索详情（含跟进时间线、转化信息、自定义字段值）。

## POST /leads

创建线索（FR-L03）。**Response 201**: 线索结构（默认 NEW，owner 空进池）。

**Body**:

```json
{ "name": "张三", "company": "Acme 科技", "source": "EXHIBITION",
  "campaignId": null, "customFieldValues": [] }
```

## PUT /leads/{id}

编辑线索（FR-L04，含 version）。QUALIFIED/DISQUALIFIED 拒绝编辑（422）。

## POST /leads/{id}/assign

分配负责人（FR-L06，ADMIN）。**Query**: `ownerId`。

## POST /leads/{id}/claim

从线索池领取（FR-L06）。**Response 200**: 线索结构（owner=当前用户，NEW→WORKING）。

## POST /leads/{id}/convert

转化（FR-L08，单事务：客户+联系人+商机）。**Body**: `{ "opportunityName": "...", "expectedAmount": 1000000 }`。

## DELETE /leads/{id}

逻辑删除（FR-L05）；QUALIFIED 拒绝（422 LEAD_ALREADY_CONVERTED）。

## POST /leads/import / GET /leads/export / GET /leads/template

Excel 批量导入（multipart）/ 按筛选导出 / 模板下载（FR-L11）。

## 错误码

| code | status | 含义 |
|---|---|---|
| LEAD_NOT_FOUND | 404 | 线索不存在 |
| LEAD_ALREADY_CONVERTED | 422 | 已转化，不可重复操作/删除 |
| LEAD_INVALID_STATE | 422 | 状态不允许该操作 |
