# 契约：在线表单

**Base**: `/api/v1/forms`（form:manage）、`/api/v1/public/forms/{id}/submit`（公开，无需认证）

## GET /forms

表单列表：`{ "items": [ { "id":1, "name":"产品试用申请", "fields":"[...]", "successMessage":"已收到", "source":"WEBSITE", "status":"ENABLED", "submissionCount": 12 } ], "total": n }`

## POST /forms

创建表单。**Body**:
```json
{
  "name": "产品试用申请",
  "fields": [
    { "field": "name", "label": "姓名", "type": "TEXT", "required": true },
    { "field": "phone", "label": "手机", "type": "TEL", "required": true },
    { "field": "company", "label": "公司", "type": "TEXT", "required": false }
  ],
  "successMessage": "已收到您的申请，我们将尽快联系您",
  "source": "WEBSITE",
  "status": "ENABLED"
}
```

## PUT /forms/{id} / DELETE /forms/{id}

编辑/删除。

## POST /forms/{id}/toggle

启用/停用。

## GET /forms/{id}/submissions?page=&pageSize=

提交记录（payload/ip/时间/生成线索 id）。

## POST /api/v1/public/forms/{id}/submit

公开提交（匿名）。**Body**: `{ "name":"张三", "phone":"13800000000", "company":"Acme" }`

**Response 200**: `{ "success": true, "data": { "submissionId": 1, "leadId": 3, "message": "已收到您的申请…" } }`
**409**: 重复提交（email/phone 已有线索）。
**429**: 频控（同 IP 1 分钟超 3 次）。

## GET /f/{id}

公开表单提交页（前端渲染）。

## 备注

- 字段映射 lead：name/company/phone/email；其余存 payload。
- 防注入：字段值长度校验 + 白名单。
- 权限 form:manage 入 028 字典。
