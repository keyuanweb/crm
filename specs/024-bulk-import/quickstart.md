# 快速开始：批量导入

## 后端

1. `LeadExcelService`：线索导入（POI 解析 + 校验 + 逐行导入，created_by=当前用户）+ 模板生成。
2. `ContactExcelService`：联系人导入（客户名称匹配）+ 模板生成。
3. `LeadController` / `ContactController`：导入/模板端点。
4. 测试：`LeadExcelServiceTest` + `ContactExcelServiceTest` + `BulkImportIT`。

## 前端

1. `types/importResult.ts` + `leadService/contactService` 导入/模板方法。
2. `LeadListPage` / `ContactListPage` toolbar 加"导入/下载模板"按钮 + 结果反馈。

## 验证

- 后端：`mvn test`（新增测试，不影响既有 230）。
- 前端：`pnpm run typecheck` + `lint` + `test`。
- 手动：下载模板 → 填写 → 上传 → 结果反馈（成功/失败行号）。
