# 契约：权限体系加固（无新端点）

本模块不新增 API 端点，行为加固如下（响应语义）：

## 1. 行级数据权限（非 ADMIN）

- `GET /api/v1/leads`、`GET /leads/{id}`、`PUT /leads/{id}`、`DELETE /leads/{id}`、`POST /leads/{id}/assign`、`POST /leads/{id}/convert`：
  - 非 ADMIN：仅 owner ∈ 可见集（本人 + 共享）的数据可见/可操作；越权 → `403 FORBIDDEN`。
  - ADMIN：全量豁免。
- `GET /api/v1/contacts`、`GET /contacts/{id}`、`PUT /contacts/{id}`、`DELETE /contacts/{id}`：按所属客户可见性过滤，越权 → 403。
- `GET/POST/DELETE /api/v1/follow-ups`、评论接口：按关联实体可见性过滤，越权 → 403。

## 2. 导出（POST /api/v1/exports + GET /exports/{id}/download）

- 非 ADMIN：导出数据按 `resolveVisibleOwnerIds` 过滤（仅可见数据）。
- 非 ADMIN：手机/邮箱字段脱敏（`138****0000` 格式，复用 MaskingUtil）；ADMIN 明文。

## 3. 异常状态码

| 场景 | 状态码 | 响应 error.code |
|---|---|---|
| 请求体坏 JSON | 400 | BAD_REQUEST |
| 路径/查询参数类型错 | 400 | BAD_REQUEST |
| 缺必需参数 | 400 | BAD_REQUEST |
| 资源路径不存在 | 404 | NOT_FOUND |
| 唯一键冲突（重复用户名等） | 409 | DUPLICATE_KEY |
| 其余未捕获异常 | 500 | INTERNAL_ERROR（不含内部细节） |
