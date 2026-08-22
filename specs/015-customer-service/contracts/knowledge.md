# 契约：客户服务 - 知识库 /knowledge

**Base**: `/api/v1/knowledge`（写：ADMIN + SUPPORT；读：全员，仅已发布）

## GET /knowledge

文章分页列表（FR-C06/C07）。查询方为客服/管理员时可选 `includeDraft=true` 查看草稿；普通查询仅返回 PUBLISHED。

**Query**: `keyword`（标题/内容/关键词）、`category`、`status`（仅管理）、`includeDraft`、`page`、`pageSize`。

**Response 200**

```json
{
  "items": [
    { "id": 1, "category": "FAULT_TROUBLESHOOTING", "title": "如何重置密码",
      "content": "...", "keywords": "密码,重置", "status": "PUBLISHED",
      "authorId": 5, "authorName": "客服小张", "version": 0, "createdAt": "2026-08-20T10:00:00" }
  ],
  "total": 1, "page": 1, "pageSize": 20
}
```

## POST /knowledge

创建文章（FR-C06，默认 DRAFT）。

**Body**:

```json
{ "category": "FAULT_TROUBLESHOOTING", "title": "如何重置密码",
  "content": "步骤...", "keywords": "密码,重置" }
```

**Response 201**: 文章结构。

## PUT /knowledge/{id}

编辑文章（含 version）。

## POST /knowledge/{id}/publish

发布（DRAFT→PUBLISHED）。

## POST /knowledge/{id}/unpublish

下线（PUBLISHED→DRAFT）。

## DELETE /knowledge/{id}

逻辑删除。

## 错误码

| code | status | 含义 |
|---|---|---|
| ARTICLE_NOT_FOUND | 404 | 文章不存在 |
| ARTICLE_INVALID_STATE | 409 | 状态切换非法 |
| ARTICLE_FORBIDDEN | 403 | 无写权限 |
