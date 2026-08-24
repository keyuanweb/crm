# 契约：批量导入 bulk-import

**Base**: `/api/v1/leads` 与 `/api/v1/contacts`（登录用户可导入，归属本人）

## 线索导入

### GET /leads/import-template

下载线索导入模板（xlsx）。

**Response 200**: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`，文件名 `lead-import-template.xlsx`。

### POST /leads/import（multipart）

上传线索 xlsx 批量导入。

**Form**: `file`（xlsx）。

**Response 200**

```json
{
  "success": true,
  "data": { "successCount": 2, "failureCount": 1,
            "failures": [ { "row": 3, "message": "公司不能为空" } ] },
  "error": null
}
```

**校验**: 非 xlsx / 空文件 → 400。

### GET /leads/import-failures

下载导入失败明细（若需要离线修正；本期可返回最近一次失败列表或省略，由前端展示页面结果）。

## 联系人导入

### GET /contacts/import-template

下载联系人导入模板（xlsx）。

### POST /contacts/import（multipart）

上传联系人 xlsx 批量导入（按客户名称匹配）。

**校验**: 姓名/客户名称必填；客户不存在 → 该行失败。

## 备注

- 金额/来源枚举校验同创建接口。
- 导入归属当前用户（created_by）。
- 审计：导入成功后记录"导入线索/联系人 N 条"。
