# Tasks: 批量导入增强（线索/联系人导入）

**Input**: Design documents from `/specs/024-bulk-import/`



**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/bulk-import.md



**Tests**: 后端测试（JUnit 5 + Mockito）、集成测试（Spring Boot Test + MockMvc）、前端测试（Vitest + RTL）

**Note**: 线索导入复用 019 自动评分（导入时可带 score，导入后可选重算）；联系人导入按客户名称精确匹配（不区分大小写）；导入的线索/联系人归属当前用户（created_by）；失败明细下载为简单文本/Excel 文件。

## Phase 1: 后端测试 - 线索/联系人导入

- [x] T001 [P] [US2] 创建 `backend/src/test/java/com/crm/service/ContactExcelServiceTest.java` 单元测试，验证联系人导入解析/校验/成功失败明细；模板下载生成含姓名/客户名称/职位/电话/邮箱/角色/备注列的 xlsx 文件；ContactExcelService 按客户名称精确匹配（不区分大小写）关联 customerId。- [x] T002 [P] [US1] 创建 `backend/src/test/java/com/crm/integration/BulkImportIT.java` 集成测试，验证线索/联系人导入 + 模板下载 + 失败明细下载；xlsx 文件类型校验；score 字段沿用 019 自动评分（可选）；模板下载生成含姓名/公司/职位/电话/邮箱/来源/评分/备注列的 xlsx 文件。

## Phase 2: 后端联系人导入服务实现

- [x] T003 [US2] 创建 `service/ContactExcelService.java`，实现 `importContacts(InputStream)` 方法：使用 POI 解析 xlsx，逐行校验（姓名/客户名称必填），按客户名称精确匹配（不区分大小写）关联 customerId，设置 created_by=当前用户；返回 ImportResult + 失败明细；实现 `generateTemplate()` 生成 xlsx 模板（表头：姓名/客户名称/职位/电话/邮箱/角色/备注）。- [x] T004 [US2] 修改 `ContactController`，新增 `POST /contacts/import` 接口（multipart 上传）、`GET /contacts/import-template` 接口（下载模板）、`GET /contacts/import-failures` 接口（下载失败明细，复用 T003 结果）。

## Phase 3: 后端线索导入服务实现

- [x] T005 [US1] 修改 `LeadExcelService`，扩展 `importLeads(InputStream)` 方法：使用 POI 解析 xlsx，逐行校验（姓名/公司必填、来源/评分合法），调用 LeadScoreService 自动评分（可选，沿用 019 逻辑，默认 score=0）；返回 ImportResult + 失败明细；模板下载生成含姓名/公司/职位/电话/邮箱/来源/评分/备注列的 xlsx 文件。

## Phase 4: 前端实现

- [x] T006 [P] [US1] 创建 `types/importResult.ts` 类型定义；修改 `services/leadService.ts` 新增 `importLeads`/`downloadLeadTemplate` 方法；修改 `services/contactService.ts` 新增 `importContacts`/`downloadContactTemplate` 方法。- [x] T007 [US1] 修改 `LeadListPage` toolbar，新增"导入"按钮（点击弹出 Modal，支持文件上传 + 结果反馈）和"下载模板"按钮；修改 `ContactListPage` 新增相同按钮。

## Phase 5: 质量检查

- [x] T008 运行 `mvn test`，确保后端测试（ContactExcelServiceTest + BulkImportIT）覆盖率 > 80%。- [x] T009 运行 `pnpm run typecheck` + `lint` + `test`，确保前端无类型错误和 lint 警告。- [x] T010 [P] 人工审查：线索/联系人导入功能完整验证（模板下载、导入校验、结果反馈、失败明细下载）。

## Dependencies & Execution Order


- T001/T002 依赖 plan.md 和 spec.md，可并行执行
- T003 依赖于 T004
- T005 依赖于 019 评分服务
- T006/T007 依赖于后端接口
- Phase 5 在所有任务完成后执行

## Notes


- 复用 016 客户导入的 POI 解析与 ImportResult 结构
- 线索必填：姓名+公司；联系人必填：姓名+客户名称（客户按名称匹配，不区分大小写精确匹配）
- 导入的线索/联系人归属当前用户（created_by）；线索评分沿用 019 自动评分（导入时可带 score，导入后可选重算）
